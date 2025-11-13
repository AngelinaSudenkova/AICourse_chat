package models

import kotlinx.serialization.Serializable

@Serializable
data class AgentRequest(
    val messages: List<ChatMessage>,
    val conversationId: String? = null,
    val useCompression: Boolean = true
)

