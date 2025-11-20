package models

import kotlinx.serialization.Serializable

@Serializable
data class Reminder(
    val id: String,
    val text: String,
    val createdAt: Long,
    val dueDate: Long? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null
)

@Serializable
data class ReminderSummary(
    val pendingCount: Int,
    val overdueCount: Int,
    val reminders: List<Reminder>,
    val aiSummary: String? = null,
    val generatedAt: Long
)

@Serializable
data class ReminderAddRequest(
    val text: String,
    val dueDate: Long? = null
)

@Serializable
data class ReminderListResponse(
    val reminders: List<Reminder>,
    val totalCount: Int
)

