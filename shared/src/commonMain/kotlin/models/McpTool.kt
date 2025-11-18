package models

import kotlinx.serialization.Serializable

@Serializable
data class McpTool(
    val name: String,
    val description: String? = null
)

@Serializable
data class McpToolsListResponse(
    val tools: List<McpTool>
)

@Serializable
data class McpJsonMessage(
    val direction: String, // "request" or "response"
    val content: String   // JSON string
)

@Serializable
data class McpToolsResponse(
    val tools: List<McpTool>,
    val messages: List<McpJsonMessage> = emptyList() // JSON request/response pairs
)

