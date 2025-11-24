package routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.TutorRequest
import models.TutorResponse
import tutor.TutorService
import org.koin.core.context.GlobalContext

fun Route.tutorRoutes() {
    val koin = GlobalContext.get()
    val tutorService: TutorService = koin.get()
    
    /**
     * POST /api/tutor/teach
     * Orchestrates the learning tutor flow: Wikipedia → YouTube → Gemini → Notion
     */
    post("/tutor/teach") {
        try {
            val request = call.receive<TutorRequest>()
            
            if (request.topic.isBlank()) {
                call.respond(
                    io.ktor.http.HttpStatusCode.BadRequest,
                    mapOf("error" to "Topic is required")
                )
                return@post
            }
            
            val response = tutorService.teach(request)
            call.respond(response)
        } catch (e: Exception) {
            println("Tutor error: ${e.message}")
            e.printStackTrace()
            call.respond(
                io.ktor.http.HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Unknown error occurred"))
            )
        }
    }
}

