package mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import models.Reminder
import models.ReminderSummary
import models.ReminderListResponse
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicInteger

/**
 * MCP client specifically for Reminder operations.
 */
class ReminderMcpClient(
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
            println("Starting Reminder MCP server: ${commandList.joinToString(" ")}")
            
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
            
            // Pass REMINDERS_FILE env var if set
            val env = pb.environment()
            val remindersFile = System.getenv("REMINDERS_FILE")
            if (remindersFile != null) {
                env["REMINDERS_FILE"] = remindersFile
            }
            
            pb.redirectErrorStream(false)
            process = pb.start()
            
            val stderrReader = BufferedReader(InputStreamReader(process!!.errorStream))
            Thread {
                try {
                    var line = stderrReader.readLine()
                    while (line != null) {
                        println("Reminder MCP Server stderr: $line")
                        line = stderrReader.readLine()
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }.start()
        } catch (e: Exception) {
            println("Failed to start Reminder MCP server: ${e.message}")
            e.printStackTrace()
            throw McpException("Failed to start Reminder MCP server process: ${e.message}", e)
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
                    put("name", "kmp-ai-app-reminder-client")
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
            throw McpException("No valid JSON response from Reminder MCP server")
        }
        
        val initResponse = json.parseToJsonElement(initResponseLine).jsonObject
        if (initResponse.containsKey("error")) {
            val error = initResponse["error"]!!.jsonObject
            val errorMsg = error["message"]?.jsonPrimitive?.content ?: "Unknown error"
            throw McpException("Reminder MCP initialize error: $errorMsg")
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
            throw McpException("Reminder MCP tools/call error: $errorMsg")
        }
        
        val result = response["result"]?.jsonObject ?: throw McpException("No result in Reminder MCP response")
        val contentArray = result["content"] ?: throw McpException("No content in Reminder MCP result")
        
        val contentItems = json.parseToJsonElement(contentArray.toString())
        val textContent = if (contentItems is kotlinx.serialization.json.JsonArray && contentItems.isNotEmpty()) {
            val firstItem = contentItems[0].jsonObject
            firstItem["text"]?.jsonPrimitive?.content
        } else {
            null
        }
        
        val text = textContent ?: throw McpException("No text content in Reminder MCP result")
        
        val parsedText = json.parseToJsonElement(text)
        if (parsedText is JsonObject && parsedText.containsKey("error")) {
            val errorMsg = parsedText["error"]?.jsonPrimitive?.content
            throw McpException("Reminder tool error: $errorMsg")
        }
        
        return text
    }
    
    suspend fun addReminder(text: String, dueDate: Long? = null): Reminder {
        val arguments = buildJsonObject {
            put("text", text)
            if (dueDate != null) put("dueDate", dueDate)
        }
        val responseText = callTool("reminder.add", arguments)
        return json.decodeFromString(Reminder.serializer(), responseText)
    }
    
    suspend fun listReminders(onlyPending: Boolean = false): ReminderListResponse {
        val arguments = buildJsonObject {
            put("onlyPending", onlyPending)
        }
        val text = callTool("reminder.list", arguments)
        return json.decodeFromString(ReminderListResponse.serializer(), text)
    }
    
    suspend fun summaryNow(): ReminderSummary {
        val arguments = buildJsonObject {}
        val text = callTool("reminder.summary_now", arguments)
        return json.decodeFromString(ReminderSummary.serializer(), text)
    }
    
    fun close() {
        process?.destroy()
        process = null
        stdin = null
        stdout = null
        initialized = false
    }
}

