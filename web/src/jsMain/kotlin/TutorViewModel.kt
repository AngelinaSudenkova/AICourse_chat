import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import models.*
import transport.HttpTransport

class TutorViewModel(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val httpTransport: HttpTransport
) {
    var topic by mutableStateOf("")
    var level by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)
    var response by mutableStateOf<TutorResponse?>(null)
    var error by mutableStateOf<String?>(null)
    
    fun teach() {
        if (topic.isBlank()) {
            error = "Please enter a topic"
            return
        }
        
        isLoading = true
        error = null
        response = null
        
        scope.launch {
            try {
                val request = TutorRequest(
                    topic = topic,
                    level = level
                )
                response = httpTransport.teachTopic(request)
            } catch (e: Exception) {
                error = e.message ?: "Failed to get tutor response"
            } finally {
                isLoading = false
            }
        }
    }
    
    fun reset() {
        topic = ""
        level = null
        response = null
        error = null
    }
}

