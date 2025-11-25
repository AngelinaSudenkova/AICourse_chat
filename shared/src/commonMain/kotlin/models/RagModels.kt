package models

import kotlinx.serialization.Serializable

@Serializable
data class RagQuestionRequest(
    val question: String,
    val topK: Int = 5,          // how many wiki chunks to use
    val source: String = "wiki" // for now only "wiki", but keep extensible
)

@Serializable
data class RagUsedChunk(
    val chunkId: String,
    val articleId: String,
    val title: String,
    val score: Double,
    val snippet: String
)

@Serializable
data class RagAnswerComparison(
    val question: String,
    val baselineAnswer: String,
    val ragAnswer: String,
    val usedChunks: List<RagUsedChunk>
)

