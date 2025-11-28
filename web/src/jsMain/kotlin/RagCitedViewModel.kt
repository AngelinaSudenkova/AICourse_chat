import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import models.*
import transport.HttpTransport

class RagCitedViewModel(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val httpTransport: HttpTransport
) {
    var question by mutableStateOf("")
    var topK by mutableStateOf(5)
    var enableFilter by mutableStateOf(true)
    var minSimilarity by mutableStateOf(0.3)
    var allowModelFallback by mutableStateOf(true)
    var minBestScoreForRag by mutableStateOf(0.25)
    var autoFetchWiki by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var result by mutableStateOf<RagCitedAnswerResponse?>(null)
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
                val req = RagCitedAnswerRequest(
                    question = q,
                    topK = topK,
                    enableFilter = enableFilter,
                    minSimilarity = minSimilarity,
                    allowModelFallback = allowModelFallback,
                    minBestScoreForRag = minBestScoreForRag,
                    autoFetchWiki = autoFetchWiki
                )
                result = httpTransport.getRagWithCitations(req)
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
        enableFilter = true
        minSimilarity = 0.3
        allowModelFallback = true
        minBestScoreForRag = 0.25
        autoFetchWiki = false
        result = null
        error = null
    }
}

