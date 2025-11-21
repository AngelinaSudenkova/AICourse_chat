package mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import models.NewsSearchResult
import models.SaveFileResult
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicInteger

/**
 * MCP client for research operations (search docs, save files).
 */
class ResearchMcpClient(
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
            val commandList = listOf(command) + args
            println("Starting Research MCP server: ${commandList.joinToString(" ")}")
            
            val currentDir = java.io.File(System.getProperty("user.dir"))
            val projectRoot = if (currentDir.name == "server") {
                currentDir.parentFile
            } else {
                currentDir
            }
            
            val resolvedArgs = args.map { arg ->
                val argFile = java.io.File(arg)
                if (!argFile.isAbsolute) {
                    val relativeToCurrent = java.io.File(currentDir, arg)
                    if (relativeToCurrent.exists()) {
                        relativeToCurrent.absolutePath
                    } else {
                        val relativeToProject = java.io.File(projectRoot, arg)
                        if (relativeToProject.exists()) {
                            relativeToProject.absolutePath
                        } else {
                            arg
                        }
                    }
                } else if (argFile.exists()) {
                    argFile.absolutePath
                } else {
                    arg
                }
            }
            
            val finalCommandList = listOf(command) + resolvedArgs
            val pb = ProcessBuilder(finalCommandList).directory(projectRoot)
            
            val env = pb.environment()
            val newsApiKey = System.getenv("NEWS_API_KEY")
            if (newsApiKey != null) {
                env["NEWS_API_KEY"] = newsApiKey
            }
            val researchDir = System.getenv("RESEARCH_DIR")
            if (researchDir != null) {
                env["RESEARCH_DIR"] = researchDir
            }
            
            pb.redirectErrorStream(false)
            process = pb.start()
            
            val stderrReader = BufferedReader(InputStreamReader(process!!.errorStream))
            Thread {
                try {
                    var line = stderrReader.readLine()
                    while (line != null) {
                        println("Research MCP Server stderr: $line")
                        line = stderrReader.readLine()
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }.start()
        } catch (e: Exception) {
            println("Failed to start Research MCP server: ${e.message}")
            e.printStackTrace()
            throw McpException("Failed to start Research MCP server process: ${e.message}", e)
        }
        
        stdin = BufferedWriter(OutputStreamWriter(process!!.outputStream))
        stdout = BufferedReader(InputStreamReader(process!!.inputStream))
        
        val initRequest = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestIdCounter.getAndIncrement())
            put("method", "initialize")
            put("params", buildJsonObject {
                put("protocolVersion", "2024-11-05")
                put("capabilities", buildJsonObject {})
                put("clientInfo", buildJsonObject {
                    put("name", "kmp-ai-app-research-client")
                    put("version", "1.0.0")
                })
            })
        }
        
        val initRequestStr = json.encodeToString(JsonObject.serializer(), initRequest)
        stdin!!.write(initRequestStr)
        stdin!!.newLine()
        stdin!!.flush()
        
        var initResponseLine: String? = null
        var attempts = 0
        while (initResponseLine == null && attempts < 10) {
            val line = stdout!!.readLine()
            if (line != null && (line.trim().startsWith("{") || line.trim().startsWith("["))) {
                initResponseLine = line
            } else {
                attempts++
            }
        }
        
        if (initResponseLine == null) {
            throw McpException("No valid JSON response from Research MCP server")
        }
        
        val initResponse = json.parseToJsonElement(initResponseLine).jsonObject
        if (initResponse.containsKey("error")) {
            val error = initResponse["error"]!!.jsonObject
            val errorMsg = error["message"]?.jsonPrimitive?.content ?: "Unknown error"
            throw McpException("Research MCP initialize error: $errorMsg")
        }
        
        val initializedNotification = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "notifications/initialized")
        }
        val notificationStr = json.encodeToString(JsonObject.serializer(), initializedNotification)
        stdin!!.write(notificationStr)
        stdin!!.newLine()
        stdin!!.flush()
        
        initialized = true
    }
    
    private suspend fun callTool(toolName: String, arguments: JsonObject): String {
        ensureInitialized()
        
        val toolCallRequest = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestIdCounter.getAndIncrement())
            put("method", "tools/call")
            put("params", buildJsonObject {
                put("name", toolName)
                put("arguments", arguments)
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
            val errorMsg = error["message"]?.jsonPrimitive?.content ?: "Unknown error"
            throw McpException("Research MCP tools/call error: $errorMsg")
        }
        
        val result = response["result"]?.jsonObject ?: throw McpException("No result in Research MCP response")
        val contentArray = result["content"] ?: throw McpException("No content in Research MCP result")
        
        val contentItems = json.parseToJsonElement(contentArray.toString())
        val textContent = if (contentItems is kotlinx.serialization.json.JsonArray && contentItems.isNotEmpty()) {
            val firstItem = contentItems[0].jsonObject
            firstItem["text"]?.jsonPrimitive?.content
        } else {
            null
        }
        
        val text = textContent ?: throw McpException("No text content in Research MCP result")
        
        val parsedText = json.parseToJsonElement(text)
        if (parsedText is JsonObject && parsedText.containsKey("error")) {
            val errorMsg = parsedText["error"]?.jsonPrimitive?.content
            throw McpException("Research tool error: $errorMsg")
        }
        
        return text
    }
    
    suspend fun searchDocs(query: String, pageSize: Int = 10): NewsSearchResult {
        val arguments = buildJsonObject {
            put("query", query)
            put("pageSize", pageSize)
        }
        val responseText = callTool("news.search_docs", arguments)
        return json.decodeFromString(NewsSearchResult.serializer(), responseText)
    }
    
    suspend fun saveToFile(filename: String, content: String): SaveFileResult {
        val arguments = buildJsonObject {
            put("filename", filename)
            put("content", content)
        }
        val responseText = callTool("fs.save_to_file", arguments)
        return json.decodeFromString(SaveFileResult.serializer(), responseText)
    }
    
    fun close() {
        process?.destroy()
        process = null
        stdin = null
        stdout = null
        initialized = false
    }
}

