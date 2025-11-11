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

    suspend fun generate(prompt: String, temperature: Double? = null): String {
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
                val body = buildJsonObject {
                    put("contents", buildJsonArray {
                        add(buildJsonObject {
                            put("parts", buildJsonArray {
                                add(buildJsonObject {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                    if (temperature != null) {
                        put("generationConfig", buildJsonObject {
                            put("temperature", JsonPrimitive(temperature))
                        })
                    }
                }

                val res: HttpResponse = http.post(url) {
                    parameter("key", apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
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
    
    /**
     * Generate a journal entry using structured output
     * Conducts a calm dialogue about the user's day and returns a structured journal response
     */
    suspend fun generateJournalEntry(
        conversationHistory: List<String>,
        responseSchema: JsonObject
    ): structured.JournalResponse {
        if (apiKey.isEmpty()) {
            throw IllegalStateException("GEMINI_API_KEY not configured")
        }
        
        val systemInstruction = """
            You are a calm, supportive journaling assistant. Your role is to conduct a short, thoughtful dialogue 
            about the user's day to help them reflect. You should:
            
            1. Ask gentle, open-ended questions to understand their day
            2. Collect information about their mood, key moments, lessons learned, gratitude, and next steps
            3. When you have enough information (typically 2-4 exchanges), set status to "ready" and provide the journal entry
            4. If you need more information, set status to "collecting" and in the "missing" array, provide ACTUAL CONVERSATIONAL QUESTIONS 
               that you would ask the user (NOT just field names). For example:
               - Instead of "date", write "What date is today?"
               - Instead of "mood", write "How was your mood today? Would you rate it as very bad, bad, neutral, good, or very good?"
               - Instead of "keyMoments", write "What were the most significant moments of your day?"
               - Instead of "lessons", write "What did you learn today?"
               - Instead of "gratitude", write "What are you grateful for today?"
               - Instead of "nextSteps", write "What would you like to focus on tomorrow or in the coming days?"
               - Instead of "reflectionSummary", write questions that help synthesize their day
            5. Use the mood scale: very_bad (1), bad (2), neutral (3), good (4), very_good (5)
            6. For dates, use YYYY-MM-DD format
            7. Keep questions brief, natural, and conversational - ask one or two questions at a time
            8. Make the questions feel warm and supportive, not like a form to fill out
            9. Output ONLY valid JSON matching the schema - no additional text or explanations
        """.trimIndent()
        
        // Build conversation context
        val conversationText = conversationHistory.joinToString("\n") { msg ->
            if (msg.startsWith("User: ") || msg.startsWith("Assistant: ")) {
                msg
            } else {
                "User: $msg"
            }
        }
        
        val userText = if (conversationHistory.isEmpty()) {
            "Hi, I'd like to reflect on my day. Can you help me with a journal entry?"
        } else {
            conversationText
        }
        
        // Try v1beta endpoint first (structured output works best here)
        val attempts = listOf(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent"
        )
        
        val fieldNameCombos = listOf(
            Triple("generationConfig", "response_mime_type", "response_json_schema"),
            Triple("generationConfig", "response_mime_type", "response_schema"),
            Triple("generationConfig", "responseMimeType", "responseSchema")
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
                            put("stop_sequences", buildJsonArray {
                                add(JsonPrimitive("<END_JOURNAL>"))
                            })
                        })
                    }
                    
                    // Print pretty-printed request
                    println("=== JOURNAL REQUEST ===")
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
                    println("=== JOURNAL RESPONSE ===")
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
                            println("=== JOURNAL EXTRACTED JSON PAYLOAD ===")
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
                            
                            val decoded = json.decodeFromString<structured.JournalResponse>(jsonPayload)
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
        
        println("=== JOURNAL FALLBACK: Using regular generate method ===")
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
        println("=== JOURNAL FALLBACK RESPONSE ===")
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
        
        val decoded = json.decodeFromString<structured.JournalResponse>(jsonText)
        return decoded
    }
}
