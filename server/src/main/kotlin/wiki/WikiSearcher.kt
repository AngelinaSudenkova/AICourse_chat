package wiki

import indexing.EmbeddingsClient
import models.*
import kotlin.math.sqrt

class WikiSearcher(
    private val embeddingsClient: EmbeddingsClient
) {
    suspend fun search(index: WikiIndex, request: WikiSearchRequest): WikiSearchResponse {
        if (index.chunks.isEmpty()) {
            return WikiSearchResponse(request.query, emptyList())
        }

        val queryEmbedding = embeddingsClient.embed(listOf(request.query)).firstOrNull() ?: emptyList()

        if (queryEmbedding.isEmpty()) {
            return WikiSearchResponse(request.query, emptyList())
        }

        val articleById = index.articles.associateBy { it.id }

        val scored = index.chunks.mapNotNull { chunk ->
            val score = cosineSimilarity(queryEmbedding, chunk.embedding)
            if (score.isNaN()) null else chunk to score
        }.sortedByDescending { it.second }
            .take(request.topK)

        val results = scored.map { (chunk, score) ->
            val article = articleById[chunk.articleId]
            WikiSearchResultItem(
                chunkId = chunk.id,
                articleId = chunk.articleId,
                title = article?.title ?: chunk.articleId,
                score = score,
                snippet = chunk.text.take(300)
            )
        }

        return WikiSearchResponse(
            query = request.query,
            results = results
        )
    }

    private fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
        val n = minOf(a.size, b.size)
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in 0 until n) {
            val x = a.getOrNull(i) ?: 0.0
            val y = b.getOrNull(i) ?: 0.0
            dot += x * y
            normA += x * x
            normB += y * y
        }

        if (normA == 0.0 || normB == 0.0) return 0.0

        return dot / (sqrt(normA) * sqrt(normB))
    }
}

