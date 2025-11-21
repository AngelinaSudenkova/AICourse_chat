package models

import kotlinx.serialization.Serializable

@Serializable
data class ResearchSummary(
    val title: String,
    val summary: String,
    val keyPoints: List<String>,
    val sources: List<String>
)

@Serializable
data class NewsSearchResult(
    val query: String,
    val totalResults: Int,
    val articles: List<NewsArticle>,
    val fetchedAt: String
)

@Serializable
data class SaveFileResult(
    val path: String,
    val ok: Boolean
)

@Serializable
data class ResearchPipelineResult(
    val query: String,
    val summary: ResearchSummary,
    val savedPath: String
)

@Serializable
data class ResearchRequest(
    val query: String
)

@Serializable
data class ResearchResponse(
    val query: String,
    val summary: ResearchSummary,
    val savedPath: String
)

@Serializable
data class ResearchLogEntry(
    val filename: String,
    val path: String,
    val title: String,
    val createdAt: Long,
    val query: String
)

@Serializable
data class ResearchLogResponse(
    val entries: List<ResearchLogEntry>
)

