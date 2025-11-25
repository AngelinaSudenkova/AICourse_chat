package models

import kotlinx.serialization.Serializable

@Serializable
data class WikiArticleMeta(
    val id: String,           // e.g. slug or title
    val title: String,
    val filePath: String      // relative path under data/wiki/
)

@Serializable
data class WikiChunk(
    val id: String,           // e.g. "$articleId::$index"
    val articleId: String,
    val index: Int,
    val text: String,
    val embedding: List<Double>
)

@Serializable
data class WikiIndex(
    val createdAt: Long,
    val articles: List<WikiArticleMeta>,
    val chunks: List<WikiChunk>
)

@Serializable
data class WikiFetchRequest(
    val topic: String        // e.g. "Quantum computing"
)

@Serializable
data class WikiIndexRequest(
    val topics: List<String> // topics to index, or empty to index all files under data/wiki/
)

@Serializable
data class WikiSearchRequest(
    val query: String,
    val topK: Int = 5
)

@Serializable
data class WikiSearchResultItem(
    val chunkId: String,
    val articleId: String,
    val title: String,
    val score: Double,
    val snippet: String
)

@Serializable
data class WikiSearchResponse(
    val query: String,
    val results: List<WikiSearchResultItem>
)

