package models

import kotlinx.serialization.Serializable

@Serializable
data class NewsArticle(
    val source: String,
    val author: String? = null,
    val title: String,
    val description: String? = null,
    val url: String,
    val publishedAt: String
)

