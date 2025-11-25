import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import models.*
import transport.HttpTransport

class RagCompareViewModel(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val httpTransport: HttpTransport
) {
    var question by mutableStateOf("")
    var topK by mutableStateOf(5)
    var loading by mutableStateOf(false)
    var result by mutableStateOf<RagAnswerComparison?>(null)
    var error by mutableStateOf<String?>(null)
    
    fun submit() {
        val q = question.trim()
        if (q.isEmpty()) {
            error = "Please enter a question"
            return
        }
        
        loading = true
        error = null
        result = null
        
        scope.launch {
            try {
                val req = RagQuestionRequest(
                    question = q,
                    topK = topK
                )
                result = httpTransport.compareRagAnswers(req)
            } catch (e: Exception) {
                error = e.message ?: "Unknown error"
            } finally {
                loading = false
            }
        }
    }
    
    fun reset() {
        question = ""
        topK = 5
        result = null
        error = null
    }
}

