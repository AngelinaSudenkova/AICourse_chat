package memory

import models.MemoryEntry

interface MemoryStore {
    suspend fun save(entry: MemoryEntry)
    suspend fun list(conversationId: String): List<MemoryEntry>
}


