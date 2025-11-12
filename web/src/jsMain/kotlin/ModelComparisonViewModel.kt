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

enum class PromptPreset(val label: String) {
    Short("Short"),
    Long("Long"),
    OverLimit("Over-limit")
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
    val totalTokensApprox: Int,
    val costUSD: Double?,
    val output: String,
    val overLimit: Boolean,
    val error: String?,
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
    val presets: List<PromptPreset> = PromptPreset.entries

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

    var detailTab by mutableStateOf(DetailTab.PerModel)
        private set

    private val softTokenLimits = mapOf(
        "openai/gpt-oss-120b" to 128_000,
        "openai/gpt-oss-120b:hf-inference" to 128_000,
        "openai/gpt-oss-120b:fastest" to 128_000,
        "Qwen/Qwen2.5-7B-Instruct" to 128_000,
        "Qwen/Qwen2.5-7B-Instruct:hf-inference" to 128_000,
        "Qwen/Qwen2.5-7B-Instruct:together" to 128_000,
        "deepseek-ai/DeepSeek-R1" to 32_768,
        "deepseek-ai/DeepSeek-R1:hf-inference" to 32_768,
        "deepseek-ai/DeepSeek-R1:fastest" to 32_768
    )

    private val defaultSoftLimit = 32_768

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

    fun applyPreset(preset: PromptPreset) {
        val base = when (currentTask) {
            ComparisonTask.Story -> storyPreset(preset)
            ComparisonTask.Code -> codePreset(preset)
        }
        prompt = base
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

    fun selectDetailTab(tab: DetailTab) {
        if (detailTab == tab) return
        detailTab = tab
    }

    private fun ModelRun.toUiRun(): UiRun = UiRun(
        model = model,
        latencyMs = latencyMs,
        inputTokensApprox = inputTokensApprox,
        outputTokensApprox = outputTokensApprox,
        totalTokensApprox = totalTokensApprox,
        costUSD = costUSD,
        output = output,
        overLimit = overLimit,
        error = error,
        expanded = false
    )

    private fun storyPreset(preset: PromptPreset): String = when (preset) {
        PromptPreset.Short -> "Describe in one vivid sentence a curious cat uncovering a hidden room and hint at its secret."
        PromptPreset.Long -> "Write a richly detailed 10-sentence story about a curious cat finding a hidden room at night. Include sensory details, the cat's internal thoughts, and end with a reflective twist." 
        PromptPreset.OverLimit -> buildHugePrompt(
            base = "Narrate the chronicles of a curious cat exploring a hidden mansion, covering history, emotions, dialogue, discoveries, and philosophical musings in exhaustive detail.",
            targetTokens = overLimitTargetTokens()
        )
    }

    private fun codePreset(preset: PromptPreset): String = when (preset) {
        PromptPreset.Short -> "Explain the Fibonacci sequence in one sentence and output the first 8 terms inline."
        PromptPreset.Long -> "Write idiomatic Kotlin code that prints the Fibonacci sequence up to 30 terms, includes memoization and iterative variants, demonstrates unit tests, and documents time complexity." 
        PromptPreset.OverLimit -> buildHugePrompt(
            base = "Provide deeply annotated Kotlin and pseudocode explanations of the Fibonacci sequence, covering history, dynamic programming, matrix exponentiation, closed-form analysis, and benchmarking instructions.",
            targetTokens = overLimitTargetTokens()
        )
    }

    private fun buildHugePrompt(base: String, targetTokens: Int): String {
        val targetChars = (targetTokens * 5L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val unit = "$base\nPlease elaborate in exhaustive technical and narrative detail. Continue expanding the discussion without summarizing.\n"
        val builder = StringBuilder(targetChars + 1024)
        while (builder.length < targetChars) {
            builder.append(unit)
        }
        return builder.toString()
    }

    private fun overLimitTargetTokens(): Int {
        val maxLimit = models
            .mapNotNull { spec ->
                val candidates = listOf(spec.id, spec.id.substringBefore(":"))
                candidates.firstNotNullOfOrNull { softTokenLimits[it] }
            }
            .maxOrNull() ?: defaultSoftLimit
        val cushion = (maxLimit * 0.05).toInt().coerceAtLeast(2_048)
        return maxLimit + cushion
    }
}

enum class DetailTab(val label: String) {
    PerModel("Per Model"),
    RawLogs("Raw Logs")
}


