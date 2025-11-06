package routes

import ai.GeminiClient
import ai.ReadingSummarySchema
import structured.ReadingSummary
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.getKoin
import kotlinx.serialization.json.*

fun Route.summaryRoutes() {
    post("/summarize") {
        val koin = application.getKoin()
        val gemini: GeminiClient = koin.get()

        // Accepts plain text or JSON { "text": "..." }
        val raw = call.receiveText().trim()
        val text = if (raw.startsWith("{")) {
            // Naive JSON extract; try to get "text" field
            try {
                kotlinx.serialization.json.Json.parseToJsonElement(raw)
                    .jsonObject["text"]?.jsonPrimitive?.content ?: raw
            } catch (e: Exception) {
                raw
            }
        } else {
            raw
        }

        val prompt = """
            Read and summarize the following text. 
            
            Return strictly the JSON with fields:
            - title (string): A concise, descriptive title for the text
            - theSourceOfTheText (string): URL/domain or 'User Input' if unknown
            - timeOfReading (string): Estimated reading time (e.g., '~2 min', '~5 min')
            - summary (string): A concise overview of the main points. Use bullet points or numbered lists when appropriate to make it more readable.
        """.trimIndent()

        val result: ReadingSummary = runCatching {
            gemini.generateStructured<ReadingSummary>(
                userText = "$prompt\n\nTEXT:\n$text",
                responseSchema = ReadingSummarySchema
            )
        }.getOrElse { ex ->
            // Always respond with a valid shape on error
            ReadingSummary(
                title = "Summary unavailable",
                theSourceOfTheText = "User Input",
                timeOfReading = "~unknown",
                summary = "Error: ${ex.message ?: ex::class.simpleName}"
            )
        }
        
        call.respond(result)
    }
}

