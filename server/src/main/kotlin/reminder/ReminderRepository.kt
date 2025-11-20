package reminder

import models.Reminder
import models.ReminderSummary
import java.io.File
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import platform.currentTimeMillis

class ReminderRepository(
    private val storageFile: File = File("server/data/reminders.json")
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    
    init {
        // Ensure data directory exists
        storageFile.parentFile?.mkdirs()
        // Initialize empty file if it doesn't exist
        if (!storageFile.exists()) {
            saveReminders(emptyList())
        }
    }
    
    /**
     * Adds a new reminder.
     */
    fun add(reminder: Reminder): Reminder {
        val reminders = loadReminders().toMutableList()
        reminders.add(reminder)
        saveReminders(reminders)
        return reminder
    }
    
    /**
     * Lists all reminders.
     */
    fun listAll(): List<Reminder> {
        return loadReminders()
    }
    
    /**
     * Lists pending (not completed) reminders.
     */
    fun listPending(now: Long = currentTimeMillis()): List<Reminder> {
        return loadReminders().filter { !it.completed }
    }
    
    /**
     * Lists overdue reminders (pending with dueDate < now).
     */
    fun listOverdue(now: Long = currentTimeMillis()): List<Reminder> {
        return listPending(now).filter { reminder ->
            val dueDate = reminder.dueDate
            dueDate != null && dueDate < now
        }
    }
    
    /**
     * Generates a summary of reminders.
     */
    fun summary(now: Long = currentTimeMillis()): ReminderSummary {
        val pending = listPending(now)
        val overdue = listOverdue(now)
        
        return ReminderSummary(
            pendingCount = pending.size,
            overdueCount = overdue.size,
            reminders = pending,
            aiSummary = null,
            generatedAt = now
        )
    }
    
    /**
     * Marks a reminder as completed.
     */
    fun complete(id: String, now: Long = currentTimeMillis()): Reminder? {
        val reminders = loadReminders().toMutableList()
        val index = reminders.indexOfFirst { it.id == id }
        if (index >= 0) {
            val reminder = reminders[index]
            val updated = reminder.copy(completed = true, completedAt = now)
            reminders[index] = updated
            saveReminders(reminders)
            return updated
        }
        return null
    }
    
    /**
     * Deletes a reminder.
     */
    fun delete(id: String): Boolean {
        val reminders = loadReminders().toMutableList()
        val removed = reminders.removeAll { it.id == id }
        if (removed) {
            saveReminders(reminders)
        }
        return removed
    }
    
    private fun loadReminders(): List<Reminder> {
        return try {
            if (storageFile.exists()) {
                val content = storageFile.readText()
                if (content.isBlank()) {
                    emptyList()
                } else {
                    json.decodeFromString<List<Reminder>>(content)
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            println("Failed to load reminders: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    private fun saveReminders(reminders: List<Reminder>) {
        try {
            storageFile.writeText(json.encodeToString(reminders))
        } catch (e: Exception) {
            println("Failed to save reminders: ${e.message}")
            e.printStackTrace()
        }
    }
}

