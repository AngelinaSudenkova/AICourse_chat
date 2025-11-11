package routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.*
import tools.ToolRegistry
import ai.GeminiClient
import org.koin.core.context.GlobalContext

fun Route.chatRoutes() {
    post("/ai/chat") {
        val koin = GlobalContext.get()
        val toolRegistry: ToolRegistry = koin.get()
        val geminiClient: GeminiClient = koin.get()
        val chatService: ChatService = koin.get()
        
        val req = call.receive<AgentRequest>()
        val last = req.messages.lastOrNull()?.content.orEmpty()
        
        val toolCall = toolRegistry.decide(last)
        if (toolCall != null) {
            val result = toolRegistry.invokeSync(toolCall)
            val response = AgentResponse(
                ChatMessage("assistant", result),
                listOf(toolCall.copy(result = result))
            )
            
            chatService.saveConversation(req.conversationId, req.messages + response.message, response.toolCalls)
            
            call.respond(response)
            return@post
        }
        
        val prompt = req.messages.joinToString("\n") { "${it.role}: ${it.content}" }
        val text = geminiClient.generate(prompt)
        val response = AgentResponse(ChatMessage("assistant", text))
        
        chatService.saveConversation(req.conversationId, req.messages + response.message, emptyList())
        
        call.respond(response)
    }
}

