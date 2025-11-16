package models

import kotlinx.serialization.Serializable

@Serializable
data class AgentResponse(
    val message: ChatMessage,
    val toolCalls: List<ToolCall> = emptyList(),
    val compression: CompressionStats? = null,
    val latestSummaryPreview: String? = null,
    val requestPrompt: String? = null,  // The actual prompt sent to the model
    val memories: List<MemoryEntry>? = null
)

