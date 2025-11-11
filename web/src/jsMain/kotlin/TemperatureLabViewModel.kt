import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import structured.ComparePerTemp
import structured.CompareSummary
import structured.TempRun
import transport.HttpTransport
import kotlin.math.round

private val DefaultTemps = listOf(0.0, 0.7, 1.2)
private val DefaultTempSet = DefaultTemps.map { it.normalize() }.toSet()

data class CompareCard(
    val perTemp: List<ComparePerTemp>,
    val keyDifferences: List<String>,
    val bestUseCases: Map<String, List<String>>,
    val verdict: String
)

class TemperatureLabViewModel(private val scope: CoroutineScope) {
    private val transport = HttpTransport("http://localhost:8081")

    var codePrompt by mutableStateOf(
        "Write a Kotlin function isEven(n: Int): Boolean that returns true if n is even and false if odd. Include a minimal unit test example."
    )
    var storyPrompt by mutableStateOf(
        "Tell a short 4–6 sentence story about a cat discovering a hidden room at night. End with a gentle twist."
    )

    var codeResults by mutableStateOf<List<TempRun>>(emptyList())
        private set
    var storyResults by mutableStateOf<List<TempRun>>(emptyList())
        private set

    var codeError by mutableStateOf<String?>(null)
        private set
    var storyError by mutableStateOf<String?>(null)
        private set

    var codeLoading by mutableStateOf(false)
        private set
    var storyLoading by mutableStateOf(false)
        private set
    var codeLoadingTemp by mutableStateOf<Double?>(null)
        private set
    var storyLoadingTemp by mutableStateOf<Double?>(null)
        private set

    var codeCompare by mutableStateOf<CompareCard?>(null)
        private set
    var storyCompare by mutableStateOf<CompareCard?>(null)
        private set
    var codeCompareLoading by mutableStateOf(false)
        private set
    var storyCompareLoading by mutableStateOf(false)
        private set
    var codeCompareError by mutableStateOf<String?>(null)
        private set
    var storyCompareError by mutableStateOf<String?>(null)
        private set

    fun runCode(temp: Double?) {
        scope.launch {
            executeRun(prompt = codePrompt, isStory = false, temp = temp)
        }
    }

    fun runStory(temp: Double?) {
        scope.launch {
            executeRun(prompt = storyPrompt, isStory = true, temp = temp)
        }
    }

    fun runCodeAll() = runCode(null)
    fun runStoryAll() = runStory(null)

    fun compareCode() {
        scope.launch {
            executeCompare(prompt = codePrompt, isStory = false)
        }
    }

    fun compareStory() {
        scope.launch {
            executeCompare(prompt = storyPrompt, isStory = true)
        }
    }

    fun resetCode() {
        codeResults = emptyList()
        codeError = null
        codeCompare = null
        codeCompareError = null
    }

    fun resetStory() {
        storyResults = emptyList()
        storyError = null
        storyCompare = null
        storyCompareError = null
    }

    private suspend fun executeRun(prompt: String, isStory: Boolean, temp: Double?): Boolean {
        val setLoading = if (isStory) { value: Boolean -> storyLoading = value } else { value: Boolean -> codeLoading = value }
        val setLoadingTemp = if (isStory) { value: Double? -> storyLoadingTemp = value } else { value: Double? -> codeLoadingTemp = value }
        val setError = if (isStory) { value: String? -> storyError = value } else { value: String? -> codeError = value }
        val currentResults = if (isStory) storyResults else codeResults
        val updateResults: (List<TempRun>) -> Unit = if (isStory) { value -> storyResults = value } else { value -> codeResults = value }
        val clearCompare = if (isStory) { { storyCompare = null } } else { { codeCompare = null } }
        val isLoading = if (isStory) storyLoading else codeLoading

        if (isLoading) return false

        setError(null)
        setLoading(true)
        setLoadingTemp(temp)

        return try {
            val temps = temp?.let { listOf(it) } ?: DefaultTemps
            val response = transport.temperature(prompt, temps)
            val merged = mergeRuns(currentResults, response.runs)
            updateResults(merged)
            clearCompare()
            true
        } catch (e: Throwable) {
            setError("Error: ${e.message ?: e::class.simpleName}")
            false
        } finally {
            setLoadingTemp(null)
            setLoading(false)
        }
    }

    private suspend fun executeCompare(prompt: String, isStory: Boolean) {
        val setCompareLoading = if (isStory) { value: Boolean -> storyCompareLoading = value } else { value: Boolean -> codeCompareLoading = value }
        val setCompareError = if (isStory) { value: String? -> storyCompareError = value } else { value: String? -> codeCompareError = value }
        val setCompare = if (isStory) { value: CompareCard? -> storyCompare = value } else { value: CompareCard? -> codeCompare = value }

        setCompareError(null)
        setCompareLoading(true)

        var runs = if (isStory) storyResults else codeResults

        if (!hasAllTemps(runs)) {
            val success = executeRun(prompt = prompt, isStory = isStory, temp = null)
            runs = if (isStory) storyResults else codeResults
            if (!success && !hasAllTemps(runs)) {
                setCompareError("Unable to generate all temperature runs.")
                setCompareLoading(false)
                return
            }
        }

        runs = runs.filter { it.temperature.normalize() in DefaultTempSet }.sortedBy { it.temperature }

        if (!hasAllTemps(runs)) {
            setCompareError("Unable to generate all temperature runs.")
            setCompareLoading(false)
            return
        }

        try {
            val summary = transport.compareTemperature(prompt, runs)
            setCompare(summary.toCard())
        } catch (e: Throwable) {
            setCompareError("Error: ${e.message ?: e::class.simpleName}")
        } finally {
            setCompareLoading(false)
        }
    }

    private fun mergeRuns(existing: List<TempRun>, newRuns: List<TempRun>): List<TempRun> {
        val map = existing.associateBy { it.temperature.normalize() }.toMutableMap()
        newRuns.forEach { run ->
            map[run.temperature.normalize()] = run
        }
        return map.values.sortedBy { it.temperature }
    }

    private fun hasAllTemps(runs: List<TempRun>): Boolean {
        val temps = runs.map { it.temperature.normalize() }.toSet()
        return DefaultTempSet.all { temps.contains(it) }
    }

    private fun CompareSummary.toCard(): CompareCard {
        val sortedUseCases = bestUseCases.entries
            .sortedBy { it.key }
            .associate { it.toPair() }

        return CompareCard(
            perTemp = perTemp.sortedBy { it.temperature },
            keyDifferences = keyDifferences,
            bestUseCases = sortedUseCases,
            verdict = verdict
        )
    }
}

private fun Double.normalize(): Double = round(this * 10) / 10.0


