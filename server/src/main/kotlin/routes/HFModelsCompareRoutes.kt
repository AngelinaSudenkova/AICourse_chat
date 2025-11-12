package routes

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.receive
import kotlinx.serialization.json.*
import structured.ModelRun
import structured.ModelSpec
import structured.ModelsCompareRequest
import structured.ModelsCompareResponse
import kotlin.math.max
import kotlin.math.roundToInt

private const val HF_CHAT_URL = "https://router.huggingface.co/v1/chat/completions"
private const val HF_COMPLETIONS_URL = "https://router.huggingface.co/v1/completions"

private val hfClient = HttpClient(CIO) {
    expectSuccess = false
}

private val providerOverrides = mapOf(
    "openai/gpt-oss-120b" to "openai/gpt-oss-120b:fastest",
    "deepseek-ai/DeepSeek-R1" to "deepseek-ai/DeepSeek-R1:fastest",
    "Qwen/Qwen2.5-7B-Instruct" to "Qwen/Qwen2.5-7B-Instruct:together"
)

private val softTokenLimits = mapOf(
    "openai/gpt-oss-120b" to 128_000,
    "openai/gpt-oss-120b:hf-inference" to 128_000,
    "openai/gpt-oss-120b:fastest" to 128_000,
    "Qwen/Qwen2.5-7B-Instruct" to 128_000,
    "Qwen/Qwen2.5-7B-Instruct:hf-inference" to 128_000,
    "Qwen/Qwen2.5-7B-Instruct:together" to 128_000,
    "deepseek-ai/DeepSeek-R1" to 32_768,
    "deepseek-ai/DeepSeek-R1:hf-inference" to 32_768,
    "deepseek-ai/DeepSeek-R1:fastest" to 32_768
)

private const val DEFAULT_SOFT_TOKEN_LIMIT = 32_768

private data class TokenUsage(val prompt: Int, val completion: Int, val total: Int)

fun Route.hfModelsCompareRoutes() {
    route("/models") {
        post("/compare") {
            val token = System.getenv("HF_API_TOKEN")
            if (token.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "HF_API_TOKEN environment variable is not set"))
                return@post
            }

            val request = call.receive<ModelsCompareRequest>()
            val sanitizedModels = request.models.filter { it.id.isNotBlank() }
            if (sanitizedModels.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No valid model IDs provided"))
                return@post
            }

            val runs = sanitizedModels.map { spec ->
                val resolvedModelId = spec.idWithProvider
                val softLimit = resolveSoftLimit(spec, resolvedModelId)
                runCatching {
                    queryModel(spec, resolvedModelId, request.prompt, token, softLimit)
                }.getOrElse { ex ->
                    errorRun(
                        model = spec.id,
                        prompt = request.prompt,
                        latencyMs = 0,
                        message = ex.message ?: ex::class.simpleName.orEmpty(),
                        softLimit = softLimit
                    )
                }
            }

            call.respond(ModelsCompareResponse(prompt = request.prompt, runs = runs))
        }
    }
}

private val ModelSpec.idWithProvider: String
    get() = when {
        id.contains(":") -> id
        providerOverrides.containsKey(id) -> providerOverrides.getValue(id)
        else -> "$id:hf-inference"
    }

private suspend fun queryModel(
    spec: ModelSpec,
    modelId: String,
    prompt: String,
    token: String,
    softLimit: Int
): ModelRun {
    val start = System.nanoTime()
    val (chatStatus, chatBody) = callChat(modelId, prompt, token)

    println("[HF] Chat request model=$modelId status=$chatStatus body=$chatBody")

    val chatSuccess = chatStatus in 200..299
    val fallbackNeeded = chatStatus == 400 && chatBody.contains("not", ignoreCase = true) &&
        (chatBody.contains("model_not_supported", ignoreCase = true) ||
         chatBody.contains("not a chat model", ignoreCase = true) ||
         chatBody.contains("model_not_found", ignoreCase = true))

    val finalOutput: String
    val finalStatus: Int
    if (chatSuccess) {
        finalOutput = extractChatOutput(chatBody)
        finalStatus = chatStatus
    } else if (fallbackNeeded) {
        val (compStatus, compBody) = callCompletions(modelId, prompt, token)
        println("[HF] Completion fallback model=$modelId status=$compStatus body=$compBody")
        finalStatus = compStatus
        finalOutput = if (compStatus in 200..299) extractCompletionOutput(compBody) else compBody
    } else {
        finalStatus = chatStatus
        finalOutput = chatBody
    }

    val latencyMs = (System.nanoTime() - start) / 1_000_000

    if (finalStatus !in 200..299) {
        return errorRun(
            model = spec.id,
            prompt = prompt,
            latencyMs = latencyMs,
            message = "HTTP $finalStatus - ${finalOutput.take(400)}",
            softLimit = softLimit
        )
    }

    val usage = computeUsage(prompt, finalOutput)
    val cost = estimateCost(spec, usage.prompt, usage.completion)

    return finishRun(
        model = spec.id,
        latencyMs = latencyMs,
        usage = usage,
        cost = cost,
        output = finalOutput,
        softLimit = softLimit
    )
}

private suspend fun callChat(modelId: String, prompt: String, token: String): Pair<Int, String> {
    val payload = buildJsonObject {
        put("model", JsonPrimitive(modelId))
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", JsonPrimitive(prompt))
            })
        })
        put("temperature", JsonPrimitive(0.7))
        put("max_tokens", JsonPrimitive(256))
    }

    val response = hfClient.post(HF_CHAT_URL) {
        header("Authorization", "Bearer $token")
        header("user-agent", "KMP-AI-Compare/1.0")
        contentType(ContentType.Application.Json)
        setBody(payload.toString())
    }
    return response.status.value to response.bodyAsText()
}

