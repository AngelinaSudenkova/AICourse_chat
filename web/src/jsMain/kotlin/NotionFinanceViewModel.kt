import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import models.FinanceEntry
import models.FinanceAnalyzeResponse
import transport.HttpTransport

class NotionFinanceViewModel(private val scope: CoroutineScope) {
    private val transport = HttpTransport("http://localhost:8081")

    var entries by mutableStateOf<List<FinanceEntry>>(emptyList())
        private set

    var isLoadingSnapshot by mutableStateOf(false)
        private set

    var isLoadingAnalysis by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var aiAnswer by mutableStateOf<String?>(null)
        private set

    var customQuestion by mutableStateOf("")
        private set

    fun updateCustomQuestion(text: String) {
        customQuestion = text
    }

    fun loadSnapshot() {
        if (isLoadingSnapshot) return

        isLoadingSnapshot = true
        error = null

        scope.launch {
            try {
                val result = transport.getFinanceSnapshot()
                entries = result.entries
            } catch (e: Throwable) {
                val errorMsg = e.message ?: e::class.simpleName ?: "Unknown error"
                error = if (errorMsg.contains("Failed to fetch") || errorMsg.contains("NetworkError") || errorMsg.contains("Connection refused")) {
                    "Cannot connect to server. Make sure the backend server is running on port 8081. Run: ./start-server.sh"
                } else {
                    "Error loading finance data: $errorMsg"
                }
                entries = emptyList()
            } finally {
                isLoadingSnapshot = false
            }
        }
    }

    fun askDefaultQuestion() {
        askQuestion(null)
    }

    fun askCustomQuestion() {
        if (customQuestion.isBlank()) return
        askQuestion(customQuestion)
        customQuestion = "" // Clear after sending
    }

    private fun askQuestion(question: String?) {
        if (isLoadingAnalysis) return

        isLoadingAnalysis = true
        error = null
        aiAnswer = null

        scope.launch {
            try {
                val request = models.FinanceAnalyzeRequest(
                    question = question
                )
                val response: FinanceAnalyzeResponse = transport.analyzeFinance(request)
                entries = response.entries
                aiAnswer = response.aiAnswer
            } catch (e: Throwable) {
                val errorMsg = e.message ?: e::class.simpleName ?: "Unknown error"
                error = if (errorMsg.contains("Failed to fetch") || errorMsg.contains("NetworkError") || errorMsg.contains("Connection refused")) {
                    "Cannot connect to server. Make sure the backend server is running on port 8081. Run: ./start-server.sh"
                } else {
                    "Error analyzing finance: $errorMsg"
                }
                aiAnswer = null
            } finally {
                isLoadingAnalysis = false
            }
        }
    }

    fun reset() {
        entries = emptyList()
        aiAnswer = null
        error = null
        customQuestion = ""
    }
}

