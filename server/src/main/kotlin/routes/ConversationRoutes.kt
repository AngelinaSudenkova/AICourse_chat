package routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext
import models.Conversation

fun Route.conversationRoutes() {
    val koin = GlobalContext.get()
    val chatService: ChatService = koin.get()
    
    get("/conversations") {
        val conversations = chatService.listConversations()
        call.respond(conversations)
    }
    
    get("/conversations/{id}") {
        val id = call.parameters["id"] ?: return@get call.respond(
            io.ktor.http.HttpStatusCode.BadRequest,
            mapOf("error" to "Missing conversation id")
        )
        
        val conversation = chatService.getConversation(id)
        if (conversation != null) {
            call.respond(conversation)
        } else {
            call.respond(
                io.ktor.http.HttpStatusCode.NotFound,
                mapOf("error" to "Conversation not found")
            )
        }
    }
    
    post("/conversations") {
        val conversation = chatService.createConversation()
        call.respond(conversation)
    }
    
    delete("/conversations/{id}") {
        val id = call.parameters["id"] ?: return@delete call.respond(
            io.ktor.http.HttpStatusCode.BadRequest,
            mapOf("error" to "Missing conversation id")
        )
        
        val deleted = chatService.deleteConversation(id)
        if (deleted) {
            call.respond(io.ktor.http.HttpStatusCode.OK, mapOf("success" to true))
        } else {
            call.respond(
                io.ktor.http.HttpStatusCode.NotFound,
                mapOf("error" to "Conversation not found")
            )
        }
    }
}

