package routes

import ai.GeminiClient
import structured.ReasonRequest
import structured.ReasonResponse
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext
import kotlinx.serialization.Serializable

fun Route.reasoningRoutes() {
    post("/reason") {
        val koin = GlobalContext.get()
        val gemini: GeminiClient = koin.get()
        
        val request = runCatching {
            call.receive<ReasonRequest>()
        }.getOrElse { ex ->
            call.respond(
                ReasonResponse(
                    method = "error",
                    promptUsed = "Failed to parse request",
                    answer = "Error: ${ex.message}"
                )
            )
            return@post
        }
        
        val method = request.method.trim().lowercase()
        val challenge = request.challenge.trim()
        
        if (challenge.isEmpty()) {
            call.respond(
                ReasonResponse(
                    method = method,
                    promptUsed = "",
                    answer = "Error: Challenge cannot be empty"
                )
            )
            return@post
        }
        
        suspend fun ask(prompt: String): String {
            return runCatching {
                gemini.generate(prompt)
            }.getOrElse { ex ->
                "Error: ${ex.message}"
            }
        }
        
        val (promptUsed, answer) = when (method) {
            "direct" -> {
                val prompt = buildDirectPrompt(challenge)
                prompt to ask(prompt)
            }
            "step" -> {
                val prompt = buildStepPrompt(challenge)
                prompt to ask(prompt)
            }
            "meta" -> {
                val designPrompt = buildMetaDesignPrompt(challenge)
                val improvedPrompt = ask(designPrompt).trim()
                // Remove markdown code blocks if present
                val cleanPrompt = improvedPrompt
                    .removePrefix("```")
                    .removePrefix("json")
                    .removePrefix("prompt")
                    .removeSuffix("```")
                    .trim()
                cleanPrompt to ask(cleanPrompt)
            }
            "experts" -> {
                val prompt = buildExpertsPrompt(challenge)
                prompt to ask(prompt)
            }
            else -> {
                "Invalid method: $method" to "Error: Unsupported method '$method'. Supported methods: direct, step, meta, experts"
            }
        }
        
        // Log method, promptUsed (first 200 chars)
        val promptPreview = promptUsed.take(200) + if (promptUsed.length > 200) "..." else ""
        println("=== REASONING LAB ===")
        println("Method: $method")
        println("Prompt (first 200 chars): $promptPreview")
        println("Answer length: ${answer.length} chars")
        println()
        
        call.respond(
            ReasonResponse(
                method = method,
                promptUsed = promptUsed,
                answer = answer
            )
        )
    }
}

private fun buildDirectPrompt(challenge: String): String {
    return """
        Give a short direct answer with no reasoning.
        
        Challenge: $challenge
        
        Answer in 1–2 sentences.
    """.trimIndent()
}

private fun buildStepPrompt(challenge: String): String {
    return """
        Solve step by step. 
        
        Define the model, state initial probabilities/assumptions, apply the relevant conditional update, and compute the final numeric result.
        
        Challenge: $challenge
        
        Conclude with a concise final statement.
    """.trimIndent()
}

private fun buildMetaDesignPrompt(challenge: String): String {
    return """
        Improve a prompt that makes a model solve the following challenge with a correct, verifiable result.
        
        The prompt should: 
        - clearly define the setup and assumptions,
        - force step-by-step reasoning,
        - request both formal reasoning and an imagined simulation check,
        - end with exact numeric conclusion(s).
        
        Output ONLY the improved prompt, nothing else.
        
        Challenge: $challenge
    """.trimIndent()
}

private fun buildExpertsPrompt(challenge: String): String {
    return """
        Simulate three experts solving the challenge:
        
        Expert A (Formal): builds a formal model and derives the result strictly.
        Expert B (Intuition + imagined simulation of 100 trials): explains and estimates frequencies.
        Expert C (Skeptic): checks edge cases and challenges assumptions.
        
        Steps:
        1) Each expert writes an independent 3–6 sentence solution.
        2) Synthesis: reconcile differences.
        3) Final: one-line conclusion with exact numeric result(s).
        
        Challenge: $challenge
        
        Format:
        [Expert A]
        
        [Expert B]
        
        [Expert C]
        
        [Synthesis]
        
        [Final]
    """.trimIndent()
}

