package rag

import indexing.EmbeddingsClient
import models.RagAnswerComparison
import models.RagFilteringComparison
import models.RagQuestionRequest
import models.RagUsedChunk
import models.WikiIndex
import models.WikiSearchRequest
import models.RagCitedAnswerRequest
import models.RagCitedAnswerResponse
import models.LabeledSource
import models.RagChatRequest
import models.RagChatResponse
import models.ChatMessage
import platform.currentTimeMillis
import wiki.WikiIndexer
import wiki.WikiSearcher
import wiki.WikiFetcher
import ai.GeminiClient

class RagService(
    private val wikiIndexer: WikiIndexer,
    private val wikiSearcher: WikiSearcher,
    private val wikiFetcher: WikiFetcher,
    private val geminiClient: GeminiClient,
    private val embeddingsClient: EmbeddingsClient
) {
    suspend fun answerWithComparison(req: RagQuestionRequest): RagAnswerComparison {
        val question = req.question.trim()
        require(question.isNotEmpty()) { "Question must not be empty" }

        val index: WikiIndex = wikiIndexer.loadIndex()
            ?: error("Wiki index not found. Please build it first.")

        // 1) Baseline answer (no RAG)
        val baselinePrompt = buildBaselinePrompt(question)
        val baselineAnswer = geminiClient.generate(baselinePrompt)

        // 2) RAG: search relevant chunks
        val searchReq = WikiSearchRequest(
            query = question,
            topK = req.topK
        )
        val searchResp = wikiSearcher.search(index, searchReq)

        val usedChunks = searchResp.results.map {
            RagUsedChunk(
                chunkId = it.chunkId,
                articleId = it.articleId,
                title = it.title,
                score = it.score,
                snippet = it.snippet
            )
        }

        val context = buildContextFromChunks(usedChunks)

        val ragPrompt = buildRagPrompt(
            question = question,
            context = context
        )
        val ragAnswer = geminiClient.generate(ragPrompt)

        return RagAnswerComparison(
            question = question,
            baselineAnswer = baselineAnswer,
            ragAnswer = ragAnswer,
            usedChunks = usedChunks
        )
    }

    suspend fun answerWithFiltering(req: RagQuestionRequest): RagFilteringComparison {
        val question = req.question.trim()
        require(question.isNotEmpty()) { "Question must not be empty" }

        val index: WikiIndex = wikiIndexer.loadIndex()
            ?: error("Wiki index not found. Please build it first.")

        // 1) Baseline answer (no RAG)
        val baselinePrompt = buildBaselinePrompt(question)
        val baselineAnswer = geminiClient.generate(baselinePrompt)

        // 2) RAG: search relevant chunks (raw retrieval)
        val searchReq = WikiSearchRequest(
            query = question,
            topK = req.topK
        )
        val searchResp = wikiSearcher.search(index, searchReq)

        val usedChunksRaw = searchResp.results.map {
            RagUsedChunk(
                chunkId = it.chunkId,
                articleId = it.articleId,
                title = it.title,
                score = it.score,
                snippet = it.snippet
            )
        }

        // 3) Apply filtering
        val usedChunksFiltered = filterChunks(
            chunks = usedChunksRaw,
            minSimilarity = req.minSimilarity,
            enable = req.enableFilter
        )

        // 4) Build RAG prompts for raw and filtered
        val contextRaw = buildContextFromChunks(usedChunksRaw)
        val contextFiltered = buildContextFromChunks(usedChunksFiltered)

        val ragPromptRaw = buildRagPrompt(
            question = question,
            context = contextRaw
        )
        val ragPromptFiltered = buildRagPrompt(
            question = question,
            context = contextFiltered
        )

        // 5) Generate both RAG answers
        val ragRawAnswer = geminiClient.generate(ragPromptRaw)
        val ragFilteredAnswer = geminiClient.generate(ragPromptFiltered)

        return RagFilteringComparison(
            question = question,
            baselineAnswer = baselineAnswer,
            ragRawAnswer = ragRawAnswer,
            ragFilteredAnswer = ragFilteredAnswer,
            usedChunksRaw = usedChunksRaw,
            usedChunksFiltered = usedChunksFiltered,
            filterEnabled = req.enableFilter,
            minSimilarity = req.minSimilarity
        )
    }

    private fun filterChunks(
        chunks: List<RagUsedChunk>,
        minSimilarity: Double,
        enable: Boolean
    ): List<RagUsedChunk> {
        if (!enable) return chunks

        val filtered = chunks.filter { it.score >= minSimilarity }
        if (filtered.isNotEmpty()) return filtered

        // Fallback: if everything was filtered out, keep the best one
        return chunks.maxByOrNull { it.score }?.let { listOf(it) } ?: emptyList()
    }

    /**
     * Detects if there is no usable context from local RAG search.
     * Returns true if:
     * - No results found, OR
     * - Best similarity score is below threshold (default 0.2)
     */
    private fun hasNoUsableContext(chunks: List<RagUsedChunk>, threshold: Double = 0.2): Boolean {
        if (chunks.isEmpty()) return true
        val bestScore = chunks.maxOfOrNull { it.score } ?: 0.0
        return bestScore < threshold
    }

    /**
     * Attempts to fetch Wikipedia articles for a question and update the index.
     * Returns the updated index if successful, null otherwise.
     * The index is automatically merged with existing articles and saved to disk.
     */
    private suspend fun tryFetchAndIndexWikiArticles(question: String): WikiIndex? {
        return try {
            println("[RAG] No usable local context found. Attempting to fetch Wikipedia articles for: $question")
            
            // Extract Wikipedia topics from question
            val topics = extractWikipediaTopics(question)
            if (topics.isEmpty()) {
                println("[RAG] Could not extract Wikipedia topics from question")
                return null
            }
            
            println("[RAG] Extracted Wikipedia topics: ${topics.joinToString(", ")}")
            
            // Fetch and index new articles (automatically merges with existing index and saves)
            val existingIndex = wikiIndexer.loadIndex()
            val existingArticleCount = existingIndex?.articles?.size ?: 0
            val updatedIndex = wikiIndexer.buildIndexForTopics(topics, mergeWithExisting = true)
            val newArticleCount = updatedIndex.articles.size - existingArticleCount
            println("[RAG] Successfully indexed $newArticleCount new articles (total: ${updatedIndex.articles.size} articles, ${updatedIndex.chunks.size} chunks)")
            
            return updatedIndex
        } catch (e: Exception) {
            println("[RAG] Failed to fetch and index Wikipedia articles: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    /**
     * Searches for relevant chunks, automatically fetching Wikipedia articles if no usable context found.
     * Returns a SearchResult with chunks and a flag indicating if Wikipedia fetch was used.
     */
    private data class SearchResult(
        val chunks: List<RagUsedChunk>,
        val index: WikiIndex,
        val usedWikiFetch: Boolean
    )

    private suspend fun searchWithAutoFetch(
        question: String,
        topK: Int,
        minBestScoreForRag: Double,
        currentIndex: WikiIndex
    ): SearchResult {
        // First, try local search
        val searchReq = WikiSearchRequest(query = question, topK = topK)
        val searchResp = wikiSearcher.search(currentIndex, searchReq)
        
        val rawChunks = searchResp.results.map {
            RagUsedChunk(
                chunkId = it.chunkId,
                articleId = it.articleId,
                title = it.title,
                score = it.score,
                snippet = it.snippet
            )
        }
        
        val bestScore = rawChunks.maxOfOrNull { it.score } ?: 0.0
        
        // Check if we have usable context
        if (!hasNoUsableContext(rawChunks, minBestScoreForRag)) {
            println("[RAG] Using local RAG: Found ${rawChunks.size} chunks with best score $bestScore")
            return SearchResult(rawChunks, currentIndex, usedWikiFetch = false)
        }
        
        // No usable context - try to fetch Wikipedia articles
        println("[RAG] Local RAG found no usable context (best score: $bestScore, threshold: $minBestScoreForRag)")
        val updatedIndex = tryFetchAndIndexWikiArticles(question)
        
        if (updatedIndex == null) {
            println("[RAG] Wikipedia fetch failed, using original results")
            return SearchResult(rawChunks, currentIndex, usedWikiFetch = false)
        }
        
        // Re-search with updated index
        val updatedSearchResp = wikiSearcher.search(updatedIndex, searchReq)
        val updatedChunks = updatedSearchResp.results.map {
            RagUsedChunk(
                chunkId = it.chunkId,
                articleId = it.articleId,
                title = it.title,
                score = it.score,
                snippet = it.snippet
            )
        }
        
        val updatedBestScore = updatedChunks.maxOfOrNull { it.score } ?: 0.0
        println("[RAG] After Wikipedia fetch: Found ${updatedChunks.size} chunks with best score $updatedBestScore")
        
        if (!hasNoUsableContext(updatedChunks, minBestScoreForRag)) {
            println("[RAG] Using wiki fetch + RAG: Successfully found relevant context after fetching")
            return SearchResult(updatedChunks, updatedIndex, usedWikiFetch = true)
        }
        
        println("[RAG] Still no usable context after Wikipedia fetch (best score: $updatedBestScore)")
        return SearchResult(updatedChunks, updatedIndex, usedWikiFetch = true)
    }

    private suspend fun extractWikipediaTopics(question: String): List<String> {
        val prompt = """
            Based on the following question, suggest 2-4 specific Wikipedia article titles that would be most relevant to answer it.
            
            Return ONLY a comma-separated list of Wikipedia article titles (exact titles as they appear on Wikipedia).
            Do not include any explanations or additional text.
            Use proper capitalization and formatting (e.g., "Quantum computing", "Machine learning", "Artificial intelligence").
            
            Question: $question
            
            Wikipedia article titles:
        """.trimIndent()
        
        val response = geminiClient.generate(prompt)
        
        // Parse the response - extract titles from comma-separated list
        return response.lines()
            .flatMap { line ->
                line.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.contains(":") && !it.contains("Wikipedia") }
            }
            .take(4) // Limit to 4 topics
    }

    private fun buildBaselinePrompt(question: String): String {
        return """
            You are a helpful assistant.
            Answer the user's question clearly and concisely.

            Question:
            $question
        """.trimIndent()
    }

    private fun buildContextFromChunks(chunks: List<RagUsedChunk>): String {
        if (chunks.isEmpty()) return "No external context available."

        val sb = StringBuilder()
        chunks.forEachIndexed { idx, c ->
            sb.appendLine("=== Context #${idx + 1} (score=${"%.3f".format(c.score)}) ===")
            sb.appendLine("Title: ${c.title}")
            sb.appendLine(c.snippet)
            sb.appendLine()
        }
        return sb.toString()
    }

    suspend fun answerWithCitations(req: RagCitedAnswerRequest): RagCitedAnswerResponse {
        val question = req.question.trim()
        require(question.isNotEmpty()) { "Question must not be empty" }

        val initialIndex: WikiIndex = wikiIndexer.loadIndex()
            ?: error("Wiki index not found. Please build it first.")

        // 1) Search with automatic Wikipedia fetch if needed
        val searchResult = searchWithAutoFetch(
            question = question,
            topK = req.topK,
            minBestScoreForRag = req.minBestScoreForRag,
            currentIndex = initialIndex
        )

        val rawChunks = searchResult.chunks
        val bestScore = rawChunks.maxOfOrNull { it.score } ?: 0.0

        // 2) Check if we have usable context after auto-fetch
        if (hasNoUsableContext(rawChunks, req.minBestScoreForRag)) {
            // Still no usable context - use fallback if allowed
            if (req.allowModelFallback) {
                println("[RAG] LLM fallback: No usable context found (best score: $bestScore), using model fallback")
                val fallbackAnswer = geminiClient.generate(buildBaselinePrompt(question))
                
                return RagCitedAnswerResponse(
                    question = question,
                    answerWithCitations = fallbackAnswer,
                    labeledSources = emptyList()
                )
            } else {
                error("No relevant sources found and fallback is disabled")
            }
        }

        // 3) Apply filtering
        val filteredChunks = filterChunks(
            chunks = rawChunks,
            minSimilarity = req.minSimilarity,
            enable = req.enableFilter
        )

        if (filteredChunks.isEmpty()) {
            // After filtering, no chunks remain - use fallback if allowed
            if (req.allowModelFallback) {
                println("[RAG] LLM fallback: All chunks filtered out, using model fallback")
                val fallbackAnswer = geminiClient.generate(buildBaselinePrompt(question))
                
                return RagCitedAnswerResponse(
                    question = question,
                    answerWithCitations = fallbackAnswer,
                    labeledSources = emptyList()
                )
            } else {
                error("No relevant sources found after filtering and fallback is disabled")
            }
        }

        // 4) Create labeled sources
        val labeledSources = filteredChunks.mapIndexed { index, chunk ->
            LabeledSource(
                label = "${index + 1}",
                chunkId = chunk.chunkId,
                articleId = chunk.articleId,
                title = chunk.title,
                score = chunk.score,
                snippet = chunk.snippet
            )
        }

        // 5) Build labeled context
        val labeledContext = buildLabeledContext(labeledSources)

        // 6) Build citation prompt
        val citationPrompt = buildCitationPrompt(
            question = question,
            context = labeledContext
        )

        // 7) Generate answer with citations
        val sourceType = if (searchResult.usedWikiFetch) "wiki fetch + RAG" else "local RAG"
        println("[RAG] Using $sourceType: ${labeledSources.size} sources (best score: $bestScore)")
        val answerWithCitations = geminiClient.generate(citationPrompt)

        return RagCitedAnswerResponse(
            question = question,
            answerWithCitations = answerWithCitations,
            labeledSources = labeledSources
        )
    }

    private fun buildLabeledContext(sources: List<LabeledSource>): String {
        if (sources.isEmpty()) return "No external context available."

        val sb = StringBuilder()
        sources.forEach { source ->
            sb.appendLine("=== [${source.label}] ${source.title} (score=${"%.3f".format(source.score)}) ===")
            sb.appendLine(source.snippet)
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun buildCitationPrompt(
        question: String,
        context: String
    ): String {
        return """
            You are a helpful assistant that must answer using ONLY the provided context from a local wiki index.
            
            CRITICAL REQUIREMENTS:
            1. Use ONLY the information in the context below. Do not use any external knowledge.
            2. For each sentence or claim you make that uses information from the context, add inline citations in square brackets at the end of that sentence.
            3. Citations should reference the source labels [1], [2], [3], etc. that correspond to the numbered sources in the context.
            4. You can cite multiple sources like [1,2] if information comes from multiple sources.
            5. If the context does not contain enough information to answer the question, you MUST say: "I don't know based on the provided sources."
            6. At the end of your answer, add a "Sources:" section listing all cited sources in the format: [label] Title — Wikipedia
            
            Example citation format:
            "Quantum computing uses qubits instead of classical bits [1]. Qubits can exist in superposition states [1,2]."
            
            Sources:
            [1] Quantum computing — Wikipedia
            [2] Qubit — Wikipedia

            -------------------- CONTEXT START --------------------
            $context
            -------------------- CONTEXT END ----------------------

            Now answer the user's question using the context above. Remember to add citations [1], [2], etc. for each claim, and include a Sources section at the end.

            Question:
            $question
        """.trimIndent()
    }

    private fun buildRagPrompt(
        question: String,
        context: String
    ): String {
        return """
            You are a helpful assistant that must answer using the provided context from a local wiki index.
            
            Use ONLY the information in the context below when possible.
            If the context is insufficient, say you don't know based on the available context.

            -------------------- CONTEXT START --------------------
            $context
            -------------------- CONTEXT END ----------------------

            Now answer the user's question using the context above.

            Question:
            $question
        """.trimIndent()
    }

    suspend fun answerChatWithSources(req: RagChatRequest): RagChatResponse {
        // 1) Extract the latest user message
        val latestUserMessage = req.messages.lastOrNull { it.role == "user" }
            ?: error("No user message found in request")
        
        val question = latestUserMessage.content.trim()
        require(question.isNotEmpty()) { "Question must not be empty" }

        // 2) Build compact conversation history (last 4-6 messages for context)
        val recentHistory = req.messages.takeLast(6).joinToString("\n") { msg ->
            "${msg.role}: ${msg.content}"
        }

        val initialIndex: WikiIndex = wikiIndexer.loadIndex()
            ?: error("Wiki index not found. Please build it first.")

        // 3) Search with automatic Wikipedia fetch if needed
        val searchResult = searchWithAutoFetch(
            question = question,
            topK = req.topK,
            minBestScoreForRag = req.minBestScoreForRag,
            currentIndex = initialIndex
        )

        val rawChunks = searchResult.chunks
        val bestScore = rawChunks.maxOfOrNull { it.score } ?: 0.0

        // 4) Check if we have usable context after auto-fetch
        if (hasNoUsableContext(rawChunks, req.minBestScoreForRag)) {
            // Still no usable context - use fallback if allowed
            if (req.allowModelFallback) {
                println("[RAG Chat] LLM fallback: No usable context found (best score: $bestScore), using model fallback")
                val fallbackPrompt = buildChatPrompt(
                    question = question,
                    conversationHistory = recentHistory,
                    context = "No external context available."
                )
                val fallbackAnswer = geminiClient.generate(fallbackPrompt)
                
                return RagChatResponse(
                    message = ChatMessage(
                        role = "assistant",
                        content = fallbackAnswer,
                        timestamp = currentTimeMillis()
                    ),
                    labeledSources = emptyList()
                )
            } else {
                error("No relevant sources found and fallback is disabled")
            }
        }

        // 5) Apply filtering
        val filteredChunks = filterChunks(
            chunks = rawChunks,
            minSimilarity = req.minSimilarity,
            enable = req.enableFilter
        )

        if (filteredChunks.isEmpty()) {
            // After filtering, no chunks remain - use fallback if allowed
            if (req.allowModelFallback) {
                println("[RAG Chat] LLM fallback: All chunks filtered out, using model fallback")
                val fallbackPrompt = buildChatPrompt(
                    question = question,
                    conversationHistory = recentHistory,
                    context = "No external context available."
                )
                val fallbackAnswer = geminiClient.generate(fallbackPrompt)
                
                return RagChatResponse(
                    message = ChatMessage(
                        role = "assistant",
                        content = fallbackAnswer,
                        timestamp = currentTimeMillis()
                    ),
                    labeledSources = emptyList()
                )
            } else {
                error("No relevant sources found after filtering and fallback is disabled")
            }
        }

        // 6) Create labeled sources
        val labeledSources = filteredChunks.mapIndexed { index, chunk ->
            LabeledSource(
                label = "${index + 1}",
                chunkId = chunk.chunkId,
                articleId = chunk.articleId,
                title = chunk.title,
                score = chunk.score,
                snippet = chunk.snippet
            )
        }

        // 7) Build labeled context
        val labeledContext = buildLabeledContext(labeledSources)

        // 8) Build chat prompt with conversation history
        val chatPrompt = buildChatPrompt(
            question = question,
            conversationHistory = recentHistory,
            context = labeledContext
        )

        // 9) Generate answer with citations
        val sourceType = if (searchResult.usedWikiFetch) "wiki fetch + RAG" else "local RAG"
        println("[RAG Chat] Using $sourceType: ${labeledSources.size} sources (best score: $bestScore)")
        val answerWithCitations = geminiClient.generate(chatPrompt)

        return RagChatResponse(
            message = ChatMessage(
                role = "assistant",
                content = answerWithCitations,
                timestamp = currentTimeMillis()
            ),
            labeledSources = labeledSources
        )
    }

    private fun buildChatPrompt(
        question: String,
        conversationHistory: String,
        context: String
    ): String {
        return """
            You are a helpful assistant that must answer using the provided context from a local wiki index, while also considering the conversation history.
            
            CRITICAL REQUIREMENTS:
            1. Use the information in the context below when available. You can also reference the conversation history for context.
            2. For each sentence or claim you make that uses information from the context, add inline citations in square brackets at the end of that sentence.
            3. Citations should reference the source labels [1], [2], [3], etc. that correspond to the numbered sources in the context.
            4. You can cite multiple sources like [1,2] if information comes from multiple sources.
            5. If the context does not contain enough information to answer the question, you can use your general knowledge, but still try to cite sources when possible.
            6. At the end of your answer, add a "Sources:" section listing all cited sources in the format: [label] Title — Wikipedia
            
            Example citation format:
            "Quantum computing uses qubits instead of classical bits [1]. Qubits can exist in superposition states [1,2]."
            
            Sources:
            [1] Quantum computing — Wikipedia
            [2] Qubit — Wikipedia

            -------------------- CONVERSATION HISTORY --------------------
            $conversationHistory
            -------------------- CONVERSATION HISTORY END ----------------

            -------------------- CONTEXT FROM DOCUMENTS --------------------
            $context
            -------------------- CONTEXT END ----------------------

            Now answer the user's latest question using both the conversation history and the document context above. Remember to add citations [1], [2], etc. for each claim that uses document context, and include a Sources section at the end.

            Latest Question:
            $question
        """.trimIndent()
    }
}

