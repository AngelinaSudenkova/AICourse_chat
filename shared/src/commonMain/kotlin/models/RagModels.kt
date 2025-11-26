package models

import kotlinx.serialization.Serializable

@Serializable
data class RagQuestionRequest(
    val question: String,
    val topK: Int = 5,          // how many wiki chunks to use
    val source: String = "wiki", // for now only "wiki", but keep extensible
    
    // Day 17 additions:
    val enableFilter: Boolean = true,
    /**
     * Cosine similarity threshold in [0.0, 1.0].
     * Chunks with score < minSimilarity are dropped in the filtered RAG variant.
     */
    val minSimilarity: Double = 0.3
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

@Serializable
data class RagFilteringComparison(
    val question: String,
    
    val baselineAnswer: String,         // no RAG
    val ragRawAnswer: String,           // RAG with topK chunks, no filter
    val ragFilteredAnswer: String,      // RAG with filtered chunks
    
    val usedChunksRaw: List<RagUsedChunk>,      // as Day 16
    val usedChunksFiltered: List<RagUsedChunk>, // after threshold
    val filterEnabled: Boolean,
    val minSimilarity: Double
)

