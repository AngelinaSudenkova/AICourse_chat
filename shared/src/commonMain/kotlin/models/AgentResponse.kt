package models

import kotlinx.serialization.Serializable

@Serializable
data class AgentResponse(
    val message: ChatMessage,
    val toolCalls: List<ToolCall> = emptyList()
)

