package transport

import kotlinx.coroutines.flow.Flow
import models.AgentRequest
import models.AgentResponse

interface Transport {
    suspend fun send(request: AgentRequest): AgentResponse
    fun sendStream(request: AgentRequest): Flow<AgentResponse>
}

