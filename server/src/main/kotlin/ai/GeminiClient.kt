package ai

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.request.setBody
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import io.ktor.client.plugins.HttpTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.DeserializationStrategy

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
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val prettyJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
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

    /**
     * Generate structured output using Gemini's structured output feature
     * @param userText The text to process
     * @param responseSchema The JSON schema for the expected response
     * @param systemInstruction Optional system instruction (defaults to JSON output instruction)
     * @param model The model to use (defaults to gemini-1.5-flash-latest with fallback to -001)
     * @return The decoded structured response of type T
     */
    suspend fun <T> generateStructured(
        userText: String,
        responseSchema: JsonObject,
        systemInstruction: String = "You must output ONLY JSON matching the given schema.",
        model: String = "gemini-1.5-flash-latest",
        deserializer: kotlinx.serialization.DeserializationStrategy<T>
    ): T {
        if (apiKey.isEmpty()) {
            throw IllegalStateException("GEMINI_API_KEY not configured")
        }

        // Try v1beta endpoint first (structured output works best here)
        // Working combination: generationConfig + response_mime_type + response_json_schema
        val attempts = listOf(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent",
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent"
        )

        // Try the working combination first, then fallback options
        val fieldNameCombos = listOf(
            Triple("generationConfig", "response_mime_type", "response_json_schema"), // Working combination
            Triple("generationConfig", "response_mime_type", "response_schema"),       // Fallback
            Triple("generationConfig", "responseMimeType", "responseSchema")           // Fallback
        )

        for (url in attempts) {
            for ((genConfigName, mimeTypeName, schemaName) in fieldNameCombos) {
                try {
                    val body = buildJsonObject {
                        put("contents", buildJsonArray {
                            add(buildJsonObject {
                                put("role", "user")
                                put("parts", buildJsonArray {
                                    add(buildJsonObject { 
                                        put("text", "$systemInstruction\n\n$userText") 
                                    })
                                })
                            })
                        })
                        put(genConfigName, buildJsonObject {
                            put(mimeTypeName, JsonPrimitive("application/json"))
                            put(schemaName, responseSchema)
                        })
                    }

                    // Print pretty-printed request
                    println("=== REQUEST ===")
                    println("URL: $url")
                    println("Body:")
                    println(prettyJson.encodeToString(JsonObject.serializer(), body))
                    println()

                    val res: HttpResponse = http.post(url) {
                        parameter("key", apiKey)
                        contentType(ContentType.Application.Json)
                        setBody(body.toString())
                    }

                    // Print pretty-printed response
                    val responseBody = res.bodyAsText()
                    println("=== RESPONSE ===")
                    println("Status: ${res.status.value}")
                    println("Body:")
                    try {
                        val responseJson = Json.parseToJsonElement(responseBody)
                        when (responseJson) {
                            is JsonObject -> println(prettyJson.encodeToString(JsonObject.serializer(), responseJson))
                            is JsonArray -> println(prettyJson.encodeToString(JsonArray.serializer(), responseJson))
                            else -> println(responseBody)
                        }
                    } catch (e: Exception) {
                        println(responseBody)
                    }
                    println()

                    if (res.status.value == 200) {
                        val root = Json.parseToJsonElement(responseBody).jsonObject
                        val jsonPayload = root["candidates"]?.jsonArray?.firstOrNull()
                            ?.jsonObject?.get("content")?.jsonObject
                            ?.get("parts")?.jsonArray?.firstOrNull()
                            ?.jsonObject?.get("text")?.jsonPrimitive?.content

                        if (jsonPayload != null) {
                            // Print the extracted JSON payload (pretty printed)
                            println("=== EXTRACTED JSON PAYLOAD ===")
                            try {
                                val payloadJson = Json.parseToJsonElement(jsonPayload)
                                when (payloadJson) {
                                    is JsonObject -> println(prettyJson.encodeToString(JsonObject.serializer(), payloadJson))
                                    is JsonArray -> println(prettyJson.encodeToString(JsonArray.serializer(), payloadJson))
                                    else -> println(jsonPayload)
                                }
                            } catch (e: Exception) {
                                println(jsonPayload)
                            }
                            println()

                            val decoded = json.decodeFromString(deserializer, jsonPayload)
                            return decoded
                        }
                    }
                } catch (e: Exception) {
                    // Continue to next combination
                    continue
                }
            }
        }

        // Fallback: use regular generate with JSON prompt if structured output fails
        val jsonPrompt = """
            $systemInstruction
            
            You must output ONLY valid JSON matching this exact schema:
            ${responseSchema.toString()}
            
            $userText
            
            Output ONLY the JSON object, no markdown, no code blocks, no explanations.
        """.trimIndent()
        
        println("=== FALLBACK: Using regular generate method ===")
        println("Prompt:")
        println(jsonPrompt)
        println()
        
        val textResponse = generate(jsonPrompt)
        
        // Extract JSON from response (might be wrapped in markdown code blocks)
        val jsonText = textResponse
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        
        // Print the extracted JSON (pretty printed)
        println("=== FALLBACK RESPONSE ===")
        try {
            val payloadJson = Json.parseToJsonElement(jsonText)
            when (payloadJson) {
                is JsonObject -> println(prettyJson.encodeToString(JsonObject.serializer(), payloadJson))
                is JsonArray -> println(prettyJson.encodeToString(JsonArray.serializer(), payloadJson))
                else -> println(jsonText)
            }
        } catch (e: Exception) {
            println(jsonText)
        }
        println()
        
        val decoded = json.decodeFromString(deserializer, jsonText)
        return decoded
    }
    
    /**
     * Convenience inline function for reified type parameter
     */
    suspend inline fun <reified T> generateStructured(
        userText: String,
        responseSchema: JsonObject,
        systemInstruction: String = "You must output ONLY JSON matching the given schema.",
        model: String = "gemini-1.5-flash-latest"
    ): T = generateStructured(userText, responseSchema, systemInstruction, model, kotlinx.serialization.serializer<T>())
}
