package models

import kotlinx.serialization.Serializable

@Serializable
data class FinanceEntry(
    val id: String,
    val title: String,
    val amount: Double,
    val date: String,
    val categoryIds: List<String> = emptyList(),
    val url: String? = null
)

@Serializable
data class FinanceEntriesResult(
    val entries: List<FinanceEntry>,
    val totalCount: Int,
    val databaseId: String
)

