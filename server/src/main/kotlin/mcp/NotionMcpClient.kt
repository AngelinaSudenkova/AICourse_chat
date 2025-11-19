package mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import models.FinanceEntry
import models.FinanceEntriesResult
import models.McpTool
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicInteger

/**
 * MCP client specifically for Notion finance operations.
 * 
 * This client maintains a long-lived connection to the MCP server
 * and can call the notion.finance_get_entries tool.
 */
class NotionMcpClient(
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
            println("Starting MCP server: ${commandList.joinToString(" ")}")
            
            // Resolve relative paths to absolute paths
            // The working directory might be the server directory, so we need to go up one level
            val currentDir = java.io.File(System.getProperty("user.dir"))
            val projectRoot = if (currentDir.name == "server") {
                currentDir.parentFile
            } else {
                currentDir
            }
            
            val resolvedArgs = args.map { arg ->
                val argFile = java.io.File(arg)
                if (!argFile.isAbsolute) {
                    // Try relative to current directory first
                    val relativeToCurrent = java.io.File(currentDir, arg)
                    if (relativeToCurrent.exists()) {
                        val resolved = relativeToCurrent.absolutePath
                        println("Resolved path (current dir): $arg -> $resolved")
                        resolved
                    } else {
                        // Try relative to project root
                        val relativeToProject = java.io.File(projectRoot, arg)
                        if (relativeToProject.exists()) {
                            val resolved = relativeToProject.absolutePath
                            println("Resolved path (project root): $arg -> $resolved")
                            resolved
                        } else {
                            // Use as-is if file doesn't exist (might be created later)
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
                .directory(projectRoot) // Set working directory to project root
            
            // Inherit environment variables from parent process (including NOTION_API_TOKEN)
            val env = pb.environment()
            // Explicitly pass NOTION_API_TOKEN if it's set
            val notionToken = System.getenv("NOTION_API_TOKEN")
            if (notionToken != null) {
                env["NOTION_API_TOKEN"] = notionToken
                println("Passing NOTION_API_TOKEN to MCP server process")
            } else {
                println("WARNING: NOTION_API_TOKEN not found in environment")
            }
            
            // Also pass NOTION_FINANCE_DATABASE_ID if set
            val databaseId = System.getenv("NOTION_FINANCE_DATABASE_ID")
            if (databaseId != null) {
                env["NOTION_FINANCE_DATABASE_ID"] = databaseId
            }
            
            // Don't redirect error stream - we'll read it separately for debugging
            pb.redirectErrorStream(false)
            
            process = pb.start()
            
            // Read stderr in background for debugging
            val stderrReader = BufferedReader(InputStreamReader(process!!.errorStream))
            Thread {
                try {
                    var line = stderrReader.readLine()
                    while (line != null) {
                        println("MCP Server stderr: $line")
                        line = stderrReader.readLine()
                    }
                } catch (e: Exception) {
                    // Ignore errors reading stderr
                }
            }.start()
        } catch (e: Exception) {
            println("Failed to start MCP server: ${e.message}")
            e.printStackTrace()
            throw McpException("Failed to start Notion MCP server process: ${e.message}", e)
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
                    put("name", "kmp-ai-app-notion-client")
                    put("version", "1.0.0")
                })
            })
        }
        
        val initRequestStr = json.encodeToString(JsonObject.serializer(), initRequest)
        stdin!!.write(initRequestStr)
        stdin!!.newLine()
        stdin!!.flush()
        
        // Read initialize response
        // Skip any non-JSON lines (error messages, etc.)
        var initResponseLine: String? = null
        var attempts = 0
        while (initResponseLine == null && attempts < 10) {
            val line = stdout!!.readLine()
            if (line != null) {
                // Check if it looks like JSON (starts with { or [)
                if (line.trim().startsWith("{") || line.trim().startsWith("[")) {
                    initResponseLine = line
                } else {
                    println("MCP Server output (non-JSON, skipping): $line")
                    attempts++
                }
            } else {
                throw McpException("No response from MCP server after $attempts attempts")
            }
        }
        
        if (initResponseLine == null) {
            throw McpException("No valid JSON response from MCP server")
        }
        
        println("MCP Initialize Response: $initResponseLine")
        val initResponse = json.parseToJsonElement(initResponseLine).jsonObject
        
        if (initResponse.containsKey("error")) {
            val error = initResponse["error"]!!.jsonObject
            val errorMsg = error["message"]?.jsonPrimitive?.content ?: "Unknown error"
            println("MCP Initialize Error: $errorMsg")
            throw McpException("MCP initialize error: $errorMsg")
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
     * Lists available tools from the MCP server.
     */
    suspend fun listTools(): List<McpTool> {
        ensureInitialized()
        
        val toolsListRequest = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestIdCounter.getAndIncrement())
            put("method", "tools/list")
            put("params", buildJsonObject {})
        }
        
        val requestStr = json.encodeToString(JsonObject.serializer(), toolsListRequest)
        stdin!!.write(requestStr)
        stdin!!.newLine()
        stdin!!.flush()
        
        val responseLine = stdout!!.readLine() ?: throw McpException("No response to tools/list request")
        val response = json.parseToJsonElement(responseLine).jsonObject
        
        if (response.containsKey("error")) {
            val error = response["error"]!!.jsonObject
            throw McpException("MCP tools/list error: ${error["message"]?.jsonPrimitive?.content}")
        }
        
        val result = response["result"]?.jsonObject
            ?: throw McpException("No result in MCP response")
        
        val toolsElement = result["tools"]
            ?: throw McpException("No tools array in MCP response")
        
        val toolsList = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(McpTool.serializer()),
            toolsElement.toString()
        )
        
        return toolsList
    }
    
    /**
     * Calls the notion.finance_get_entries tool to retrieve finance entries.
     */
    suspend fun financeGetEntries(
        fromDate: String? = null,
        toDate: String? = null,
        limit: Int = 50
    ): FinanceEntriesResult {
        ensureInitialized()
        
        val toolCallRequest = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestIdCounter.getAndIncrement())
            put("method", "tools/call")
            put("params", buildJsonObject {
                put("name", "notion.finance_get_entries")
                put("arguments", buildJsonObject {
                    if (fromDate != null) put("fromDate", fromDate)
                    if (toDate != null) put("toDate", toDate)
                    put("limit", limit)
                })
            })
        }
        
        val requestStr = json.encodeToString(JsonObject.serializer(), toolCallRequest)
        stdin!!.write(requestStr)
        stdin!!.newLine()
        stdin!!.flush()
        
        val responseLine = stdout!!.readLine() ?: throw McpException("No response to tools/call request")
        println("MCP Tools/Call Response: $responseLine")
        val response = json.parseToJsonElement(responseLine).jsonObject
        
        if (response.containsKey("error")) {
            val error = response["error"]!!.jsonObject
            val errorMsg = error["message"]?.jsonPrimitive?.content ?: "Unknown error"
            println("MCP Tools/Call Error: $errorMsg")
            throw McpException("MCP tools/call error: $errorMsg")
        }
        
        val result = response["result"]?.jsonObject
            ?: throw McpException("No result in MCP response")
        
        // Extract content from result
        // MCP SDK returns content as an array of content items
        val contentArray = result["content"]
            ?: throw McpException("No content in MCP result")
        
        // Parse content array
        val contentItems = json.parseToJsonElement(contentArray.toString())
        val textContent = if (contentItems is kotlinx.serialization.json.JsonArray && contentItems.isNotEmpty()) {
            val firstItem = contentItems[0].jsonObject
            firstItem["text"]?.jsonPrimitive?.content
        } else {
            null
        }
        
        val text = textContent ?: throw McpException("No text content in MCP result")
        
        // Check if it's an error response
        val parsedText = json.parseToJsonElement(text)
        if (parsedText is JsonObject && parsedText.containsKey("error")) {
            val errorMsg = parsedText["error"]?.jsonPrimitive?.content
            throw McpException("Notion API error: $errorMsg")
        }
        
        // Parse the FinanceEntriesResult
        return json.decodeFromString(FinanceEntriesResult.serializer(), text)
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

