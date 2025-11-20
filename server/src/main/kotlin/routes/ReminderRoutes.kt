package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mcp.ReminderMcpClient
import mcp.McpException
import models.ReminderAddRequest
import models.ReminderListResponse
import models.ReminderSummary
import reminder.ReminderScheduler
import org.koin.core.context.GlobalContext

fun Route.reminderRoutes() {
    val koin = GlobalContext.get()
    val reminderClient: ReminderMcpClient = koin.get()
    val reminderScheduler: ReminderScheduler = koin.get()
    
    /**
     * POST /api/reminder/add
     * Adds a new reminder.
     */
    post("/reminder/add") {
        try {
            val request = call.receive<ReminderAddRequest>()
            val reminder = reminderClient.addReminder(request.text, request.dueDate)
            call.respond(reminder)
        } catch (e: McpException) {
            println("Reminder Add Error (MCP): ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf<String, String>("error" to "Failed to add reminder: ${e.message}")
            )
        } catch (e: Exception) {
            println("Reminder Add Error: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf<String, String>("error" to (e.message ?: "Unknown error occurred"))
            )
        }
    }
    
    /**
     * GET /api/reminder/list?onlyPending=true
     * Lists reminders, optionally filtered to pending only.
     */
    get("/reminder/list") {
        try {
            val onlyPending = call.parameters["onlyPending"]?.toBoolean() ?: false
            val response = reminderClient.listReminders(onlyPending)
            call.respond(response)
        } catch (e: McpException) {
            println("Reminder List Error (MCP): ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ReminderListResponse(reminders = emptyList(), totalCount = 0)
            )
        } catch (e: Exception) {
            println("Reminder List Error: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ReminderListResponse(reminders = emptyList(), totalCount = 0)
            )
        }
    }
    
    /**
     * GET /api/reminder/summary
     * Gets the current reminder summary (with AI-generated text if available).
     */
    get("/reminder/summary") {
        try {
            val summary = reminderScheduler.generateSummary()
            call.respond(summary)
        } catch (e: Exception) {
            println("Reminder Summary Error: ${e.message}")
            e.printStackTrace()
            // Try to return latest stored summary
            val latest = reminderScheduler.loadLatestSummary()
            if (latest != null) {
                call.respond(latest)
            } else {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ReminderSummary(
                        pendingCount = 0,
                        overdueCount = 0,
                        reminders = emptyList(),
                        aiSummary = "Error: ${e.message}",
                        generatedAt = platform.currentTimeMillis()
                    )
                )
            }
        }
    }
}

