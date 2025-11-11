package structured

import kotlinx.serialization.Serializable

@Serializable
data class ReasonRequest(
    val method: String,
    val challenge: String
)

@Serializable
data class ReasonResponse(
    val method: String,
    val promptUsed: String,
    val answer: String
)

