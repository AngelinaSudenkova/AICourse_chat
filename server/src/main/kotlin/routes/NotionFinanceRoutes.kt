package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mcp.NotionMcpClient
import mcp.McpException
import models.FinanceAnalyzeRequest
import models.FinanceAnalyzeResponse
import models.FinanceEntry
import models.FinanceEntriesResult
import ai.GeminiClient
import org.koin.core.context.GlobalContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun Route.notionFinanceRoutes() {
    val koin = GlobalContext.get()
    val notionClient: NotionMcpClient = koin.get()
    
    /**
     * GET /api/notion/finance/snapshot
     * Returns raw finance entries (for the UI table).
     */
    get("/notion/finance/snapshot") {
        try {
            // Get entries from last 30 days by default
            val toDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
            val fromDate = LocalDate.now().minusDays(30).format(DateTimeFormatter.ISO_DATE)
            
            val result = notionClient.financeGetEntries(
                fromDate = fromDate,
                toDate = toDate,
                limit = 100
            )
            
            call.respond(result)
        } catch (e: McpException) {
            println("Notion Finance Snapshot Error (MCP): ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                FinanceEntriesResult(
                    entries = emptyList(),
                    totalCount = 0,
                    databaseId = ""
                )
            )
        } catch (e: Exception) {
            println("Notion Finance Snapshot Error: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                FinanceEntriesResult(
                    entries = emptyList(),
                    totalCount = 0,
                    databaseId = ""
                )
            )
        }
    }
    
    /**
     * POST /api/notion/finance/analyze
     * Accepts an optional user question, calls MCP to get entries,
     * builds an AI prompt + calls Gemini, and returns the AI answer.
     */
    post("/notion/finance/analyze") {
        try {
            val geminiClient: GeminiClient = koin.get()
            val request = call.receive<FinanceAnalyzeRequest>()
            
            // Get finance entries
            val result = notionClient.financeGetEntries(
                fromDate = request.fromDate,
                toDate = request.toDate,
                limit = 50
            )
            
            // Build prompt for Gemini
            val question = request.question?.takeIf { it.isNotBlank() }
                ?: "Give me a short spending summary and highlight any unusual or large expenses."
            
            val prompt = buildString {
                appendLine("You are a financial assistant. You receive a list of expenses from Notion.")
                appendLine()
                appendLine("Each expense has: date, title, amount (PLN).")
                appendLine()
                appendLine("Data:")
                
                if (result.entries.isEmpty()) {
                    appendLine("- No expenses found in the specified date range.")
                } else {
                    result.entries.forEach { entry ->
                        appendLine("- ${entry.date} | ${entry.title} | ${entry.amount}")
                    }
                }
                
                appendLine()
                appendLine("User question:")
                appendLine("\"$question\"")
            }
            
            // Call Gemini
            val aiAnswer = geminiClient.generate(prompt)
            
            // Return response
            call.respond(
                FinanceAnalyzeResponse(
                    entries = result.entries,
                    aiAnswer = aiAnswer
                )
            )
        } catch (e: McpException) {
            println("Notion Finance Analyze Error (MCP): ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                FinanceAnalyzeResponse(
                    entries = emptyList(),
                    aiAnswer = "Error: Failed to fetch finance data: ${e.message}"
                )
            )
        } catch (e: Exception) {
            println("Notion Finance Analyze Error: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                FinanceAnalyzeResponse(
                    entries = emptyList(),
                    aiAnswer = "Error: ${e.message ?: "Unknown error occurred"}"
                )
            )
        }
    }
}

