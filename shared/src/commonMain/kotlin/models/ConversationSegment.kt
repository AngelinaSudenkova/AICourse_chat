package models

import kotlinx.serialization.Serializable

@Serializable
data class ConversationSegment(
    val id: String,
    val summary: String? = null,               // committed summary (if summarized)
    val messages: List<ChatMessage> = emptyList()
)

