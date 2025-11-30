import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import models.*
import transport.HttpTransport

class RagChatViewModel(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val httpTransport: HttpTransport
) {
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
    var sourcesByMessage by mutableStateOf<Map<String, List<LabeledSource>>>(emptyMap())
    var isLoading by mutableStateOf(false)
    var currentConversationId: String? by mutableStateOf(null)
    var conversations by mutableStateOf<List<Conversation>>(emptyList())
    var isLoadingConversations by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    
    // RAG parameters
    var topK by mutableStateOf(5)
    var enableFilter by mutableStateOf(true)
    var minSimilarity by mutableStateOf(0.3)
    var allowModelFallback by mutableStateOf(true)
    var minBestScoreForRag by mutableStateOf(0.25)
    var autoFetchWiki by mutableStateOf(false)
    
    fun loadConversations() {
        if (isLoadingConversations) return
        isLoadingConversations = true
        scope.launch {
            try {
                conversations = httpTransport.listConversations()
                if (conversations.isEmpty()) {
                    createNewChat()
                } else if (currentConversationId == null) {
                    loadConversation(conversations.first().id)
                }
            } catch (e: Exception) {
                error = "Error loading conversations: ${e.message}"
            } finally {
                isLoadingConversations = false
            }
        }
    }
    
    fun loadConversation(id: String) {
        if (isLoading && currentConversationId == id) return
        
        isLoading = true
        scope.launch {
            try {
                val conversation = httpTransport.getConversation(id)
                messages = conversation.messages
                currentConversationId = id
                // Note: sources are not persisted, so we clear them when loading
                sourcesByMessage = emptyMap()
                error = null
            } catch (e: Exception) {
                error = "Error loading conversation: ${e.message}"
                messages = listOf(ChatMessage("assistant", "Error loading conversation: ${e.message}"))
            } finally {
                isLoading = false
            }
        }
    }
    
    fun createNewChat() {
        scope.launch {
            try {
                val newConversation = httpTransport.createConversation()
                currentConversationId = newConversation.id
                messages = emptyList()
                sourcesByMessage = emptyMap()
                error = null
                loadConversations() // Refresh list
            } catch (e: Exception) {
                error = "Error creating conversation: ${e.message}"
            }
        }
    }
    
    fun deleteConversation(id: String) {
        scope.launch {
            try {
                httpTransport.deleteConversation(id)
                if (currentConversationId == id) {
                    currentConversationId = null
                    messages = emptyList()
                    sourcesByMessage = emptyMap()
                }
                loadConversations() // Refresh list
            } catch (e: Exception) {
                error = "Error deleting conversation: ${e.message}"
            }
        }
    }
    
    fun sendMessage(text: String) {
        if (currentConversationId == null) {
            createNewChat()
            // Wait a bit for the conversation to be created
            scope.launch {
                kotlinx.coroutines.delay(100)
                sendMessageInternal(text)
            }
        } else {
            sendMessageInternal(text)
        }
    }
    
    private var isSendingMessage = false
    
    private fun sendMessageInternal(text: String) {
        if (isSendingMessage || isLoading) return
        isSendingMessage = true
        
        val userMessage = ChatMessage("user", text)
        val currentMessages = messages
        messages = currentMessages + userMessage
        isLoading = true
        error = null
        
        scope.launch {
            try {
                val request = RagChatRequest(
                    messages = messages,
                    conversationId = currentConversationId,
                    topK = topK,
                    enableFilter = enableFilter,
                    minSimilarity = minSimilarity,
                    allowModelFallback = allowModelFallback,
                    minBestScoreForRag = minBestScoreForRag,
                    autoFetchWiki = autoFetchWiki
                )
                val response = httpTransport.ragChat(request)
                
                // Only add response if we're still on the same conversation
                if (currentConversationId != null) {
                    messages = messages + response.message
                    // Store sources for this message
                    if (response.labeledSources.isNotEmpty()) {
                        sourcesByMessage = sourcesByMessage + (response.message.timestamp.toString() to response.labeledSources)
                    }
                }
                
                // Refresh conversation list to update titles
                scope.launch {
                    try {
                        conversations = httpTransport.listConversations()
                    } catch (e: Exception) {
                        // Ignore errors when refreshing list
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: "Unknown error"
                // Remove the user message if sending failed
                messages = currentMessages
            } finally {
                isLoading = false
                isSendingMessage = false
            }
        }
    }
}

