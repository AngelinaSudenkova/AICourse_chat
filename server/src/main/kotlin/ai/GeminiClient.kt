package ai

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.request.setBody
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import io.ktor.client.plugins.HttpTimeout

class GeminiClient(
    private val apiKey: String,
    private val model: String = "models/gemini-2.5-flash"  // Full model name with models/ prefix
) {
    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000  // 60 second request timeout
            connectTimeoutMillis = 10_000  // 10 second connection timeout
            socketTimeoutMillis = 60_000   // 60 second socket timeout
        }
    }
    private var cachedModel: String? = null

    suspend fun listAvailableModels(): String {
        if (apiKey.isEmpty()) {
            return "Error: GEMINI_API_KEY not configured"
        }
        
        return try {
            val res: HttpResponse = http.get("https://generativelanguage.googleapis.com/v1/models") {
                parameter("key", apiKey)
            }
            res.bodyAsText()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun generate(prompt: String): String {
        if (apiKey.isEmpty()) {
            return "Error: GEMINI_API_KEY not configured"
        }
        
        // Try different model/endpoint combinations - use full model names with models/ prefix
        val attempts = listOf(
            "https://generativelanguage.googleapis.com/v1/$model:generateContent",
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent",
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-pro:generateContent",
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash:generateContent",
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash-001:generateContent"
        )
        
        var lastError: String? = null
        
        for (url in attempts) {
            try {
                val res: HttpResponse = http.post(url) {
                    parameter("key", apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("contents", buildJsonArray {
                            add(buildJsonObject {
                                put("parts", buildJsonArray { 
                                    add(buildJsonObject { 
                                        put("text", prompt) 
                                    }) 
                                })
                            })
                        })
                    }.toString())
                }
                
                val statusCode = res.status.value
                val responseBody = res.bodyAsText()
                
                if (statusCode == 200) {
                    val json = Json.parseToJsonElement(responseBody).jsonObject
                    
                    val text = json["candidates"]?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("content")?.jsonObject
                        ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("text")?.jsonPrimitive?.content
                    
                    if (text != null) {
                        // Cache the working model name (extract from URL like "models/gemini-2.5-flash")
                        cachedModel = url.split("/").dropLast(1).lastOrNull() ?: model
                        return text
                    }
                    
                    val error = json["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    if (error != null) {
                        lastError = "Error: $error"
                        continue
                    }
                } else {
                    val json = try {
                        Json.parseToJsonElement(responseBody).jsonObject
                    } catch (e: Exception) {
                        null
                    }
                    val error = json?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.content
                        ?: responseBody.take(200)
                    lastError = "Status $statusCode: $error"
                    continue
                }
                
            } catch (e: Exception) {
                lastError = "Exception: ${e.message}"
                continue
            }
        }
        
        val availableModels = listAvailableModels()
        
        return "All Gemini API endpoints failed. Available models: $availableModels. Check server logs for details."
    }
}
