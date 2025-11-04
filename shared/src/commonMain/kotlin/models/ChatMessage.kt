package models

import kotlinx.serialization.Serializable
import platform.currentTimeMillis

@Serializable
data class ChatMessage(
    val role: String, // "user", "assistant", "tool", "system"
    val content: String,
    val timestamp: Long = currentTimeMillis()
)

