package transport

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.*
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import models.AgentRequest
import models.AgentResponse
import models.Conversation
import models.ConversationWithMessages
import models.ConversationState
import models.MemoryEntry
import models.McpTool
import models.McpToolsResponse
import models.FinanceEntriesResult
import models.FinanceAnalyzeRequest
import models.FinanceAnalyzeResponse
import models.NewsSummaryResponse
import models.Reminder
import models.ReminderAddRequest
import models.ReminderListResponse
import models.ReminderSummary
import models.ResearchRequest
import models.ResearchResponse
import models.ResearchLogResponse
import structured.ReadingSummary
import structured.JournalResponse
import structured.ReasonRequest
import structured.ReasonResponse
import structured.TempRequest
import structured.TempResponse
import structured.TempRun
import structured.CompareRequest
import structured.CompareSummary
import structured.ModelsCompareRequest
import structured.ModelsCompareResponse

class HttpTransport(
    private val baseUrl: String
) : Transport {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
        // Ktor 3: configure base URL via DefaultRequest plugin
        install(DefaultRequest) {
            url { takeFrom(baseUrl) }
            contentType(ContentType.Application.Json)
        }
    }

    override suspend fun send(request: AgentRequest): AgentResponse {
        // With DefaultRequest above, this hits: {baseUrl}/api/agent
        return client.post("/api/agent") {
            setBody(request)
        }.body()
    }

    override fun sendStream(request: AgentRequest): Flow<AgentResponse> = flow {
        val response = send(request)
        emit(response)
    }
    
    suspend fun listConversations(): List<Conversation> {
        return client.get("/api/conversations").body()
    }
    
    suspend fun getConversation(id: String): ConversationWithMessages {
        return client.get("/api/conversations/$id").body()
    }
    
    suspend fun createConversation(): Conversation {
        return client.post("/api/conversations").body()
    }
    
    suspend fun deleteConversation(id: String): Boolean {
        val response = client.delete("/api/conversations/$id")
        return response.status.value == 200
    }
    
    suspend fun summarize(text: String): ReadingSummary {
        return client.post("/api/summarize") {
            contentType(ContentType.Text.Plain)
            setBody(text)
        }.body()
    }
    
    suspend fun journal(message: String, conversationHistory: List<String> = emptyList()): JournalResponse {
        return client.post("/api/journal") {
            setBody(JournalRequest(message, conversationHistory))
        }.body()
    }
    
    suspend fun reason(method: String, challenge: String): ReasonResponse {
        return client.post("/api/reason") {
            setBody(ReasonRequest(method, challenge))
        }.body()
    }
    
    suspend fun temperature(prompt: String, temps: List<Double> = listOf(0.0, 0.7, 1.2)): TempResponse {
        return client.post("/api/temperature") {
            setBody(TempRequest(prompt = prompt, temps = temps))
        }.body()
    }
    
    suspend fun compareTemperature(prompt: String, runs: List<TempRun>): CompareSummary {
        return client.post("/api/temperature/compare") {
            setBody(CompareRequest(prompt = prompt, runs = runs))
        }.body()
    }
    
    suspend fun compareModels(request: ModelsCompareRequest): ModelsCompareResponse {
        return try {
            client.post("/api/models/compare") {
                setBody(request)
            }.body()
        } catch (e: ClientRequestException) {
            val message = parseErrorMessage(e.response.bodyAsText())
            throw Exception(message)
        } catch (e: ServerResponseException) {
            val message = parseErrorMessage(e.response.bodyAsText())
            throw Exception(message)
        }
    }
    
    suspend fun forceSummarize(request: AgentRequest): ConversationState {
        return client.post("/api/agent/force-summarize") {
            setBody(request)
        }.body()
    }

    suspend fun listMemories(conversationId: String): List<MemoryEntry> {
        return client.get("/api/memory/$conversationId").body()
    }
    
    suspend fun listMcpTools(): McpToolsResponse {
        val response = client.post("/api/mcp/tools")
        return try {
            response.body<McpToolsResponse>()
        } catch (e: Exception) {
            // If deserialization fails, return error response
            McpToolsResponse(
                tools = emptyList(),
                messages = listOf(
                    models.McpJsonMessage(
                        direction = "error",
                        content = "{\"error\": \"Failed to parse response: ${e.message}\"}"
                    )
                )
            )
        }
    }
    
    suspend fun getFinanceSnapshot(): FinanceEntriesResult {
        return client.get("/api/notion/finance/snapshot").body()
    }
    
    suspend fun analyzeFinance(request: FinanceAnalyzeRequest): FinanceAnalyzeResponse {
        return client.post("/api/notion/finance/analyze") {
            setBody(request)
        }.body()
    }
    
    suspend fun getLatestNews(): NewsSummaryResponse {
        return client.get("/api/news/latest").body()
    }
    
    suspend fun refreshNews(): NewsSummaryResponse {
        return client.post("/api/news/refresh").body()
    }
    
    suspend fun searchNews(query: String): NewsSummaryResponse {
        return client.get("/api/news/search") {
            parameter("q", query)
        }.body()
    }
    
    suspend fun addReminder(request: ReminderAddRequest): Reminder {
        return client.post("/api/reminder/add") {
            setBody(request)
        }.body()
    }
    
    suspend fun listReminders(onlyPending: Boolean = false): ReminderListResponse {
        return client.get("/api/reminder/list") {
            parameter("onlyPending", onlyPending.toString())
        }.body()
    }
    
    suspend fun getReminderSummary(): ReminderSummary {
        return client.get("/api/reminder/summary").body()
    }
    
    suspend fun research(query: String): ResearchResponse {
        return client.post("/api/research") {
            setBody(ResearchRequest(query = query))
        }.body()
    }
    
    suspend fun getResearchLog(): ResearchLogResponse {
        return client.get("/api/research/log").body()
    }
    
    suspend fun getResearchFile(filename: String): Map<String, String> {
        return client.get("/api/research/file/$filename").body()
    }
    
    @kotlinx.serialization.Serializable
    private data class JournalRequest(
        val message: String,
        val conversationHistory: List<String> = emptyList()
    )
}

private fun parseErrorMessage(raw: String): String {
    return try {
        val element = Json.parseToJsonElement(raw)
        if (element is kotlinx.serialization.json.JsonObject && element["error"] != null) {
            element["error"]!!.jsonPrimitive.content
        } else {
            raw
        }
    } catch (e: Exception) {
        raw
    }
}