private suspend fun callCompletions(modelId: String, prompt: String, token: String): Pair<Int, String> {
    val payload = buildJsonObject {
        put("model", JsonPrimitive(modelId))
        put("prompt", JsonPrimitive(prompt))
        put("temperature", JsonPrimitive(0.7))
        put("max_tokens", JsonPrimitive(256))
    }

    val response = hfClient.post(HF_COMPLETIONS_URL) {
        header("Authorization", "Bearer $token")
        header("user-agent", "KMP-AI-Compare/1.0")
        contentType(ContentType.Application.Json)
        setBody(payload.toString())
    }
    return response.status.value to response.bodyAsText()
}

private fun approxTokens(text: String): Int {
    if (text.isBlank()) return 0
    return max(1, (text.length / 4.0).roundToInt())
}

private fun computeUsage(prompt: String, output: String): TokenUsage {
    val promptTokens = approxTokens(prompt)
    val completionTokens = approxTokens(output)
    return TokenUsage(promptTokens, completionTokens, promptTokens + completionTokens)
}

private fun finishRun(
    model: String,
    latencyMs: Long,
    usage: TokenUsage,
    cost: Double?,
    output: String,
    softLimit: Int
): ModelRun {
    val run = ModelRun(
        model = model,
        latencyMs = latencyMs,
        inputTokensApprox = usage.prompt,
        outputTokensApprox = usage.completion,
        totalTokensApprox = usage.total,
        costUSD = cost,
        output = output,
        overLimit = usage.total > softLimit,
        error = null
    )
    logRun(run, softLimit)
    return run
}

private fun errorRun(
    model: String,
    prompt: String,
    latencyMs: Long,
    message: String,
    softLimit: Int
): ModelRun {
    val usage = computeUsage(prompt, "")
    val run = ModelRun(
        model = model,
        latencyMs = latencyMs,
        inputTokensApprox = usage.prompt,
        outputTokensApprox = 0,
        totalTokensApprox = usage.total,
        costUSD = null,
        output = "",
        overLimit = usage.total > softLimit,
        error = message
    )
    logRun(run, softLimit)
    return run
}

private fun estimateCost(spec: ModelSpec, inputTokens: Int, outputTokens: Int): Double? {
    val inputCost = spec.pricePer1kInput?.let { (inputTokens / 1000.0) * it }
    val outputCost = spec.pricePer1kOutput?.let { (outputTokens / 1000.0) * it }
    val total = (inputCost ?: 0.0) + (outputCost ?: 0.0)
    return if (inputCost == null && outputCost == null) {
        null
    } else {
        ((total * 100.0).roundToInt()) / 100.0
    }
}

private fun extractOutput(raw: String): String {
    return try {
        val element = Json.parseToJsonElement(raw)
        extractFromElement(element) ?: raw
    } catch (_: Exception) {
        raw
    }
}

private fun extractChatOutput(raw: String): String {
    return try {
        val root = Json.parseToJsonElement(raw).jsonObject
        root["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content
            ?: raw
    } catch (_: Exception) {
        raw
    }
}

private fun extractCompletionOutput(raw: String): String {
    return try {
        val root = Json.parseToJsonElement(raw).jsonObject
        root["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: raw
    } catch (_: Exception) {
        raw
    }
}

private fun extractFromElement(element: JsonElement): String? {
    return when (element) {
        is JsonArray -> {
            element.firstNotNullOfOrNull { item ->
                when {
                    item is JsonPrimitive && item.isString -> item.content
                    item is JsonObject && item["generated_text"] != null -> item["generated_text"]?.jsonPrimitive?.content
                    item is JsonObject && item["summary_text"] != null -> item["summary_text"]?.jsonPrimitive?.content
                    item is JsonObject && item["text"] != null -> item["text"]?.jsonPrimitive?.content
                    else -> null
                }
            }
        }

        is JsonObject -> {
            when {
                element["generated_text"] != null -> element["generated_text"]?.jsonPrimitive?.content
                element["summary_text"] != null -> element["summary_text"]?.jsonPrimitive?.content
                element["text"] != null -> element["text"]?.jsonPrimitive?.content
                element["choices"] is JsonArray -> extractFromElement(element["choices"]!!)
                element["error"] != null -> "Error: ${element["error"]?.jsonPrimitive?.content}"
                else -> element.toString()
            }
        }

        is JsonPrimitive -> if (element.isString) element.content else element.toString()
    }
}

private fun logRun(run: ModelRun, softLimit: Int) {
    val snippet = if (run.output.isBlank()) "<no output>" else run.output.replace("\n", " ").take(200)
    println(
        "[HF][Result] model=${run.model} promptTokens=${run.inputTokensApprox} " +
            "outputTokens=${run.outputTokensApprox} totalTokens=${run.totalTokensApprox} softLimit=$softLimit " +
            "overLimit=${run.overLimit} error=${run.error ?: "none"} outputSnippet=\"$snippet\""
    )
}

private fun resolveSoftLimit(spec: ModelSpec, resolvedModelId: String): Int {
    val candidates = sequenceOf(
        resolvedModelId,
        resolvedModelId.substringBefore(":"),
        spec.id,
        spec.id.substringBefore(":")
    )
    return candidates.firstNotNullOfOrNull { softTokenLimits[it] } ?: DEFAULT_SOFT_TOKEN_LIMIT
}


