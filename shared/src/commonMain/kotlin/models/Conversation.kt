package models

import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long
)

@Serializable
data class ConversationWithMessages(
    val id: String,
    val title: String,
    val createdAt: Long,
    val messages: List<ChatMessage>,
    val toolCalls: List<ToolCall> = emptyList()
)

