package mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import models.McpTool
import models.McpJsonMessage
import models.McpToolsResponse
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicInteger

/**
 * MCP (Model Context Protocol) client that communicates with an MCP server via stdio.
 * 
 * The client sends JSON-RPC 2.0 requests and parses JSON-RPC 2.0 responses.
 */
class McpClient(
    private val command: String,
    private val args: List<String> = emptyList(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = false }, // Compact for transmission
    private val jsonPretty: Json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true } // Pretty for display
) {
    private val requestIdCounter = AtomicInteger(1)
    
    /**
     * Lists available tools from the MCP server.
     * 
     * @return McpToolsResponse containing tools and JSON request/response messages
     * @throws McpException if communication fails
     */
    suspend fun listTools(): McpToolsResponse {
        val process = try {
            val commandList = listOf(command) + args
            ProcessBuilder(commandList)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            throw McpException("Failed to start MCP server process: ${e.message}", e)
        }
        
        val messages = mutableListOf<McpJsonMessage>()
        
        try {
            val stdin = BufferedWriter(OutputStreamWriter(process.outputStream))
            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            
            // Send initialize request first (MCP protocol requirement)
            val initRequest = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", requestIdCounter.getAndIncrement())
                put("method", "initialize")
                put("params", buildJsonObject {
                    put("protocolVersion", "2024-11-05")
                    put("capabilities", buildJsonObject {})
                    put("clientInfo", buildJsonObject {
                        put("name", "kmp-ai-app")
                        put("version", "1.0.0")
                    })
                })
            }
            
            val initRequestStr = json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(), 
                initRequest
            )
            // Store pretty-printed version for display
            val initRequestPretty = jsonPretty.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(), 
                initRequest
            )
            messages.add(McpJsonMessage("request", initRequestPretty))
            
            stdin.write(initRequestStr)
            stdin.newLine()
            stdin.flush()
            
            // Read initialize response
            val initResponseLine = stdout.readLine() ?: throw McpException("No response from MCP server")
            val initResponseFormatted = try {
                val parsed = json.parseToJsonElement(initResponseLine)
                jsonPretty.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), parsed)
            } catch (e: Exception) {
                initResponseLine // Fallback to original if parsing fails
            }
            messages.add(McpJsonMessage("response", initResponseFormatted))
            val initResponse = json.parseToJsonElement(initResponseLine).jsonObject
            
            // Check for errors in initialize
            if (initResponse.containsKey("error")) {
                val error = initResponse["error"]!!.jsonObject
                throw McpException("MCP initialize error: ${error["message"]?.jsonPrimitive?.content}")
            }
            
            // Send initialized notification
            val initializedNotification = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "notifications/initialized")
            }
            val notificationStr = json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(), 
                initializedNotification
            )
            // Store pretty-printed version for display
            val notificationPretty = jsonPretty.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(), 
                initializedNotification
            )
            messages.add(McpJsonMessage("request", notificationPretty))
            
            stdin.write(notificationStr)
            stdin.newLine()
            stdin.flush()
            
            // Send tools/list request
            val toolsListRequest = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", requestIdCounter.getAndIncrement())
                put("method", "tools/list")
                put("params", buildJsonObject {})
            }
            
            val toolsListRequestStr = json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(), 
                toolsListRequest
            )
            // Store pretty-printed version for display
            val toolsListRequestPretty = jsonPretty.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(), 
                toolsListRequest
            )
            messages.add(McpJsonMessage("request", toolsListRequestPretty))
            
            stdin.write(toolsListRequestStr)
            stdin.newLine()
            stdin.flush()
            
            // Read tools/list response
            val responseLine = stdout.readLine() ?: throw McpException("No response to tools/list request")
            val responseFormatted = try {
                val parsed = json.parseToJsonElement(responseLine)
                jsonPretty.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), parsed)
            } catch (e: Exception) {
                responseLine // Fallback to original if parsing fails
            }
            messages.add(McpJsonMessage("response", responseFormatted))
            val response = json.parseToJsonElement(responseLine).jsonObject
            
            // Check for errors
            if (response.containsKey("error")) {
                val error = response["error"]!!.jsonObject
                throw McpException("MCP tools/list error: ${error["message"]?.jsonPrimitive?.content}")
            }
            
            // Parse result
            val result = response["result"]?.jsonObject
                ?: throw McpException("No result in MCP response")
            
            // MCP protocol: result.tools is an array
            val toolsElement = result["tools"]
                ?: throw McpException("No tools array in MCP response")
            
            // Parse tools list
            val toolsList = json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(models.McpTool.serializer()),
                toolsElement.toString()
            )
            
            return McpToolsResponse(tools = toolsList, messages = messages)
        } catch (e: McpException) {
            throw e
        } catch (e: Exception) {
            throw McpException("Error communicating with MCP server: ${e.message}", e)
        } finally {
            process.destroy()
        }
    }
}
