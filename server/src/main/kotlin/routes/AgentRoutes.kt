package routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.*
import tools.ToolRegistry
import ai.GeminiClient
import org.koin.core.context.GlobalContext
import platform.currentTimeMillis
import kotlinx.coroutines.withTimeout

fun Route.agentRoutes() {
    post("/agent") {
        val koin = GlobalContext.get()
        val toolRegistry: ToolRegistry = koin.get()
        val geminiClient: GeminiClient = koin.get()
        val chatService: ChatService = koin.get()
        
        val req = runCatching { call.receive<AgentRequest>() }
            .getOrElse {
                return@post call.respond(
                    AgentResponse(
                        message = ChatMessage(
                            role = "assistant",
                            content = "Handler error (receive): ${it::class.qualifiedName}",
                            timestamp = currentTimeMillis()
                        ),
                        toolCalls = emptyList()
                    )
                )
            }
        
        val last = req.messages.lastOrNull()?.content.orEmpty()
        
        val toolCall = runCatching { toolRegistry.decide(last) }
            .getOrNull()
        
        if (toolCall != null) {
            val result = runCatching { toolRegistry.invokeSync(toolCall) }
                .getOrDefault("Tool failed: ${toolCall.name}")
            
            val response = AgentResponse(
                message = ChatMessage("assistant", result, timestamp = currentTimeMillis()),
                toolCalls = listOf(toolCall.copy(result = result))
            )
            
            runCatching {
                chatService.saveConversation(req.conversationId, req.messages + response.message, response.toolCalls)
            }
            
            return@post call.respond(response)
        }
        
        val cleanMessages = req.messages.filter { 
            it.role != "system" && 
            !it.content.startsWith("Error:") && 
            !it.content.startsWith("LLM error:") &&
            !it.content.startsWith("Handler error:") &&
            !it.content.startsWith("DI error:")
        }
        val prompt = cleanMessages.joinToString("\n") { "${it.role}: ${it.content}" }
        
        val text = runCatching {
            withTimeout(60_000) { geminiClient.generate(prompt) }  // Increased to 60 seconds
        }.getOrElse { e ->
            val errorMsg = when (e) {
                is kotlinx.coroutines.TimeoutCancellationException -> "Request timed out after 60 seconds. The API may be slow or unresponsive."
                else -> "LLM error: ${e.message ?: e::class.simpleName ?: "unknown"}"
            }
            errorMsg
        }
        
        val response = AgentResponse(
            message = ChatMessage("assistant", text, timestamp = currentTimeMillis()),
            toolCalls = emptyList()
        )
        
        runCatching {
            chatService.saveConversation(req.conversationId, req.messages + response.message, emptyList())
        }
        
        call.respond(response)
    }
}
