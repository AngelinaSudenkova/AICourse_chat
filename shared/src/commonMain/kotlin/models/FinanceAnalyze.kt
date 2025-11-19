package models

import kotlinx.serialization.Serializable

@Serializable
data class FinanceAnalyzeRequest(
    val fromDate: String? = null,
    val toDate: String? = null,
    val question: String? = null
)

@Serializable
data class FinanceAnalyzeResponse(
    val entries: List<FinanceEntry>,
    val aiAnswer: String
)

