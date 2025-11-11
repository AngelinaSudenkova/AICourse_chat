import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import structured.ModelSpec
import structured.ModelsCompareRequest
import structured.ModelRun
import transport.HttpTransport

enum class ComparisonTask(val label: String, val defaultPrompt: String) {
    Story("Story Prompt", "Tell a short 6 sentence story about a curious cat exploring a hidden room.") ,
    Code("Fibonacci Code", "Write idiomatic Kotlin code that prints the Fibonacci sequence up to 20 terms.")
}

data class UiModelSpec(
    val id: String,
    val priceIn: String = "",
    val priceOut: String = ""
)

data class UiRun(
    val model: String,
    val latencyMs: Long,
    val inputTokensApprox: Int,
    val outputTokensApprox: Int,
    val costUSD: Double?,
    val output: String,
    val expanded: Boolean = false
)

class ModelComparisonViewModel(
    private val scope: CoroutineScope,
    private val transport: HttpTransport
) {
    var currentTask by mutableStateOf(ComparisonTask.Story)
        private set

    var prompt by mutableStateOf(currentTask.defaultPrompt)
        private set

    val tasks: List<ComparisonTask> = ComparisonTask.entries

    var models by mutableStateOf(
        listOf(
            UiModelSpec("openai/gpt-oss-120b:fastest"),
            UiModelSpec("deepseek-ai/DeepSeek-R1:fastest"),
            UiModelSpec("Qwen/Qwen2.5-7B-Instruct:together")
        )
    )
        private set

    var results by mutableStateOf<List<UiRun>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun updatePrompt(newPrompt: String) {
        prompt = newPrompt
    }

    fun setTask(task: ComparisonTask) {
        if (task == currentTask) return
        currentTask = task
        prompt = task.defaultPrompt
        results = emptyList()
        error = null
    }

    fun updateModelId(index: Int, value: String) {
        models = models.mapIndexed { i, spec ->
            if (i == index) spec.copy(id = value) else spec
        }
    }

    fun updateModelPriceIn(index: Int, value: String) {
        models = models.mapIndexed { i, spec ->
            if (i == index) spec.copy(priceIn = value) else spec
        }
    }

    fun updateModelPriceOut(index: Int, value: String) {
        models = models.mapIndexed { i, spec ->
            if (i == index) spec.copy(priceOut = value) else spec
        }
    }

    fun addModel() {
        models = models + UiModelSpec("")
    }

    fun removeModel(index: Int) {
        if (models.size <= 1) return
        models = models.filterIndexed { i, _ -> i != index }
    }

    fun run() {
        if (isLoading) return
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isEmpty()) {
            error = "Prompt cannot be empty."
            return
        }

        val specs = models.filter { it.id.isNotBlank() }
        if (specs.isEmpty()) {
            error = "Please provide at least one model ID."
            return
        }

        isLoading = true
        error = null

        scope.launch {
            try {
                val request = ModelsCompareRequest(
                    prompt = trimmedPrompt,
                    models = specs.map { spec ->
                        ModelSpec(
                            id = spec.id.trim(),
                            pricePer1kInput = spec.priceIn.toDoubleOrNull(),
                            pricePer1kOutput = spec.priceOut.toDoubleOrNull()
                        )
                    }
                )

                val response = transport.compareModels(request)
                results = response.runs.map { run -> run.toUiRun() }
            } catch (e: Exception) {
                error = e.message ?: "Unknown error"
                results = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    fun reset() {
        prompt = currentTask.defaultPrompt
        models = listOf(
            UiModelSpec("openai/gpt-oss-120b:fastest"),
            UiModelSpec("deepseek-ai/DeepSeek-R1:fastest"),
            UiModelSpec("Qwen/Qwen2.5-7B-Instruct:together")
        )
        results = emptyList()
        error = null
    }

    fun clearError() {
        error = null
    }

    fun toggleExpand(index: Int) {
        results = results.mapIndexed { i, run ->
            if (i == index) run.copy(expanded = !run.expanded) else run
        }
    }

    private fun ModelRun.toUiRun(): UiRun = UiRun(
        model = model,
        latencyMs = latencyMs,
        inputTokensApprox = inputTokensApprox,
        outputTokensApprox = outputTokensApprox,
        costUSD = costUSD,
        output = output,
        expanded = false
    )
}


