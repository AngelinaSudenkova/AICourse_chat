package models

import kotlinx.serialization.Serializable

@Serializable
data class TutorRequest(
    val topic: String,
    val level: String? = null // e.g., "beginner", "intermediate", "advanced"
)

@Serializable
data class WikipediaSnippet(
    val title: String,
    val url: String,
    val summary: String
)

@Serializable
data class YouTubeVideo(
    val title: String,
    val url: String,
    val channel: String? = null,
    val duration: String? = null
)

@Serializable
data class TutorStudyNote(
    val notionPageId: String? = null,
    val title: String,
    val topic: String,
    val keyPoints: List<String>,
    val explanation: String,
    val resources: List<String> // links: Wikipedia + YouTube
)

@Serializable
data class TutorResponse(
    val topic: String,
    val wikipedia: WikipediaSnippet,
    val videos: List<YouTubeVideo>,
    val simpleExplanation: String,
    val studyNote: TutorStudyNote
)

