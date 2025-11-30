package wiki

import indexing.EmbeddingsClient
import indexing.TextChunker
import models.*
import platform.currentTimeMillis
import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class WikiIndexer(
    private val fetcher: WikiFetcher,
    private val chunker: TextChunker,
    private val embeddingsClient: EmbeddingsClient
) {
    private val indexPath = Paths.get("data/wiki/wiki_index.json")
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    suspend fun buildIndexForTopics(topics: List<String>): WikiIndex {
        return buildIndexForTopics(topics, mergeWithExisting = true)
    }

    suspend fun buildIndexForTopics(topics: List<String>, mergeWithExisting: Boolean): WikiIndex {
        // Load existing index if merging
        val existingIndex = if (mergeWithExisting) loadIndex() else null
        val existingArticleIds = existingIndex?.articles?.map { it.id }?.toSet() ?: emptySet()

        val newArticles = mutableListOf<WikiArticleMeta>()
        val newChunks = mutableListOf<WikiChunk>()

        for (topic in topics) {
            // Skip if article already exists in index
            val existingLocal = fetcher.listAllLocalArticles().find { it.title.equals(topic, ignoreCase = true) }
            if (existingLocal != null && existingArticleIds.contains(existingLocal.id)) {
                println("Article '${existingLocal.title}' already in index, skipping")
                continue
            }

            // Fetch if not already local
            val article = existingLocal ?: fetcher.fetchAndSave(topic)
            
            // Skip if we just fetched but it's already in the index (shouldn't happen, but safety check)
            if (existingArticleIds.contains(article.id)) {
                println("Article '${article.title}' already in index after fetch, skipping")
                continue
            }

            newArticles.add(article)

            // Read article text
            val text = fetcher.readArticleText(article)
            
            // Chunk the text
            val chunks = chunker.chunk(text)
            
            // Generate embeddings for chunks
            println("Generating embeddings for ${chunks.size} chunks from article: ${article.title}")
            val embeddings = embeddingsClient.embed(chunks)
            
            // Create WikiChunk objects
            chunks.forEachIndexed { index, chunkText ->
                val embedding = embeddings.getOrNull(index) ?: emptyList()
                val chunk = WikiChunk(
                    id = "${article.id}::$index",
                    articleId = article.id,
                    index = index,
                    text = chunkText,
                    embedding = embedding
                )
                newChunks.add(chunk)
            }
        }

        // Merge with existing index if merging
        val finalArticles = if (mergeWithExisting && existingIndex != null) {
            existingIndex.articles + newArticles
        } else {
            newArticles
        }

        val finalChunks = if (mergeWithExisting && existingIndex != null) {
            existingIndex.chunks + newChunks
        } else {
            newChunks
        }

        val index = WikiIndex(
            createdAt = currentTimeMillis(),
            articles = finalArticles,
            chunks = finalChunks
        )

        // Save index to file
        saveIndex(index)

        return index
    }

    suspend fun buildIndexFromLocal(): WikiIndex {
        val articles = fetcher.listAllLocalArticles()
        return buildIndexForTopics(articles.map { it.title })
    }

    fun saveIndex(index: WikiIndex) {
        try {
            Files.createDirectories(indexPath.parent)
            val jsonString = json.encodeToString(WikiIndex.serializer(), index)
            Files.writeString(indexPath, jsonString)
            println("Saved wiki index with ${index.articles.size} articles and ${index.chunks.size} chunks")
        } catch (e: Exception) {
            throw Exception("Failed to save wiki index: ${e.message}", e)
        }
    }

    fun loadIndex(): WikiIndex? {
        return try {
            if (!Files.exists(indexPath)) {
                return null
            }
            val jsonString = Files.readString(indexPath)
            json.decodeFromString<WikiIndex>(jsonString)
        } catch (e: Exception) {
            println("Failed to load wiki index: ${e.message}")
            null
        }
    }
}

