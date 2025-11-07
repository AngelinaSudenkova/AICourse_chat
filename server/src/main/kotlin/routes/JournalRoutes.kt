package routes

import ai.GeminiClient
import ai.JournalResponseSchema
import structured.JournalResponse
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.getKoin
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable

@Serializable
data class JournalRequest(
    val message: String,
    val conversationHistory: List<String> = emptyList()
)

fun Route.journalRoutes() {
    post("/journal") {
        val koin = application.getKoin()
        val gemini: GeminiClient = koin.get()
        
        val request = runCatching {
            call.receive<JournalRequest>()
        }.getOrElse {
            // Fallback: accept plain text
            val text = call.receiveText().trim()
            JournalRequest(
                message = text,
                conversationHistory = emptyList()
            )
        }
        
        // Build conversation history including the new message
        val conversationHistory = (request.conversationHistory + listOf(
            "User: ${request.message}"
        )).takeLast(10) // Keep last 10 messages for context
        
        val result: JournalResponse = runCatching {
            gemini.generateJournalEntry(
                conversationHistory = conversationHistory,
                responseSchema = JournalResponseSchema
            )
        }.getOrElse { ex ->
            // Always respond with a valid shape on error
            JournalResponse(
                status = "error",
                missing = listOf("Unable to process journal entry"),
                journal = null
            )
        }
        
        // Log the parsed object
        println("=== JOURNAL RESULT ===")
        println("Status: ${result.status}")
        println("Missing: ${result.missing}")
        result.journal?.let { journal ->
            println("Journal Date: ${journal.date}")
            println("Journal Title: ${journal.title}")
            println("Mood: ${journal.mood} (${journal.moodScore})")
        }
        println()
        
        call.respond(result)
    }
}

