package routes

import database.DatabaseFactory
import database.tables.*
import models.ChatMessage
import models.ToolCall
import models.Conversation
import models.ConversationWithMessages
import models.ConversationState
import models.ConversationSegment
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ChatService(private val databaseFactory: DatabaseFactory) {
    // In-memory storage for ConversationState (for compression feature)
    private val conversationStates = ConcurrentHashMap<String, ConversationState>()
    fun saveConversation(conversationId: String?, messages: List<ChatMessage>, toolCalls: List<ToolCall>) {
        transaction {
            val convIdFinal = if (conversationId != null) {
                try {
                    val id = conversationId.toInt()
                    Conversations.select { Conversations.id eq id }.firstOrNull()?.get(Conversations.id)?.value 
                        ?: Conversations.insertAndGetId {
                            it[title] = messages.firstOrNull()?.content?.take(50) ?: "New Conversation"
                            it[createdAt] = System.currentTimeMillis()
                        }.value
                } catch (e: Exception) {
                    Conversations.insertAndGetId {
                        it[title] = messages.firstOrNull()?.content?.take(50) ?: "New Conversation"
                        it[createdAt] = System.currentTimeMillis()
                    }.value
                }
            } else {
                Conversations.insertAndGetId {
                    it[title] = messages.firstOrNull()?.content?.take(50) ?: "New Conversation"
                    it[createdAt] = System.currentTimeMillis()
                }.value
            }
            
            // Update title if it's still the default
            val firstMessage = messages.firstOrNull()
            if (firstMessage != null && firstMessage.content.length > 50) {
                Conversations.update({ Conversations.id eq convIdFinal }) {
                    it[title] = firstMessage.content.take(50)
                }
            }
            
            messages.forEach { message ->
                Messages.insert {
                    it[Messages.conversationId] = convIdFinal
                    it[Messages.role] = message.role
                    it[Messages.content] = message.content
                    it[Messages.timestamp] = message.timestamp
                }
            }
            
            toolCalls.forEach { toolCall ->
                ToolCalls.insert {
                    it[ToolCalls.conversationId] = convIdFinal
                    it[ToolCalls.name] = toolCall.name
                    it[ToolCalls.input] = toolCall.input
                    it[ToolCalls.result] = toolCall.result ?: ""
                    it[ToolCalls.timestamp] = toolCall.timestamp
                }
            }
        }
    }
    
    fun listConversations(): List<Conversation> = transaction {
        Conversations.selectAll()
            .orderBy(Conversations.createdAt, SortOrder.DESC)
            .map {
                Conversation(
                    id = it[Conversations.id].value.toString(),
                    title = it[Conversations.title],
                    createdAt = it[Conversations.createdAt]
                )
            }
    }
    
    fun getConversation(id: String): ConversationWithMessages? = transaction {
        try {
            val convId = id.toInt()
            val conv = Conversations.select { Conversations.id eq convId }.firstOrNull() ?: return@transaction null
            
            val messages = Messages.select { Messages.conversationId eq convId }
                .orderBy(Messages.timestamp, SortOrder.ASC)
                .map {
                    ChatMessage(
                        role = it[Messages.role],
                        content = it[Messages.content],
                        timestamp = it[Messages.timestamp]
                    )
                }
            
            val toolCalls = ToolCalls.select { ToolCalls.conversationId eq convId }
                .map {
                    ToolCall(
                        name = it[ToolCalls.name],
                        input = it[ToolCalls.input],
                        result = it[ToolCalls.result].takeIf { r -> r.isNotEmpty() },
                        timestamp = it[ToolCalls.timestamp]
                    )
                }
            
            ConversationWithMessages(
                id = conv[Conversations.id].value.toString(),
                title = conv[Conversations.title],
                createdAt = conv[Conversations.createdAt],
                messages = messages,
                toolCalls = toolCalls
            )
        } catch (e: Exception) {
            null
        }
    }
    
    fun createConversation(): Conversation = transaction {
        val id = Conversations.insertAndGetId {
            it[title] = "New Conversation"
            it[createdAt] = System.currentTimeMillis()
        }
        
        Conversation(
            id = id.value.toString(),
            title = "New Conversation",
            createdAt = System.currentTimeMillis()
        )
    }
    
    fun deleteConversation(id: String): Boolean = transaction {
        try {
            val convId = id.toInt()
            val deleted = Conversations.deleteWhere { Conversations.id eq convId }
            // Also remove from in-memory state
            conversationStates.remove(id)
            deleted > 0
        } catch (e: Exception) {
            false
        }
    }
    
    // Compression-related methods
    fun loadState(conversationId: String?): ConversationState? {
        if (conversationId == null) return null
        return conversationStates[conversationId]
    }
    
    fun saveState(state: ConversationState) {
        conversationStates[state.conversationId] = state
    }
    
    fun getOrCreateState(conversationId: String?): ConversationState {
        if (conversationId == null) {
            // Create a new state with a new conversation ID
            val newConv = createConversation()
            val initialSegment = ConversationSegment(id = UUID.randomUUID().toString())
            return ConversationState(
                conversationId = newConv.id,
                segments = listOf(initialSegment),
                openSegmentId = initialSegment.id
            )
        }
        
        // Try to load existing state
        val existing = conversationStates[conversationId]
        if (existing != null) {
            return existing
        }
        
        // If no state exists, try to reconstruct from messages
        val conv = getConversation(conversationId)
        if (conv != null && conv.messages.isNotEmpty()) {
            // Reconstruct state from existing messages
            val initialSegment = ConversationSegment(
                id = UUID.randomUUID().toString(),
                messages = conv.messages
            )
            val state = ConversationState(
                conversationId = conversationId,
                segments = listOf(initialSegment),
                openSegmentId = initialSegment.id
            )
            conversationStates[conversationId] = state
            return state
        }
        
        // Create new state
        val initialSegment = ConversationSegment(id = UUID.randomUUID().toString())
        val state = ConversationState(
            conversationId = conversationId,
            segments = listOf(initialSegment),
            openSegmentId = initialSegment.id
        )
        conversationStates[conversationId] = state
        return state
    }
}
