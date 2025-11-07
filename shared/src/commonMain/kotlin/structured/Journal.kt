package structured

import kotlinx.serialization.Serializable

@Serializable
data class Journal(
    val date: String,
    val title: String,
    val mood: String,
    val moodScore: Int,
    val keyMoments: List<String>,
    val lessons: List<String>,
    val gratitude: List<String>,
    val nextSteps: List<String>,
    val reflectionSummary: String
)

@Serializable
data class JournalResponse(
    val status: String,
    val missing: List<String>,
    val journal: Journal? = null
)

