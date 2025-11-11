import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import transport.HttpTransport
import structured.ReasonResponse

data class ReasonRun(
    val method: String,
    val promptUsed: String,
    val answer: String
)

class ReasoningLabViewModel(private val scope: CoroutineScope) {
    var challenge by mutableStateOf(
        "Monty Hall: I pick Door #1. The host opens another empty door and offers to switch. Should I switch? What are the exact probabilities for win on switch vs. stay?"
    )
    var runs by mutableStateOf<List<ReasonRun>>(emptyList())
    var isLoading by mutableStateOf(false)
    var loadingMethod by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    
    private val transport = HttpTransport("http://localhost:8081")
    
    fun runDirect() {
        runMethod("direct")
    }
    
    fun runStep() {
        runMethod("step")
    }
    
    fun runMeta() {
        runMethod("meta")
    }
    
    fun runExperts() {
        runMethod("experts")
    }
    
    private fun runMethod(method: String) {
        if (isLoading || challenge.trim().isEmpty()) return
        isLoading = true
        loadingMethod = method
        error = null
        
        scope.launch {
            try {
                val response: ReasonResponse = transport.reason(method, challenge.trim())
                runs = runs + ReasonRun(
                    method = response.method,
                    promptUsed = response.promptUsed,
                    answer = response.answer
                )
            } catch (e: Exception) {
                error = "Error running $method: ${e.message}"
                runs = runs + ReasonRun(
                    method = method,
                    promptUsed = "Error",
                    answer = "Error: ${e.message}"
                )
            } finally {
                isLoading = false
                loadingMethod = null
            }
        }
    }
    
    fun compare(): Map<String, String> {
        // Extract key information from each run for comparison
        val comparison = mutableMapOf<String, String>()
        runs.forEach { run ->
            // Try to extract numeric results or key conclusions
            val answerLines = run.answer.lines()
            val keyLines = answerLines.filter { 
                it.contains("probability", ignoreCase = true) ||
                it.contains("chance", ignoreCase = true) ||
                it.contains("result", ignoreCase = true) ||
                it.matches(Regex(".*\\d+%?.*")) ||
                it.contains("/") && it.matches(Regex(".*\\d+/\\d+.*"))
            }.take(3)
            comparison[run.method] = keyLines.joinToString(" | ") { it.trim() }.take(150)
        }
        return comparison
    }
    
    fun reset() {
        runs = emptyList()
        error = null
    }
    
    fun clearError() {
        error = null
    }
}

