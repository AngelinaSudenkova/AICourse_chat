package routes

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*
import models.*
import server.module

class ChatRoutesTest {
    @Test
    fun testChatRouteWithCalculator() = testApplication {
        application {
            module()
        }
        
        val response = client.post("/api/ai/chat") {
            contentType(ContentType.Application.Json)
            setBody(AgentRequest(
                messages = listOf(ChatMessage("user", "/calc 2+2"))
            ))
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("calculator") || body.contains("4"))
    }
    
    @Test
    fun testHealthEndpoint() = testApplication {
        application {
            module()
        }
        
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("OK") || response.bodyAsText().contains("ok"))
    }
}

