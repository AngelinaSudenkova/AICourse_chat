package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mcp.ResearchMcpClient
import mcp.McpException
import models.ResearchRequest
import models.ResearchResponse
import models.ResearchLogEntry
import models.ResearchLogResponse
import research.ResearchPipeline
import org.koin.core.context.GlobalContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

fun Route.researchRoutes() {
    val koin = GlobalContext.get()
    val researchClient: ResearchMcpClient = koin.get()
    val researchPipeline: ResearchPipeline = koin.get()
    
    /**
     * POST /api/research
     * Runs the research pipeline: search → summarize → save
     */
    post("/research") {
        try {
            val request = call.receive<ResearchRequest>()
            
            if (request.query.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Query parameter is required")
                )
                return@post
            }
            
            val result = researchPipeline.runResearchPipeline(request.query)
            
            call.respond(
                ResearchResponse(
                    query = result.query,
                    summary = result.summary,
                    savedPath = result.savedPath
                )
            )
        } catch (e: McpException) {
            println("Research Error (MCP): ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Research failed: ${e.message}")
            )
        } catch (e: Exception) {
            println("Research Error: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Unknown error occurred"))
            )
        }
    }
    
    /**
     * GET /api/research/log
     * Lists all saved research files
     */
    get("/research/log") {
        try {
            val researchDirPath = System.getenv("RESEARCH_DIR") ?: "server/data/research"
            val researchDir = if (File(researchDirPath).isAbsolute) {
                File(researchDirPath)
            } else {
                // Resolve relative to project root
                val projectRoot = File(System.getProperty("user.dir"))
                val serverDir = if (projectRoot.name == "server") {
                    projectRoot.parentFile
                } else {
                    projectRoot
                }
                val resolvedDir = File(serverDir, researchDirPath)
                println("Research log: Looking for files in ${resolvedDir.absolutePath}")
                println("Research log: Directory exists: ${resolvedDir.exists()}, isDirectory: ${resolvedDir.isDirectory}")
                resolvedDir
            }
            
            if (!researchDir.exists() || !researchDir.isDirectory) {
                println("Research log: Directory not found at ${researchDir.absolutePath}")
                call.respond(ResearchLogResponse(entries = emptyList()))
                return@get
            }
            
            val files = researchDir.listFiles()
            println("Research log: Found ${files?.size ?: 0} files in directory")
            
            val entries = researchDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".md") }
                ?.mapNotNull { file ->
                    try {
                        val content = file.readText()
                        // Extract title from first line (should be # Title)
                        val title = content.lines().firstOrNull { it.startsWith("# ") }
                            ?.removePrefix("# ")
                            ?.trim()
                            ?: file.nameWithoutExtension
                        
                        // Extract query from content if available
                        val queryMatch = Regex("\\*\\*Research Query:\\*\\* (.+)").find(content)
                        val query = queryMatch?.groupValues?.get(1) ?: ""
                        
                        ResearchLogEntry(
                            filename = file.name,
                            path = file.absolutePath,
                            title = title,
                            createdAt = file.lastModified(),
                            query = query
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
            
            call.respond(ResearchLogResponse(entries = entries))
        } catch (e: Exception) {
            println("Research Log Error: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ResearchLogResponse(entries = emptyList())
            )
        }
    }
    
    /**
     * GET /api/research/file/{filename}
     * Gets the content of a specific research file
     */
    get("/research/file/{filename}") {
        try {
            val filename = call.parameters["filename"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Filename parameter is required")
            )
            
            val researchDirPath = System.getenv("RESEARCH_DIR") ?: "server/data/research"
            val researchDir = if (File(researchDirPath).isAbsolute) {
                File(researchDirPath)
            } else {
                // Resolve relative to project root
                val projectRoot = File(System.getProperty("user.dir"))
                val serverDir = if (projectRoot.name == "server") {
                    projectRoot.parentFile
                } else {
                    projectRoot
                }
                File(serverDir, researchDirPath)
            }
            val file = File(researchDir, filename)
            
            if (!file.exists() || !file.isFile) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "File not found")
                )
                return@get
            }
            
            // Security check: ensure file is within research directory
            if (!file.canonicalPath.startsWith(researchDir.canonicalPath)) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Access denied")
                )
                return@get
            }
            
            val content = file.readText()
            call.respond(mapOf("content" to content))
        } catch (e: Exception) {
            println("Research File Error: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Unknown error occurred"))
            )
        }
    }
}

