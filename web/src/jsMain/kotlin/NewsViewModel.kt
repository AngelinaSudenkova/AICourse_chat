import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import models.NewsArticle
import models.NewsSummaryResponse
import transport.HttpTransport

class NewsViewModel(private val scope: CoroutineScope) {
    private val transport = HttpTransport("http://localhost:8081")

    var articles by mutableStateOf<List<NewsArticle>>(emptyList())
        private set

    var aiSummary by mutableStateOf<String?>(null)
        private set

    var fetchedAt by mutableStateOf<String>("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun loadLatest() {
        if (isLoading) return

        isLoading = true
        error = null

        scope.launch {
            try {
                val response = transport.getLatestNews()
                articles = response.articles
                aiSummary = response.aiSummary
                fetchedAt = response.fetchedAt
            } catch (e: Throwable) {
                val errorMsg = e.message ?: e::class.simpleName ?: "Unknown error"
                error = if (errorMsg.contains("Failed to fetch") || errorMsg.contains("NetworkError") || errorMsg.contains("Connection refused")) {
                    "Cannot connect to server. Make sure the backend server is running on port 8081. Run: ./start-server.sh"
                } else {
                    "Error loading news: $errorMsg"
                }
                articles = emptyList()
                aiSummary = null
                fetchedAt = ""
            } finally {
                isLoading = false
            }
        }
    }

    fun refresh() {
        if (isRefreshing) return

        isRefreshing = true
        error = null

        scope.launch {
            try {
                val response = transport.refreshNews()
                articles = response.articles
                aiSummary = response.aiSummary
                fetchedAt = response.fetchedAt
            } catch (e: Throwable) {
                val errorMsg = e.message ?: e::class.simpleName ?: "Unknown error"
                error = if (errorMsg.contains("Failed to fetch") || errorMsg.contains("NetworkError") || errorMsg.contains("Connection refused")) {
                    "Cannot connect to server. Make sure the backend server is running on port 8081. Run: ./start-server.sh"
                } else {
                    "Error refreshing news: $errorMsg"
                }
            } finally {
                isRefreshing = false
            }
        }
    }
}

