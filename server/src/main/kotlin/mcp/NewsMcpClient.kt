package mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import models.TopHeadlinesResult
import models.NewsArticle
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicInteger

/**
 * MCP client specifically for NewsAPI operations.
 * 
 * This client maintains a long-lived connection to the MCP server
 * and can call the news.get_top_headlines and news.search tools.
 */
class NewsMcpClient(
    private val command: String,
    private val args: List<String> = emptyList(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = false },
    private val jsonPretty: Json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }
) {
    private val requestIdCounter = AtomicInteger(1)
    private var process: Process? = null
    private var stdin: BufferedWriter? = null
    private var stdout: BufferedReader? = null
    private var initialized = false
    
    /**
     * Initializes the MCP connection if not already initialized.
     */
    private suspend fun ensureInitialized() {
        if (initialized && process?.isAlive == true) {
            return
        }
        
        // Clean up old process if exists
        process?.destroy()
        
        // Start new process
        try {
            val commandList = listOf(command) + args
            println("Starting News MCP server: ${commandList.joinToString(" ")}")
            
            // Resolve relative paths to absolute paths
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
                        val resolved = relativeToCurrent.absolutePath
                        println("Resolved path (current dir): $arg -> $resolved")
                        resolved
                    } else {
                        val relativeToProject = java.io.File(projectRoot, arg)
                        if (relativeToProject.exists()) {
                            val resolved = relativeToProject.absolutePath
                            println("Resolved path (project root): $arg -> $resolved")
                            resolved
                        } else {
                            println("Path not found, using as-is: $arg")
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
            println("Final command: ${finalCommandList.joinToString(" ")}")
            
            val pb = ProcessBuilder(finalCommandList)
                .directory(projectRoot)
            
            // Inherit environment variables from parent process (including NEWS_API_KEY)
            val env = pb.environment()
            val newsApiKey = System.getenv("NEWS_API_KEY")
            if (newsApiKey != null) {
                env["NEWS_API_KEY"] = newsApiKey
                println("Passing NEWS_API_KEY to MCP server process")
            } else {
                println("WARNING: NEWS_API_KEY not found in environment")
            }
            
            pb.redirectErrorStream(false)
            
            process = pb.start()
            
            // Read stderr in background for debugging
            val stderrReader = BufferedReader(InputStreamReader(process!!.errorStream))
            Thread {
                try {
                    var line = stderrReader.readLine()
                    while (line != null) {
                        println("News MCP Server stderr: $line")
                        line = stderrReader.readLine()
                    }
                } catch (e: Exception) {
                    // Ignore errors reading stderr
                }
            }.start()
        } catch (e: Exception) {
            println("Failed to start News MCP server: ${e.message}")
            e.printStackTrace()
            throw McpException("Failed to start News MCP server process: ${e.message}", e)
        }
        
        stdin = BufferedWriter(OutputStreamWriter(process!!.outputStream))
        stdout = BufferedReader(InputStreamReader(process!!.inputStream))
        
        // Send initialize request
        val initRequest = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestIdCounter.getAndIncrement())
            put("method", "initialize")
            put("params", buildJsonObject {
                put("protocolVersion", "2024-11-05")
                put("capabilities", buildJsonObject {})
                put("clientInfo", buildJsonObject {
                    put("name", "kmp-ai-app-news-client")
                    put("version", "1.0.0")
                })
            })
        }
        
        val initRequestStr = json.encodeToString(JsonObject.serializer(), initRequest)
        stdin!!.write(initRequestStr)
        stdin!!.newLine()
        stdin!!.flush()
        
        // Read initialize response
        var initResponseLine: String? = null
        var attempts = 0
        while (initResponseLine == null && attempts < 10) {
            val line = stdout!!.readLine()
            if (line != null) {
                if (line.trim().startsWith("{") || line.trim().startsWith("[")) {
                    initResponseLine = line
                } else {
                    println("News MCP Server output (non-JSON, skipping): $line")
                    attempts++
                }
            } else {
                throw McpException("No response from News MCP server after $attempts attempts")
            }
        }
        
        if (initResponseLine == null) {
            throw McpException("No valid JSON response from News MCP server")
        }
        
        println("News MCP Initialize Response: $initResponseLine")
        val initResponse = json.parseToJsonElement(initResponseLine).jsonObject
        
        if (initResponse.containsKey("error")) {
            val error = initResponse["error"]!!.jsonObject
            val errorMsg = error["message"]?.jsonPrimitive?.content ?: "Unknown error"
            println("News MCP Initialize Error: $errorMsg")
            throw McpException("News MCP initialize error: $errorMsg")
        }
        
        // Send initialized notification
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
    
    /**
     * Gets top headlines from NewsAPI.
     */
    suspend fun getTopHeadlines(
        country: String? = null,
        category: String? = null,
        pageSize: Int = 20
    ): TopHeadlinesResult {
        ensureInitialized()
        
        val toolCallRequest = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestIdCounter.getAndIncrement())
            put("method", "tools/call")
            put("params", buildJsonObject {
                put("name", "news.get_top_headlines")
                put("arguments", buildJsonObject {
                    if (country != null) put("country", country)
                    if (category != null) put("category", category)
                    put("pageSize", pageSize)
                })
            })
        }
        
        val requestStr = json.encodeToString(JsonObject.serializer(), toolCallRequest)
        stdin!!.write(requestStr)
        stdin!!.newLine()
        stdin!!.flush()
        
        val responseLine = stdout!!.readLine() ?: throw McpException("No response to tools/call request")
        println("News MCP Tools/Call Response: $responseLine")
        val response = json.parseToJsonElement(responseLine).jsonObject
        
        if (response.containsKey("error")) {
            val error = response["error"]!!.jsonObject
            val errorMsg = error["message"]?.jsonPrimitive?.content ?: "Unknown error"
            println("News MCP Tools/Call Error: $errorMsg")
            throw McpException("News MCP tools/call error: $errorMsg")
        }
        
        val result = response["result"]?.jsonObject
            ?: throw McpException("No result in News MCP response")
        
        // Extract content from result
        val contentArray = result["content"]
            ?: throw McpException("No content in News MCP result")
        
        val contentItems = json.parseToJsonElement(contentArray.toString())
        val textContent = if (contentItems is kotlinx.serialization.json.JsonArray && contentItems.isNotEmpty()) {
            val firstItem = contentItems[0].jsonObject
            firstItem["text"]?.jsonPrimitive?.content
        } else {
            null
        }
        
        val text = textContent ?: throw McpException("No text content in News MCP result")
        
        // Check if it's an error response
        val parsedText = json.parseToJsonElement(text)
        if (parsedText is JsonObject && parsedText.containsKey("error")) {
            val errorMsg = parsedText["error"]?.jsonPrimitive?.content
            throw McpException("NewsAPI error: $errorMsg")
        }
        
        // Parse the TopHeadlinesResult
        return json.decodeFromString(TopHeadlinesResult.serializer(), text)
    }
    
    /**
     * Searches news articles from NewsAPI.
     */
    suspend fun searchNews(
        query: String,
        pageSize: Int = 20,
        sortBy: String = "publishedAt"
    ): TopHeadlinesResult {
        ensureInitialized()
        
        val toolCallRequest = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestIdCounter.getAndIncrement())
            put("method", "tools/call")
            put("params", buildJsonObject {
                put("name", "news.search")
                put("arguments", buildJsonObject {
                    put("query", query)
                    put("pageSize", pageSize)
                    put("sortBy", sortBy)
                })
            })
        }
        
        val requestStr = json.encodeToString(JsonObject.serializer(), toolCallRequest)
        stdin!!.write(requestStr)
        stdin!!.newLine()
        stdin!!.flush()
        
        val responseLine = stdout!!.readLine() ?: throw McpException("No response to tools/call request")
        println("News MCP Tools/Call Response: $responseLine")
        val response = json.parseToJsonElement(responseLine).jsonObject
        
        if (response.containsKey("error")) {
            val error = response["error"]!!.jsonObject
            val errorMsg = error["message"]?.jsonPrimitive?.content ?: "Unknown error"
            println("News MCP Tools/Call Error: $errorMsg")
            throw McpException("News MCP tools/call error: $errorMsg")
        }
        
        val result = response["result"]?.jsonObject
            ?: throw McpException("No result in News MCP response")
        
        // Extract content from result
        val contentArray = result["content"]
            ?: throw McpException("No content in News MCP result")
        
        val contentItems = json.parseToJsonElement(contentArray.toString())
        val textContent = if (contentItems is kotlinx.serialization.json.JsonArray && contentItems.isNotEmpty()) {
            val firstItem = contentItems[0].jsonObject
            firstItem["text"]?.jsonPrimitive?.content
        } else {
            null
        }
        
        val text = textContent ?: throw McpException("No text content in News MCP result")
        
        // Check if it's an error response
        val parsedText = json.parseToJsonElement(text)
        if (parsedText is JsonObject && parsedText.containsKey("error")) {
            val errorMsg = parsedText["error"]?.jsonPrimitive?.content
            throw McpException("NewsAPI error: $errorMsg")
        }
        
        // Parse the result (it will have query instead of country/category)
        // We'll convert it to TopHeadlinesResult format
        val searchResult = json.parseToJsonElement(text).jsonObject
        val articles = json.decodeFromString<List<NewsArticle>>(searchResult["articles"].toString())
        val totalResults = searchResult["totalResults"]?.jsonPrimitive?.content?.toIntOrNull() ?: articles.size
        val fetchedAt = searchResult["fetchedAt"]?.jsonPrimitive?.content ?: ""
        
        return TopHeadlinesResult(
            articles = articles,
            totalResults = totalResults,
            country = null,
            category = null,
            fetchedAt = fetchedAt
        )
    }
    
    /**
     * Closes the MCP connection and cleans up resources.
     */
    fun close() {
        process?.destroy()
        process = null
        stdin = null
        stdout = null
        initialized = false
    }
}

