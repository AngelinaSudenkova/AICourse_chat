package wiki

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import models.WikiArticleMeta
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

class WikiFetcher(
    private val client: HttpClient = HttpClient(CIO)
) {
    private val rootDir = Paths.get("data/wiki")

    init {
        // Ensure directory exists
        Files.createDirectories(rootDir)
    }

    suspend fun fetchAndSave(topic: String): WikiArticleMeta {
        // 1. Normalize topic -> slug (e.g. "Quantum computing" -> "Quantum_computing")
        val slug = topic.replace(" ", "_").replace("/", "_").replace("\\", "_")
        val filePath = "data/wiki/$slug.txt"

        // 2. Call Wikipedia API to get plain text content
        val title = topic.replace(" ", "_")
        
        try {
            // First, try to get the full article text
            val textUrl = "https://en.wikipedia.org/api/rest_v1/page/html/$title"
            val textResponse: HttpResponse = client.get(textUrl) {
                headers {
                    append(HttpHeaders.Accept, "text/html")
                }
            }

            if (textResponse.status.isSuccess()) {
                val htmlContent = textResponse.bodyAsText()
                val plainText = extractPlainTextFromHtml(htmlContent)
                
                // Save to file
                val file = rootDir.resolve("$slug.txt")
                Files.write(file, plainText.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                
                return WikiArticleMeta(
                    id = slug,
                    title = topic,
                    filePath = filePath
                )
            }

            // Fallback to summary API
            val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$title"
            val summaryResponse: HttpResponse = client.get(summaryUrl) {
                headers {
                    append(HttpHeaders.Accept, "application/json")
                }
            }
            
            if (!summaryResponse.status.isSuccess()) {
                throw Exception("Wikipedia article not found for topic: $topic")
            }
            
            val summaryBody = summaryResponse.bodyAsText()
            val extract = extractTextFromSummary(summaryBody)
            
            // Save to file
            val file = rootDir.resolve("$slug.txt")
            Files.write(file, extract.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            
            return WikiArticleMeta(
                id = slug,
                title = topic,
                filePath = filePath
            )
        } catch (e: Exception) {
            throw Exception("Failed to fetch Wikipedia article for '$topic': ${e.message}", e)
        }
    }

    fun listAllLocalArticles(): List<WikiArticleMeta> {
        return try {
            Files.list(rootDir)
                .filter { it.toString().endsWith(".txt") }
                .map { file ->
                    val fileName = file.fileName.toString()
                    val slug = fileName.removeSuffix(".txt")
                    val title = slug.replace("_", " ")
                    WikiArticleMeta(
                        id = slug,
                        title = title,
                        filePath = "data/wiki/$fileName"
                    )
                }
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun readArticleText(meta: WikiArticleMeta): String {
        val file = rootDir.resolve(meta.filePath.removePrefix("data/wiki/"))
        return try {
            Files.readString(file)
        } catch (e: Exception) {
            throw Exception("Failed to read article file: ${e.message}", e)
        }
    }

    private fun extractTextFromSummary(jsonBody: String): String {
        // Simple extraction: try to get extract field from JSON
        return try {
            val extractStart = jsonBody.indexOf("\"extract\":\"") + 11
            val extractEnd = jsonBody.indexOf("\"", extractStart)
            if (extractStart > 10 && extractEnd > extractStart) {
                jsonBody.substring(extractStart, extractEnd)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            } else {
                "Wikipedia article content for this topic."
            }
        } catch (e: Exception) {
            "Wikipedia article content. Error parsing summary: ${e.message}"
        }
    }

    private fun extractPlainTextFromHtml(html: String): String {
        // Simple HTML tag removal - for production, use a proper HTML parser
        return html
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

