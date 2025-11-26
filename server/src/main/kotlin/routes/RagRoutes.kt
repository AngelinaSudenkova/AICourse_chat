package routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext
import rag.RagService
import models.RagQuestionRequest
import models.RagAnswerComparison
import models.RagFilteringComparison

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
    }
}

