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
        val articles = mutableListOf<WikiArticleMeta>()
        val allChunks = mutableListOf<WikiChunk>()

        for (topic in topics) {
            // Fetch if not already local
            val existing = fetcher.listAllLocalArticles().find { it.title.equals(topic, ignoreCase = true) }
            val article = existing ?: fetcher.fetchAndSave(topic)
            articles.add(article)

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
                allChunks.add(chunk)
            }
        }

        val index = WikiIndex(
            createdAt = currentTimeMillis(),
            articles = articles,
            chunks = allChunks
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

