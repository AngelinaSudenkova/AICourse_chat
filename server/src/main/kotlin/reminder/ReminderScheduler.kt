package reminder

import ai.GeminiClient
import models.ReminderSummary
import kotlinx.coroutines.*
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.currentTimeMillis

class ReminderScheduler(
    private val reminderRepository: ReminderRepository,
    private val geminiClient: GeminiClient,
    private val storageFile: File = File("server/data/reminder-summary.json")
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    
    init {
        storageFile.parentFile?.mkdirs()
    }
    
    /**
     * Builds a prompt for Gemini to summarize reminders.
     */
    private fun buildReminderSummaryPrompt(summary: ReminderSummary): String {
        return buildString {
            appendLine("You are a personal assistant. Summarize the following reminders:")
            appendLine()
            appendLine("Pending reminders: ${summary.pendingCount}")
            appendLine("Overdue reminders: ${summary.overdueCount}")
            appendLine()
            
            if (summary.reminders.isEmpty()) {
                appendLine("No pending reminders.")
            } else {
                appendLine("Reminders:")
                summary.reminders.forEachIndexed { index, reminder ->
                    appendLine("${index + 1}. ${reminder.text}")
                    val dueDate = reminder.dueDate
                    if (dueDate != null) {
                        val dueDateStr = formatDate(dueDate)
                        val isOverdue = dueDate < currentTimeMillis()
                        appendLine("   Due: $dueDateStr${if (isOverdue) " (OVERDUE)" else ""}")
                    }
                }
            }
            appendLine()
            appendLine("Provide a brief, actionable summary (2-3 sentences) highlighting:")
            appendLine("- Most urgent/overdue items")
            appendLine("- Key priorities")
            appendLine("- Any patterns or themes")
        }
    }
    
    private fun formatDate(timestamp: Long): String {
        return try {
            val date = java.util.Date(timestamp)
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(date)
        } catch (e: Exception) {
            timestamp.toString()
        }
    }
    
    /**
     * Generates reminder summary with AI.
     */
    suspend fun generateSummary(): ReminderSummary {
        try {
            val summary = reminderRepository.summary()
            
            // Generate AI summary
            val prompt = buildReminderSummaryPrompt(summary)
            val aiSummary = try {
                geminiClient.generate(prompt)
            } catch (e: Exception) {
                println("Failed to generate AI summary for reminders: ${e.message}")
                null
            }
            
            val summaryWithAI = summary.copy(aiSummary = aiSummary)
            
            // Store summary
            saveSummary(summaryWithAI)
            
            return summaryWithAI
        } catch (e: Exception) {
            println("Error in generateSummary: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    private fun saveSummary(summary: ReminderSummary) {
        try {
            storageFile.writeText(json.encodeToString(ReminderSummary.serializer(), summary))
        } catch (e: Exception) {
            println("Failed to save reminder summary: ${e.message}")
            e.printStackTrace()
        }
    }
    
    fun loadLatestSummary(): ReminderSummary? {
        return try {
            if (storageFile.exists()) {
                val content = storageFile.readText()
                json.decodeFromString(ReminderSummary.serializer(), content)
            } else {
                null
            }
        } catch (e: Exception) {
            println("Failed to load reminder summary: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Starts the scheduler to generate reminder summary periodically.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            // Generate immediately on startup
            try {
                println("ReminderScheduler: Generating initial reminder summary...")
                generateSummary()
                println("ReminderScheduler: Initial reminder summary generated successfully")
            } catch (e: Exception) {
                println("ReminderScheduler: Failed to generate initial reminder summary: ${e.message}")
                e.printStackTrace()
            }
            
            // Then generate every 6 hours
            while (true) {
                delay(6 * 60 * 60 * 1000) // 6 hours
                try {
                    println("ReminderScheduler: Generating reminder summary...")
                    generateSummary()
                    println("ReminderScheduler: Reminder summary generated successfully")
                } catch (e: Exception) {
                    println("ReminderScheduler: Failed to generate reminder summary: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
}

