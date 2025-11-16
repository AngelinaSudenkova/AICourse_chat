package memory

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import models.MemoryEntry
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class FileMemoryStore(
    private val file: File = File("data/memory.json"),
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true }
) : MemoryStore {

    private val lock = ReentrantLock()
    private var cache: MutableList<MemoryEntry> = mutableListOf()

    init {
        if (!file.parentFile.exists()) file.parentFile.mkdirs()
        if (file.exists()) {
            runCatching {
                val text = file.readText()
                cache = json.decodeFromString(
                    ListSerializer(MemoryEntry.serializer()),
                    text
                ).toMutableList()
            }
        }
    }

    override suspend fun save(entry: MemoryEntry) {
        lock.withLock {
            cache.add(entry)
            flush()
        }
    }

    override suspend fun list(conversationId: String): List<MemoryEntry> {
        return lock.withLock {
            cache.filter { it.conversationId == conversationId }
                .sortedBy { it.createdAt }
        }
    }

    private fun flush() {
        val text = json.encodeToString(
            ListSerializer(MemoryEntry.serializer()),
            cache
        )
        file.writeText(text)
    }
}


