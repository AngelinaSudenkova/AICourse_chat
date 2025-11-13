package routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.*
import tools.ToolRegistry
import ai.GeminiClient
import chat.CompressionService
import org.koin.core.context.GlobalContext
import platform.currentTimeMillis
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToInt

fun Route.agentRoutes() {
    post("/agent") {
        val koin = GlobalContext.get()
        val toolRegistry: ToolRegistry = koin.get()
        val geminiClient: GeminiClient = koin.get()
        val chatService: ChatService = koin.get()
        val compressionService: CompressionService = koin.get()
        
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
        
        // Handle compression
        if (req.useCompression) {
            // Get or create conversation state
            var state = chatService.getOrCreateState(req.conversationId)
            
            // Append new user message to open segment
            val userMessage = req.messages.lastOrNull() ?: return@post call.respond(
                AgentResponse(
                    message = ChatMessage("assistant", "No message provided", timestamp = currentTimeMillis()),
                    toolCalls = emptyList()
                )
            )
            
            state = state.copy(
                segments = state.segments.map {
                    if (it.id == state.openSegmentId) {
                        it.copy(messages = it.messages + userMessage)
                    } else {
                        it
                    }
                }
            )
            
            // Maybe summarize if segment reached window size
            val (newState, wasSummarized) = compressionService.maybeSummarize(state)
            state = newState
            
            // If we just summarized, add a continuation message to keep the dialogue flowing
            if (wasSummarized) {
                val continuationMessage = ChatMessage(
                    role = "user",
                    content = "Please continue our conversation naturally.",
                    timestamp = currentTimeMillis()
                )
                state = state.copy(
                    segments = state.segments.map {
                        if (it.id == state.openSegmentId) {
                            it.copy(messages = it.messages + continuationMessage)
                        } else {
                            it
                        }
                    }
                )
            }
            
            // Build compressed context
            val context = compressionService.buildContext(state)
            
            // Calculate token stats
            // tokensRaw: what we would send if we sent ALL raw messages (no compression at all)
            val tokensRaw = compressionService.measureTokensIfNoCompression(state)
            // tokensCompressed: what we actually send (summaries + current segment or all messages if first segment)
            val tokensCompressed = compressionService.measureTokensCompressed(context)
            val savings = if (tokensRaw == 0) 0 else 
                (((tokensRaw - tokensCompressed).toDouble() / tokensRaw) * 100).roundToInt()
            
            // Generate response using compressed context
            val systemInstruction = "You are a helpful assistant. Use the context faithfully; if details are missing, ask clarifying questions."
            val prompt = "$systemInstruction\n\n$context\n\nAssistant:"
            
            val text = runCatching {
                withTimeout(60_000) { geminiClient.generate(prompt) }
            }.getOrElse { e ->
                val errorMsg = when (e) {
                    is kotlinx.coroutines.TimeoutCancellationException -> "Request timed out after 60 seconds. The API may be slow or unresponsive."
                    else -> "LLM error: ${e.message ?: e::class.simpleName ?: "unknown"}"
                }
                errorMsg
            }
            
            val assistantMessage = ChatMessage("assistant", text, timestamp = currentTimeMillis())
            
            // Append assistant message to open segment
            state = state.copy(
                segments = state.segments.map {
                    if (it.id == state.openSegmentId) {
                        it.copy(messages = it.messages + assistantMessage)
                    } else {
                        it
                    }
                }
            )
            
            // Persist state
            chatService.saveState(state)
            
            // Also save to traditional message store for backward compatibility
            val allMessagesForStorage: List<ChatMessage> = state.segments.flatMap { seg ->
                seg.messages + (seg.summary?.let { listOf(ChatMessage("system", "[Summary] $it")) } ?: emptyList())
            }
            runCatching {
                chatService.saveConversation(req.conversationId, allMessagesForStorage, emptyList())
            }
            
            // Get latest summary preview
            val latestSummary = state.segments.mapNotNull { it.summary }.lastOrNull()
            
            val response = AgentResponse(
                message = assistantMessage,
                toolCalls = emptyList(),
                compression = CompressionStats(
                    tokensRawApprox = tokensRaw,
                    tokensCompressedApprox = tokensCompressed,
                    savingsPercent = savings
                ),
                latestSummaryPreview = latestSummary?.take(280),
                requestPrompt = prompt  // Include the actual prompt sent to the model
            )
            
            call.respond(response)
        } else {
            // No compression - use original logic
            val cleanMessages = req.messages.filter { 
                it.role != "system" && 
                !it.content.startsWith("Error:") && 
                !it.content.startsWith("LLM error:") &&
                !it.content.startsWith("Handler error:") &&
                !it.content.startsWith("DI error:")
            }
            val prompt = cleanMessages.joinToString("\n") { "${it.role}: ${it.content}" }
            
            val text = runCatching {
                withTimeout(60_000) { geminiClient.generate(prompt) }
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
    
    // Force summarize endpoint
    post("/agent/force-summarize") {
        val koin = GlobalContext.get()
        val chatService: ChatService = koin.get()
        val compressionService: CompressionService = koin.get()
        
        val req = runCatching { call.receive<AgentRequest>() }
            .getOrElse {
                return@post call.respondText("Invalid request", status = io.ktor.http.HttpStatusCode.BadRequest)
            }
        
        var state = chatService.getOrCreateState(req.conversationId)
        val (newState, wasSummarized) = compressionService.forceSummarize(state)
        state = newState
        
        // If we just summarized, add a continuation message
        if (wasSummarized) {
            val continuationMessage = ChatMessage(
                role = "user",
                content = "Please continue our conversation naturally.",
                timestamp = currentTimeMillis()
            )
            state = state.copy(
                segments = state.segments.map {
                    if (it.id == state.openSegmentId) {
                        it.copy(messages = it.messages + continuationMessage)
                    } else {
                        it
                    }
                }
            )
        }
        
        chatService.saveState(state)
        
        call.respond(state)
    }
}
