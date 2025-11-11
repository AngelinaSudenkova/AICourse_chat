package structured

import kotlinx.serialization.Serializable

@Serializable
data class ModelSpec(
    val id: String,
    val pricePer1kInput: Double? = null,
    val pricePer1kOutput: Double? = null
)

@Serializable
data class ModelsCompareRequest(
    val prompt: String,
    val models: List<ModelSpec>
)

@Serializable
data class ModelRun(
    val model: String,
    val latencyMs: Long,
    val inputTokensApprox: Int,
    val outputTokensApprox: Int,
    val costUSD: Double? = null,
    val output: String
)

@Serializable
data class ModelsCompareResponse(
    val prompt: String,
    val runs: List<ModelRun>
)


