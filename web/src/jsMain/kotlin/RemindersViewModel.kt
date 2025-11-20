import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import models.Reminder
import models.ReminderSummary
import transport.HttpTransport

class RemindersViewModel(private val scope: CoroutineScope) {
    private val transport = HttpTransport("http://localhost:8081")

    var reminders by mutableStateOf<List<Reminder>>(emptyList())
        private set

    var summary by mutableStateOf<ReminderSummary?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isLoadingSummary by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var newReminderText by mutableStateOf("")
        private set

    var newReminderDueDate by mutableStateOf<String>("")
        private set

    fun updateNewReminderText(text: String) {
        newReminderText = text
    }

    fun updateNewReminderDueDate(date: String) {
        newReminderDueDate = date
    }

    fun loadReminders() {
        if (isLoading) return

        isLoading = true
        error = null

        scope.launch {
            try {
                val response = transport.listReminders(onlyPending = true)
                reminders = response.reminders
            } catch (e: Throwable) {
                val errorMsg = e.message ?: e::class.simpleName ?: "Unknown error"
                error = if (errorMsg.contains("Failed to fetch") || errorMsg.contains("NetworkError") || errorMsg.contains("Connection refused")) {
                    "Cannot connect to server. Make sure the backend server is running on port 8081. Run: ./start-server.sh"
                } else {
                    "Error loading reminders: $errorMsg"
                }
                reminders = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    fun addReminder() {
        if (newReminderText.isBlank() || isLoading) return

        isLoading = true
        error = null

        scope.launch {
            try {
                val dueDate = try {
                    if (newReminderDueDate.isNotBlank()) {
                        val date = kotlin.js.Date(newReminderDueDate)
                        date.getTime().toLong()
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }

                val request = models.ReminderAddRequest(
                    text = newReminderText,
                    dueDate = dueDate?.toLong()
                )
                transport.addReminder(request)
                
                // Clear form and reload
                newReminderText = ""
                newReminderDueDate = ""
                loadReminders()
            } catch (e: Throwable) {
                val errorMsg = e.message ?: e::class.simpleName ?: "Unknown error"
                error = if (errorMsg.contains("Failed to fetch") || errorMsg.contains("NetworkError") || errorMsg.contains("Connection refused")) {
                    "Cannot connect to server. Make sure the backend server is running on port 8081. Run: ./start-server.sh"
                } else {
                    "Error adding reminder: $errorMsg"
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun loadSummary() {
        if (isLoadingSummary) return

        isLoadingSummary = true
        error = null

        scope.launch {
            try {
                val result = transport.getReminderSummary()
                summary = result
            } catch (e: Throwable) {
                val errorMsg = e.message ?: e::class.simpleName ?: "Unknown error"
                error = if (errorMsg.contains("Failed to fetch") || errorMsg.contains("NetworkError") || errorMsg.contains("Connection refused")) {
                    "Cannot connect to server. Make sure the backend server is running on port 8081. Run: ./start-server.sh"
                } else {
                    "Error loading summary: $errorMsg"
                }
            } finally {
                isLoadingSummary = false
            }
        }
    }
}

