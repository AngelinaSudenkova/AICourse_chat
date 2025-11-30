package routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext
import rag.RagService
import routes.ChatService
import models.RagQuestionRequest
import models.RagAnswerComparison
import models.RagFilteringComparison
import models.RagCitedAnswerRequest
import models.RagCitedAnswerResponse
import models.RagChatRequest
import models.RagChatResponse

fun Route.ragRoutes() {
    route("/rag") {
        post("/compare") {
            try {
                val koin = GlobalContext.get()
                val ragService: RagService = koin.get()
                val req = call.receive<RagQuestionRequest>()

                val resp: RagAnswerComparison = ragService.answerWithComparison(req)
                call.respond(resp)
            } catch (e: IllegalStateException) {
                println("RAG error: ${e.message}")
                e.printStackTrace()
                call.respond(
                    io.ktor.http.HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "RAG error"))
                )
            } catch (e: Exception) {
                println("RAG error: ${e.message}")
                e.printStackTrace()
                call.respond(
                    io.ktor.http.HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }
        
        post("/filter-compare") {
            try {
                val koin = GlobalContext.get()
                val ragService: RagService = koin.get()
                val req = call.receive<RagQuestionRequest>()

                val resp: RagFilteringComparison = ragService.answerWithFiltering(req)
                call.respond(resp)
            } catch (e: IllegalStateException) {
                println("RAG filtering error: ${e.message}")
                e.printStackTrace()
                call.respond(
                    io.ktor.http.HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "RAG filtering error"))
                )
            } catch (e: Exception) {
                println("RAG filtering error: ${e.message}")
                e.printStackTrace()
                call.respond(
                    io.ktor.http.HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }
        
        post("/cited") {
            try {
                val koin = GlobalContext.get()
                val ragService: RagService = koin.get()
                val req = call.receive<RagCitedAnswerRequest>()

                val resp: RagCitedAnswerResponse = ragService.answerWithCitations(req)
                call.respond(resp)
            } catch (e: IllegalStateException) {
                println("RAG cited error: ${e.message}")
                e.printStackTrace()
                call.respond(
                    io.ktor.http.HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "RAG cited error"))
                )
            } catch (e: Exception) {
                println("RAG cited error: ${e.message}")
                e.printStackTrace()
                call.respond(
                    io.ktor.http.HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }
        
        post("/chat") {
            try {
                val koin = GlobalContext.get()
                val ragService: RagService = koin.get()
                val chatService: ChatService = koin.get()
                val req = call.receive<RagChatRequest>()

                // Save the user message first
                val latestUserMessage = req.messages.lastOrNull { it.role == "user" }
                if (latestUserMessage != null) {
                    chatService.saveConversation(req.conversationId, listOf(latestUserMessage), emptyList())
                }

                // Get RAG response
                val resp: RagChatResponse = ragService.answerChatWithSources(req)
                
                // Save the assistant message
                chatService.saveConversation(req.conversationId, listOf(resp.message), emptyList())
                
                call.respond(resp)
            } catch (e: IllegalStateException) {
                println("RAG chat error: ${e.message}")
                e.printStackTrace()
                call.respond(
                    io.ktor.http.HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "RAG chat error"))
                )
            } catch (e: Exception) {
                println("RAG chat error: ${e.message}")
                e.printStackTrace()
                call.respond(
                    io.ktor.http.HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }
    }
}

