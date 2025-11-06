package structured

import kotlinx.serialization.Serializable

@Serializable
data class ReadingSummary(
    val title: String,
    val theSourceOfTheText: String,
    val timeOfReading: String,
    val summary: String
)

