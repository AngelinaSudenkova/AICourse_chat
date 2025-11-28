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

        val index: WikiIndex = wikiIndexer.loadIndex()
            ?: error("Wiki index not found. Please build it first.")

        // 1) Search relevant chunks
        val searchReq = WikiSearchRequest(
            query = question,
            topK = req.topK
        )
        val searchResp = wikiSearcher.search(index, searchReq)

        val rawChunks = searchResp.results.map {
            RagUsedChunk(
                chunkId = it.chunkId,
                articleId = it.articleId,
                title = it.title,
                score = it.score,
                snippet = it.snippet
            )
        }

        // 2) Check if we should use fallback or auto-fetch
        val bestScore = rawChunks.maxOfOrNull { it.score } ?: 0.0
        
        if (rawChunks.isEmpty() || bestScore < req.minBestScoreForRag) {
            // Try auto-fetch if enabled
            if (req.autoFetchWiki) {
                println("RAG auto-fetch: Best score ($bestScore) below threshold (${req.minBestScoreForRag}), attempting to fetch Wikipedia articles")
                
                try {
                    // Extract Wikipedia topics from question
                    val topics = extractWikipediaTopics(question)
                    if (topics.isNotEmpty()) {
                        println("RAG auto-fetch: Extracted topics: ${topics.joinToString(", ")}")
                        
                        // Fetch and index new articles
                        val newIndex = wikiIndexer.buildIndexForTopics(topics)
                        println("RAG auto-fetch: Indexed ${newIndex.articles.size} new articles, ${newIndex.chunks.size} chunks")
                        
                        // Re-search with updated index
                        val updatedSearchResp = wikiSearcher.search(newIndex, searchReq)
                        val updatedRawChunks = updatedSearchResp.results.map {
                            RagUsedChunk(
                                chunkId = it.chunkId,
                                articleId = it.articleId,
                                title = it.title,
                                score = it.score,
                                snippet = it.snippet
                            )
                        }
                        
                        val updatedBestScore = updatedRawChunks.maxOfOrNull { it.score } ?: 0.0
                        println("RAG auto-fetch: After fetching, best score is $updatedBestScore")
                        
                        // If we got better results, use them
                        if (updatedBestScore >= req.minBestScoreForRag || updatedRawChunks.isNotEmpty()) {
                            // Continue with updated chunks
                            val filteredChunks = filterChunks(
                                chunks = updatedRawChunks,
                                minSimilarity = req.minSimilarity,
                                enable = req.enableFilter
                            )
                            
                            if (filteredChunks.isNotEmpty()) {
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
                                
                                val labeledContext = buildLabeledContext(labeledSources)
                                val citationPrompt = buildCitationPrompt(
                                    question = question,
                                    context = labeledContext
                                )
                                
                                println("RAG with citations: Using ${labeledSources.size} sources from auto-fetched articles (best score: $updatedBestScore)")
                                val answerWithCitations = geminiClient.generate(citationPrompt)
                                
                                return RagCitedAnswerResponse(
                                    question = question,
                                    answerWithCitations = answerWithCitations,
                                    labeledSources = labeledSources
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("RAG auto-fetch failed: ${e.message}")
                    e.printStackTrace()
                    // Continue to fallback
                }
            }
            
            // Use fallback: generate answer without RAG
            if (req.allowModelFallback) {
                println("RAG fallback: Best score ($bestScore) below threshold (${req.minBestScoreForRag}), using model fallback")
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
                println("RAG fallback: All chunks filtered out, using model fallback")
                val fallbackAnswer = geminiClient.generate(buildBaselinePrompt(question))
                
                return RagCitedAnswerResponse(
                    question = question,
                    answerWithCitations = fallbackAnswer,
                    labeledSources = emptyList()
                )
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
        println("RAG with citations: Using ${labeledSources.size} sources (best score: $bestScore)")
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
}

