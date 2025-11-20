package models

import kotlinx.serialization.Serializable

@Serializable
data class NewsSummaryResponse(
    val articles: List<NewsArticle>,
    val aiSummary: String? = null,
    val fetchedAt: String,
    val totalResults: Int
)

