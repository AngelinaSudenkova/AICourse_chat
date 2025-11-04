package models

import kotlinx.serialization.Serializable

@Serializable
data class DbSearchResult(
    val id: String,
    val title: String,
    val content: String,
    val score: Double = 0.0
)

