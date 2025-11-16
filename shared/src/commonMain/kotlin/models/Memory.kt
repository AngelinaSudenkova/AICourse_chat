package models

import kotlinx.serialization.Serializable

@Serializable
data class MemoryEntry(
    val id: String,
    val conversationId: String,
    val kind: String,          // e.g. "summary", "profile", "reading", "task"
    val title: String,
    val content: String,
    val createdAt: Long        // epoch millis
)


