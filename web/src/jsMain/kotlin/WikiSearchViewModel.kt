import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import models.*
import transport.HttpTransport

class WikiSearchViewModel(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val httpTransport: HttpTransport
) {
    var topicToFetch by mutableStateOf("")
    var searchQuery by mutableStateOf("")
    var isLoadingFetch by mutableStateOf(false)
    var isLoadingIndex by mutableStateOf(false)
    var isLoadingSearch by mutableStateOf(false)
    var fetchStatus by mutableStateOf<String?>(null)
    var searchResults by mutableStateOf<WikiSearchResponse?>(null)
    var error by mutableStateOf<String?>(null)
    
    fun fetchAndIndex() {
        if (topicToFetch.isBlank()) {
            error = "Please enter a topic to fetch"
            return
        }
        
        isLoadingFetch = true
        isLoadingIndex = true
        error = null
        fetchStatus = null
        
        scope.launch {
            try {
                // Step 1: Fetch the article
                val article = httpTransport.fetchWikiArticle(WikiFetchRequest(topicToFetch))
                fetchStatus = "Fetched article: ${article.title}"
                
                // Step 2: Index it
                val index = httpTransport.indexWikiArticles(WikiIndexRequest(listOf(topicToFetch)))
                fetchStatus = "Indexed ${index.chunks.size} chunks for topic: ${topicToFetch}"
            } catch (e: Exception) {
                error = e.message ?: "Failed to fetch and index article"
            } finally {
                isLoadingFetch = false
                isLoadingIndex = false
            }
        }
    }
    
    fun search() {
        if (searchQuery.isBlank()) {
            error = "Please enter a search query"
            return
        }
        
        isLoadingSearch = true
        error = null
        searchResults = null
        
        scope.launch {
            try {
                val results = httpTransport.searchWiki(WikiSearchRequest(query = searchQuery, topK = 5))
                searchResults = results
            } catch (e: Exception) {
                error = e.message ?: "Failed to search wiki"
            } finally {
                isLoadingSearch = false
            }
        }
    }
    
    fun reset() {
        topicToFetch = ""
        searchQuery = ""
        fetchStatus = null
        searchResults = null
        error = null
    }
}

