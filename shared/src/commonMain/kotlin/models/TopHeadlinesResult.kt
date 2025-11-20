package models

import kotlinx.serialization.Serializable

@Serializable
data class TopHeadlinesResult(
    val articles: List<NewsArticle>,
    val totalResults: Int,
    val country: String? = null,
    val category: String? = null,
    val fetchedAt: String
)

