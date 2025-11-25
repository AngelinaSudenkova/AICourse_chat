package rag

import indexing.EmbeddingsClient
import models.RagAnswerComparison
import models.RagQuestionRequest
import models.RagUsedChunk
import models.WikiIndex
import models.WikiSearchRequest
import wiki.WikiIndexer
import wiki.WikiSearcher
import ai.GeminiClient

class RagService(
    private val wikiIndexer: WikiIndexer,
    private val wikiSearcher: WikiSearcher,
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
            sb.appendLine("=== Context #${idx + 1} ===")
            sb.appendLine("Title: ${c.title}")
            sb.appendLine(c.snippet)
            sb.appendLine()
        }
        return sb.toString()
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

