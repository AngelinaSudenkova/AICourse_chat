package routes

import ai.GeminiClient
import structured.TempRequest
import structured.TempResponse
import structured.TempRun
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.math.round

private val DefaultTemperatures = listOf(0.0, 0.7, 1.2)

fun Route.temperatureRoutes(gemini: GeminiClient) {
    post("/temperature") {
        val req = call.receive<TempRequest>()
        val temps = req.temps.ifEmpty { DefaultTemperatures }
            .map { round(it * 10) / 10.0 }
            .distinct()
            .sorted()

        val runs = temps.map { temp ->
            val start = System.nanoTime()
            val text = runCatching {
                gemini.generate(req.prompt, temperature = temp)
            }.getOrElse { ex ->
                println("temperature.generate.error temp=$temp")
                ex.printStackTrace()
                "Error: ${ex.message ?: ex::class.simpleName ?: "unknown error"}"
            }
            val latencyMs = (System.nanoTime() - start) / 1_000_000
            println("temperature.run temp=$temp latencyMs=$latencyMs promptPreview=${req.prompt.take(120)}")
            TempRun(temperature = temp, text = text)
        }

        call.respond(TempResponse(prompt = req.prompt, runs = runs))
    }
}


