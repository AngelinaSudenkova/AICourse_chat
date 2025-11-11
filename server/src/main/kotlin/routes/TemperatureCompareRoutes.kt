package routes

import ai.GeminiClient
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import structured.CompareRequest
import structured.CompareSummary
import structured.TempRun
import kotlin.math.round

private val RequiredTemps = setOf(0.0, 0.7, 1.2)

fun Route.temperatureCompareRoutes(gemini: GeminiClient) {
    post("/temperature/compare") {
        val req = call.receive<CompareRequest>()
        val tempsPresent = req.runs.map { round(it.temperature * 10) / 10.0 }.toSet()

        if (!tempsPresent.containsAll(RequiredTemps)) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf(
                    "error" to "runs must include results for temperatures 0.0, 0.7, and 1.2"
                )
            )
        }

        val sortedRuns = req.runs.sortedBy { it.temperature }
        val comparisonPrompt = buildComparisonPrompt(req.prompt, sortedRuns)

        val schema = buildComparisonSchema()

        val result = runCatching {
            gemini.generateStructured<CompareSummary>(
                userText = comparisonPrompt,
                responseSchema = schema,
                systemInstruction = "Return ONLY JSON matching the schema. Be concise and concrete.",
                model = "gemini-1.5-flash-latest"
            )
        }.getOrElse { ex ->
            println("temperature.compare.error promptPreview=${req.prompt.take(120)}")
            ex.printStackTrace()
            throw ex
        }

        call.respond(result)
    }
}

private fun buildComparisonPrompt(prompt: String, runs: List<TempRun>): String {
    return buildString {
        appendLine("Compare three model outputs produced at different temperatures (0.0, 0.7, 1.2).")
        appendLine("Task prompt: $prompt")
        appendLine("For each temperature, assess: accuracy (or factual correctness for code tasks), creativity, diversity of phrasing/ideas, and risks (hallucinations, verbosity).")
        appendLine("Then summarize key differences, recommend best use cases per temperature, and give a final verdict.")

        runs.forEach { run ->
            appendLine()
            appendLine("--- Temperature ${run.temperature} ---")
            appendLine(run.text)
        }
    }
}

private fun buildComparisonSchema(): JsonObject {
    val temperatureProperties = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("temperature", buildJsonObject { put("type", "number") })
            put("accuracy", buildJsonObject { put("type", "string") })
            put("creativity", buildJsonObject { put("type", "string") })
            put("diversity", buildJsonObject { put("type", "string") })
            put("risks", buildJsonObject { put("type", "string") })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("temperature"))
            add(JsonPrimitive("accuracy"))
            add(JsonPrimitive("creativity"))
            add(JsonPrimitive("diversity"))
            add(JsonPrimitive("risks"))
        })
    }

    val bestUseCasesProperties = buildJsonObject {
        RequiredTemps.sorted().forEach { temp ->
            put(
                temp.toString(),
                buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject { put("type", "string") })
                }
            )
        }
    }

    return buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("perTemp", buildJsonObject {
                put("type", "array")
                put("items", temperatureProperties)
            })
            put("keyDifferences", buildJsonObject {
                put("type", "array")
                put("items", buildJsonObject { put("type", "string") })
            })
            put("bestUseCases", buildJsonObject {
                put("type", "object")
                put("properties", bestUseCasesProperties)
                put("required", buildJsonArray {
                    RequiredTemps.sorted().forEach { temp ->
                        add(JsonPrimitive(temp.toString()))
                    }
                })
            })
            put("verdict", buildJsonObject { put("type", "string") })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("perTemp"))
            add(JsonPrimitive("keyDifferences"))
            add(JsonPrimitive("bestUseCases"))
            add(JsonPrimitive("verdict"))
        })
    }
}


