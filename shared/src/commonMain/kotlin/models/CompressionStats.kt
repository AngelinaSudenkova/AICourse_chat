package models

import kotlinx.serialization.Serializable

@Serializable
data class CompressionStats(
    val tokensRawApprox: Int,
    val tokensCompressedApprox: Int,
    val savingsPercent: Int
)

