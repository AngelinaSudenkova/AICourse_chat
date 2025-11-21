import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import models.ResearchLogEntry
import transport.HttpTransport

class ResearchLogViewModel(private val scope: CoroutineScope) {
    private val transport = HttpTransport("http://localhost:8081")

    var entries by mutableStateOf<List<ResearchLogEntry>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var selectedEntry by mutableStateOf<ResearchLogEntry?>(null)
        private set

    var selectedContent by mutableStateOf<String?>(null)
        private set

    var isLoadingContent by mutableStateOf(false)
        private set

    fun loadLog() {
        if (isLoading) return

        isLoading = true
        error = null

        scope.launch {
            try {
                val response = transport.getResearchLog()
                entries = response.entries
            } catch (e: Throwable) {
                val errorMsg = e.message ?: e::class.simpleName ?: "Unknown error"
                error = if (errorMsg.contains("Failed to fetch") || errorMsg.contains("NetworkError") || errorMsg.contains("Connection refused")) {
                    "Cannot connect to server. Make sure the backend server is running on port 8081. Run: ./start-server.sh"
                } else {
                    "Error loading research log: $errorMsg"
                }
                entries = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    fun loadFileContent(entry: ResearchLogEntry) {
        if (isLoadingContent) return

        selectedEntry = entry
        isLoadingContent = true
        error = null

        scope.launch {
            try {
                val response = transport.getResearchFile(entry.filename)
                selectedContent = response["content"] ?: "No content available"
            } catch (e: Throwable) {
                val errorMsg = e.message ?: e::class.simpleName ?: "Unknown error"
                error = "Error loading file: $errorMsg"
                selectedContent = null
            } finally {
                isLoadingContent = false
            }
        }
    }

    fun clearSelection() {
        selectedEntry = null
        selectedContent = null
    }
}

