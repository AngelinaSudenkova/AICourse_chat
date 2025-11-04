package models

import kotlinx.serialization.Serializable
import platform.currentTimeMillis

@Serializable
data class ToolCall(
    val name: String,
    val input: String,
    val result: String? = null,
    val timestamp: Long = currentTimeMillis()
)

