package structured

import kotlinx.serialization.Serializable

@Serializable
data class TempRun(
    val temperature: Double,
    val text: String
)

@Serializable
data class TempRequest(
    val prompt: String,
    val temps: List<Double> = listOf(0.0, 0.7, 1.2)
)

@Serializable
data class TempResponse(
    val prompt: String,
    val runs: List<TempRun>
)

@Serializable
data class CompareRequest(
    val prompt: String,
    val runs: List<TempRun>
)

@Serializable
data class ComparePerTemp(
    val temperature: Double,
    val accuracy: String,
    val creativity: String,
    val diversity: String,
    val risks: String
)

@Serializable
data class CompareSummary(
    val perTemp: List<ComparePerTemp>,
    val keyDifferences: List<String>,
    val bestUseCases: Map<String, List<String>>,
    val verdict: String
)


