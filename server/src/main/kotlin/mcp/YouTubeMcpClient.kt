package mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import models.YouTubeVideo
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicInteger

/**
 * MCP client for YouTube operations.
 */
class YouTubeMcpClient(
    private val command: String,
    private val args: List<String> = emptyList(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = false }
) {
    private val requestIdCounter = AtomicInteger(1)
    private var process: Process? = null
    private var stdin: BufferedWriter? = null
    private var stdout: BufferedReader? = null
    private var initialized = false
    
    private suspend fun ensureInitialized() {
        if (initialized && process?.isAlive == true) {
            return
        }
        
        process?.destroy()
        
        try {
            val currentDir = java.io.File(System.getProperty("user.dir"))
            val projectRoot = if (currentDir.name == "server") {
                currentDir.parentFile
            } else {
                currentDir
            }
            
            val resolvedArgs = args.map { arg ->
                val argFile = java.io.File(arg)
                if (!argFile.isAbsolute) {
                    val relativeToProject = java.io.File(projectRoot, arg)
                    if (relativeToProject.exists()) {
                        relativeToProject.absolutePath
                    } else {
                        arg
                    }
                } else {
                    arg
                }
            }
            
            val pb = ProcessBuilder(listOf(command) + resolvedArgs)
                .directory(projectRoot)
            
            // Pass YOUTUBE_API_KEY if available
            val env = pb.environment()
            val youtubeApiKey = System.getenv("YOUTUBE_API_KEY")
            if (youtubeApiKey != null) {
                env["YOUTUBE_API_KEY"] = youtubeApiKey
            }
            
            process = pb.start()
            stdin = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            stdout = BufferedReader(InputStreamReader(process!!.inputStream))
            
            // Initialize MCP connection
            val initRequest = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", requestIdCounter.getAndIncrement())
                put("method", "initialize")
                put("params", buildJsonObject {
                    put("protocolVersion", "2024-11-05")
                    put("capabilities", buildJsonObject {})
                    put("clientInfo", buildJsonObject {
                        put("name", "youtube-mcp-client")
                        put("version", "1.0.0")
                    })
                })
            }
            
            stdin!!.write(json.encodeToString(JsonObject.serializer(), initRequest))
            stdin!!.newLine()
            stdin!!.flush()
            
            val initResponseLine = stdout!!.readLine() ?: throw McpException("No response from MCP server")
            val initResponse = json.parseToJsonElement(initResponseLine).jsonObject
            
            if (initResponse.containsKey("error")) {
                val error = initResponse["error"]!!.jsonObject
                throw McpException("MCP initialize error: ${error["message"]?.jsonPrimitive?.content}")
            }
            
            // Send initialized notification
            val initializedNotification = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "notifications/initialized")
            }
            stdin!!.write(json.encodeToString(JsonObject.serializer(), initializedNotification))
            stdin!!.newLine()
            stdin!!.flush()
            
            initialized = true
        } catch (e: Exception) {
            throw McpException("Failed to start YouTube MCP server: ${e.message}", e)
        }
    }
    
    suspend fun searchExplainVideos(topic: String, maxResults: Int = 5): List<YouTubeVideo> {
        ensureInitialized()
        
        val query = "$topic explained for beginners"
        
        val toolCallRequest = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestIdCounter.getAndIncrement())
            put("method", "tools/call")
            put("params", buildJsonObject {
                put("name", "youtube.search_explain")
                put("arguments", buildJsonObject {
                    put("query", query)
                    put("maxResults", maxResults)
                })
            })
        }
        
        val requestStr = json.encodeToString(JsonObject.serializer(), toolCallRequest)
        stdin!!.write(requestStr)
        stdin!!.newLine()
        stdin!!.flush()
        
        val responseLine = stdout!!.readLine() ?: throw McpException("No response to tools/call request")
        val response = json.parseToJsonElement(responseLine).jsonObject
        
        if (response.containsKey("error")) {
            val error = response["error"]!!.jsonObject
            throw McpException("MCP tools/call error: ${error["message"]?.jsonPrimitive?.content}")
        }
        
        val result = response["result"]?.jsonObject
            ?: throw McpException("No result in MCP response")
        
        val contentArray = result["content"]
            ?: throw McpException("No content in MCP result")
        
        val contentItems = json.parseToJsonElement(contentArray.toString())
        val textContent = if (contentItems is kotlinx.serialization.json.JsonArray && contentItems.isNotEmpty()) {
            val firstItem = contentItems[0].jsonObject
            firstItem["text"]?.jsonPrimitive?.content
        } else {
            null
        }
        
        val text = textContent ?: throw McpException("No text content in MCP result")
        
        val parsedText = json.parseToJsonElement(text)
        if (parsedText is JsonObject && parsedText.containsKey("error")) {
            val errorMsg = parsedText["error"]?.jsonPrimitive?.content
            throw McpException("YouTube API error: $errorMsg")
        }
        
        @kotlinx.serialization.Serializable
        data class YouTubeSearchResult(
            val videos: List<YouTubeVideo>
        )
        
        val searchResult = json.decodeFromString(YouTubeSearchResult.serializer(), text)
        return searchResult.videos
    }
    
    fun close() {
        process?.destroy()
        process = null
        stdin = null
        stdout = null
        initialized = false
    }
}

