package transport

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import models.AgentRequest
import models.AgentResponse
import models.Conversation
import models.ConversationWithMessages
import structured.ReadingSummary
import structured.JournalResponse

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
    
    @kotlinx.serialization.Serializable
    private data class JournalRequest(
        val message: String,
        val conversationHistory: List<String> = emptyList()
    )
}
