package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mcp.McpClient
import mcp.McpException
import models.McpToolsResponse
import java.io.File

fun Route.mcpRoutes() {
    post("/mcp/tools") {
        try {
            // Get MCP server path from environment variable, default to built-in
            val mcpServerPath = System.getenv("MCP_SERVER_PATH") ?: "builtin"
            
            val command: String
            val args: List<String>
            
            when {
                mcpServerPath == "builtin" -> {
                    // Use built-in MCP server
                    // Find the Java executable
                    val javaHome = System.getProperty("java.home")
                    val javaExe = if (File("$javaHome/bin/java").exists()) {
                        "$javaHome/bin/java"
                    } else {
                        "java" // Fallback to PATH
                    }
                    
                    // Find the current JAR or classpath
                    val classPath = System.getProperty("java.class.path")
                    val mainClass = "mcp.McpServerKt"
                    
                    command = javaExe
                    args = listOf("-cp", classPath, mainClass)
                }
                
                mcpServerPath.startsWith("npx ") -> {
                    // Handle npx commands: "npx @modelcontextprotocol/server-stdio-hello"
                    val parts = mcpServerPath.split(" ").filter { it.isNotBlank() }
                    command = "npx"
                    args = parts.drop(1)
                }
                
                else -> {
                    // Regular binary path
                    command = mcpServerPath
                    args = emptyList()
                }
            }
            
            // Create MCP client and list tools
            val client = McpClient(command, args)
            val response = client.listTools()
            
            call.respond(response)
        } catch (e: McpException) {
            // Return a proper McpToolsResponse with error information
            val errorResponse = McpToolsResponse(
                tools = emptyList(),
                messages = listOf(
                    models.McpJsonMessage(
                        direction = "error",
                        content = "{\"error\": \"MCP error: ${e.message}\"}"
                    )
                )
            )
            call.respond(HttpStatusCode.InternalServerError, errorResponse)
        } catch (e: Exception) {
            // Return a proper McpToolsResponse with error information
            val errorResponse = McpToolsResponse(
                tools = emptyList(),
                messages = listOf(
                    models.McpJsonMessage(
                        direction = "error",
                        content = "{\"error\": \"Failed to list MCP tools: ${e.message}\"}"
                    )
                )
            )
            call.respond(HttpStatusCode.InternalServerError, errorResponse)
        }
    }
}

