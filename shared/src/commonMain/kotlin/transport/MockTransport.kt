package transport

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import models.AgentRequest
import models.AgentResponse
import models.ChatMessage
import models.ToolCall
import platform.currentTimeMillis

class MockTransport : Transport {
    override suspend fun send(request: AgentRequest): AgentResponse {
        delay(500)
        val last = request.messages.lastOrNull()?.content.orEmpty()
        
        return when {
            last.startsWith("/calc") -> {
                val expr = last.removePrefix("/calc").trim()
                val result = try {
                    evaluateExpression(expr).toString()
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
                AgentResponse(
                    message = ChatMessage("assistant", "Result: $result"),
                    toolCalls = listOf(ToolCall("calculator", expr, result))
                )
            }
            last.startsWith("/time") -> {
                val now = currentTimeMillis()
                val timeStr = formatTime(now)
                AgentResponse(
                    message = ChatMessage("assistant", "Current time: $timeStr"),
                    toolCalls = listOf(ToolCall("timeZone", "", timeStr))
                )
            }
            else -> {
                AgentResponse(
                    message = ChatMessage("assistant", "Mock response to: $last")
                )
            }
        }
    }
    
    override fun sendStream(request: AgentRequest): Flow<AgentResponse> = flow {
        emit(send(request))
    }
    
    private fun formatTime(timestamp: Long): String {
        // Simple ISO-like format - multiplatform compatible
        return "2024-01-01T00:00:00Z" // Placeholder - in real usage, use kotlinx-datetime
    }
    
    private fun evaluateExpression(expr: String): Double {
        // Simple expression evaluator - basic operations only
        return try {
            val cleanExpr = expr.replace(" ", "").replace("^", "**")
            // For a real implementation, use a proper math parser
            // This is a simplified version for mock purposes
            when {
                cleanExpr.contains("+") -> {
                    val parts = cleanExpr.split("+")
                    parts.sumOf { it.toDouble() }
                }
                cleanExpr.contains("-") && !cleanExpr.startsWith("-") -> {
                    val parts = cleanExpr.split("-")
                    parts[0].toDouble() - parts.drop(1).sumOf { it.toDouble() }
                }
                cleanExpr.contains("*") -> {
                    val parts = cleanExpr.split("*")
                    parts.fold(1.0) { acc, s -> acc * s.toDouble() }
                }
                cleanExpr.contains("/") -> {
                    val parts = cleanExpr.split("/")
                    parts.drop(1).fold(parts[0].toDouble()) { acc, s -> acc / s.toDouble() }
                }
                else -> cleanExpr.toDouble()
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid expression: $expr")
        }
    }
}
