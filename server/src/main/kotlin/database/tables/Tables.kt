package database.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object Users : IntIdTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255).nullable()
    val createdAt = long("created_at")
    val isAdmin = bool("is_admin").default(false)
}

object Conversations : IntIdTable("conversations") {
    val userId = integer("user_id").nullable()
    val title = varchar("title", 255)
    val createdAt = long("created_at")
}

object Messages : IntIdTable("messages") {
    val conversationId = integer("conversation_id")
    val role = varchar("role", 50)
    val content = text("content")
    val timestamp = long("timestamp")
}

object ToolCalls : IntIdTable("tool_calls") {
    val conversationId = integer("conversation_id")
    val name = varchar("name", 100)
    val input = text("input")
    val result = text("result")
    val timestamp = long("timestamp")
}

object Documents : IntIdTable("documents") {
    val title = varchar("title", 255)
    val content = text("content")
    val createdAt = long("created_at")
}
