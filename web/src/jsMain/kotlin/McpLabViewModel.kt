import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import models.McpTool
import models.McpJsonMessage
import transport.HttpTransport

class McpLabViewModel(private val scope: CoroutineScope) {
    private val transport = HttpTransport("http://localhost:8081")

    var tools by mutableStateOf<List<McpTool>>(emptyList())
        private set

    var messages by mutableStateOf<List<McpJsonMessage>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun loadTools() {
        if (isLoading) return

        isLoading = true
        error = null

        scope.launch {
            try {
                val response = transport.listMcpTools()
                tools = response.tools
                messages = response.messages
            } catch (e: Throwable) {
                error = "Error: ${e.message ?: e::class.simpleName}"
                tools = emptyList()
                messages = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    fun reset() {
        tools = emptyList()
        messages = emptyList()
        error = null
    }
}

