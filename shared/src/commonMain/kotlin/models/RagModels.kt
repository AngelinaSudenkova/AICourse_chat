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

// Day 18: Citations & Sources
@Serializable
data class RagCitedAnswerRequest(
    val question: String,
    val topK: Int = 5,
    val enableFilter: Boolean = true,
    val minSimilarity: Double = 0.3,
    // Fallback parameters
    val allowModelFallback: Boolean = true,
    /**
     * Minimum best similarity score to use RAG instead of fallback.
     * If the best chunk score is below this threshold and allowModelFallback is true,
     * the system will use the base model without RAG.
     */
    val minBestScoreForRag: Double = 0.25,
    /**
     * If true, when scores are low, automatically fetch relevant Wikipedia articles
     * and add them to the index before falling back to model-only answer.
     */
    val autoFetchWiki: Boolean = false
)

@Serializable
data class LabeledSource(
    val label: String,          // e.g. "1", "2"
    val chunkId: String,
    val articleId: String,
    val title: String,
    val score: Double,
    val snippet: String
)

@Serializable
data class RagCitedAnswerResponse(
    val question: String,
    val answerWithCitations: String,
    val labeledSources: List<LabeledSource>
)

// Day 19: RAG Chat with Memory
@Serializable
data class RagChatRequest(
    val messages: List<ChatMessage>,
    val conversationId: String? = null,
    val topK: Int = 5,
    val enableFilter: Boolean = true,
    val minSimilarity: Double = 0.3,
    val allowModelFallback: Boolean = true,
    val minBestScoreForRag: Double = 0.25,
    val autoFetchWiki: Boolean = false
)

@Serializable
data class RagChatResponse(
    val message: ChatMessage,
    val labeledSources: List<LabeledSource>
)

