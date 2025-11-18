package mcp

import kotlinx.serialization.json.*
import models.McpTool
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Simple MCP server implementation that handles JSON-RPC 2.0 requests over stdio.
 * 
 * This server implements the MCP protocol and exposes example tools.
 */
class McpServer {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    // Define available tools
    private val tools = listOf(
        McpTool(
            name = "hello",
            description = "Says hello with a greeting message"
        ),
        McpTool(
            name = "echo",
            description = "Echoes back the input text"
        ),
        McpTool(
            name = "calculate",
            description = "Performs basic arithmetic calculations"
        ),
        McpTool(
            name = "timestamp",
            description = "Returns the current timestamp"
        )
    )
    
    /**
     * Runs the MCP server, reading from stdin and writing to stdout.
     */
    fun run() {
        val stdin = BufferedReader(InputStreamReader(System.`in`))
        val stdout = BufferedWriter(OutputStreamWriter(System.out))
        
        var initialized = false
        
        try {
            while (true) {
                val line = stdin.readLine() ?: break
                if (line.isBlank()) continue
                
                try {
                    val request = json.parseToJsonElement(line).jsonObject
                    val method = request["method"]?.jsonPrimitive?.content
                    val id = request["id"] ?: JsonNull
                    
                    when (method) {
                        "initialize" -> {
                            val response = buildJsonObject {
                                put("jsonrpc", "2.0")
                                if (id != JsonNull) put("id", id)
                                put("result", buildJsonObject {
                                    put("protocolVersion", "2024-11-05")
                                    put("capabilities", buildJsonObject {
                                        put("tools", buildJsonObject {})
                                    })
                                    put("serverInfo", buildJsonObject {
                                        put("name", "kmp-ai-mcp-server")
                                        put("version", "1.0.0")
                                    })
                                })
                            }
                            stdout.write(response.toString())
                            stdout.newLine()
                            stdout.flush()
                            initialized = true
                        }
                        
                        "notifications/initialized" -> {
                            // Acknowledge but don't send response (notification)
                            // Server is ready
                        }
                        
                        "tools/list" -> {
                            if (!initialized) {
                                val errorResponse = buildJsonObject {
                                    put("jsonrpc", "2.0")
                                    if (id != JsonNull) put("id", id)
                                    put("error", buildJsonObject {
                                        put("code", -32002)
                                        put("message", "Server not initialized")
                                    })
                                }
                                stdout.write(errorResponse.toString())
                                stdout.newLine()
                                stdout.flush()
                            } else {
                                val response = buildJsonObject {
                                    put("jsonrpc", "2.0")
                                    if (id != JsonNull) put("id", id)
                                    put("result", buildJsonObject {
                                        put("tools", JsonArray(tools.map { tool ->
                                            buildJsonObject {
                                                put("name", tool.name)
                                                tool.description?.let { put("description", it) }
                                            }
                                        }))
                                    })
                                }
                                stdout.write(response.toString())
                                stdout.newLine()
                                stdout.flush()
                            }
                        }
                        
                        else -> {
                            val errorResponse = buildJsonObject {
                                put("jsonrpc", "2.0")
                                if (id != JsonNull) put("id", id)
                                put("error", buildJsonObject {
                                    put("code", -32601)
                                    put("message", "Method not found: $method")
                                })
                            }
                            stdout.write(errorResponse.toString())
                            stdout.newLine()
                            stdout.flush()
                        }
                    }
                } catch (e: Exception) {
                    // Send error response for malformed requests
                    val errorResponse = buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", JsonNull)
                        put("error", buildJsonObject {
                            put("code", -32700)
                            put("message", "Parse error: ${e.message}")
                        })
                    }
                    stdout.write(errorResponse.toString())
                    stdout.newLine()
                    stdout.flush()
                }
            }
        } catch (e: Exception) {
            // Server error - write to stderr
            System.err.println("MCP Server error: ${e.message}")
            e.printStackTrace()
        }
    }
}

/**
 * Main entry point for running the MCP server as a standalone process.
 * 
 * Usage: java -cp <classpath> mcp.McpServerKt
 */
fun main(args: Array<String>) {
    val server = McpServer()
    server.run()
}

