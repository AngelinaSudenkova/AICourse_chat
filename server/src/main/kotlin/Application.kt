package server

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import routes.*
import database.DatabaseFactory
import ai.GeminiClient
import tools.ToolRegistry
import chat.CompressionService
import models.*
import platform.currentTimeMillis
import org.koin.core.context.GlobalContext
import mcp.NotionMcpClient
import mcp.NewsMcpClient
import mcp.ReminderMcpClient
import news.NewsScheduler
import reminder.ReminderRepository
import reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

fun main(args: Array<String>) {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8081
    embeddedServer(CIO, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val appModule = module {
        val dbFactory = DatabaseFactory()
        dbFactory.init()
        single { dbFactory }
        single { GeminiClient(System.getenv("GEMINI_API_KEY") ?: "") }
        single { ToolRegistry(get()) }
        single { ChatService(get()) }
        single { CompressionService(get(), segmentWindowSize = 5) }
        single<memory.MemoryStore> { memory.FileMemoryStore() }
        
        // Notion MCP Client
        val mcpCmd = System.getenv("MCP_NOTION_CMD") ?: "node"
        val mcpArgsStr = System.getenv("MCP_NOTION_ARGS") ?: "mcp/notion-finance-server/dist/index.js"
        val mcpArgs = mcpArgsStr.split(" ").filter { it.isNotBlank() }
        single { NotionMcpClient(mcpCmd, mcpArgs) }
        
        // News MCP Client
        val newsMcpCmd = System.getenv("MCP_NEWS_CMD") ?: "node"
        val newsMcpArgsStr = System.getenv("MCP_NEWS_ARGS") ?: "mcp/news-server/dist/index.js"
        val newsMcpArgs = newsMcpArgsStr.split(" ").filter { it.isNotBlank() }
        single { NewsMcpClient(newsMcpCmd, newsMcpArgs) }
        
        // News Scheduler
        single { NewsScheduler(get(), get()) }
        
        // Reminder Repository
        single { ReminderRepository() }
        
        // Reminder MCP Client
        val reminderMcpCmd = System.getenv("MCP_REMINDER_CMD") ?: "node"
        val reminderMcpArgsStr = System.getenv("MCP_REMINDER_ARGS") ?: "mcp/reminder-server/dist/index.js"
        val reminderMcpArgs = reminderMcpArgsStr.split(" ").filter { it.isNotBlank() }
        single { ReminderMcpClient(reminderMcpCmd, reminderMcpArgs) }
        
        // Reminder Scheduler
        single { ReminderScheduler(get(), get()) }
    }
    
    install(Koin) {
        modules(appModule)
    }
    
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = true
        })
    }
    
    install(CORS) {
        allowNonSimpleContentTypes = true
        allowMethod(io.ktor.http.HttpMethod.Options)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowMethod(io.ktor.http.HttpMethod.Put)
        allowMethod(io.ktor.http.HttpMethod.Patch)
        allowMethod(io.ktor.http.HttpMethod.Delete)
        allowHeaders { true }
        allowCredentials = true
        allowHost("localhost:8080") // web dev server
        allowHost("127.0.0.1:8080")
        allowHost("localhost:8083") // allow alternative port
        allowHost("127.0.0.1:8083")
    }
    
    install(Compression) {
        gzip()
        deflate()
    }
    
    install(CallLogging) {
        level = org.slf4j.event.Level.INFO
    }
    
    install(StatusPages) {
        // Handle 404 explicitly with proper AgentResponse format
        status(io.ktor.http.HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                AgentResponse(
                    message = ChatMessage(
                        role = "assistant",
                        content = "Not found",
                        timestamp = currentTimeMillis()
                    ),
                    toolCalls = emptyList()
                )
            )
        }
        
        exception<Throwable> { call, cause ->
            // Always return AgentResponse format for API routes to match client expectations
            call.respond(
                io.ktor.http.HttpStatusCode.InternalServerError,
                AgentResponse(
                    message = ChatMessage(
                        role = "assistant",
                        content = "Error: ${cause.message ?: cause::class.qualifiedName ?: "Unknown error"}",
                        timestamp = currentTimeMillis()
                    ),
                    toolCalls = emptyList()
                )
            )
        }
    }
    
    routing {
        route("/api") {
            val gemini: GeminiClient = GlobalContext.get().get()
            temperatureRoutes(gemini)
            temperatureCompareRoutes(gemini)
            hfModelsCompareRoutes()
            summaryRoutes()
            journalRoutes()
            reasoningRoutes() // Added reasoning lab route
            conversationRoutes()
            chatRoutes()
            agentRoutes()
            memoryRoutes()
            mcpRoutes()
            notionFinanceRoutes()
            newsRoutes()
            reminderRoutes()
        }
        get("/health") {
            call.respondText("OK")
        }
    }
    
    // Start schedulers after Koin is ready
    val applicationScope = CoroutineScope(SupervisorJob())
    applicationScope.launch {
        try {
            val newsScheduler: NewsScheduler = GlobalContext.get().get()
            newsScheduler.start(applicationScope)
        } catch (e: Exception) {
            println("Failed to start news scheduler: ${e.message}")
            e.printStackTrace()
        }
    }
    applicationScope.launch {
        try {
            val reminderScheduler: ReminderScheduler = GlobalContext.get().get()
            reminderScheduler.start(applicationScope)
        } catch (e: Exception) {
            println("Failed to start reminder scheduler: ${e.message}")
            e.printStackTrace()
        }
    }
}

