package research

import ai.GeminiClient
import mcp.ResearchMcpClient
import models.NewsArticle
import models.ResearchSummary
import models.ResearchPipelineResult
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

class ResearchPipeline(
    private val researchClient: ResearchMcpClient,
    private val geminiClient: GeminiClient
) {
    /**
     * Runs the research pipeline: search → summarize → save
     */
    suspend fun runResearchPipeline(query: String): ResearchPipelineResult {
        // Step 1: Search docs
        val searchResult = researchClient.searchDocs(query, pageSize = 10)
        
        // Step 2: Summarize with AI
        val summary = summarizeResearch(query, searchResult.articles)
        
        // Step 3: Generate filename and format content
        val filename = generateFilenameFromQuery(query)
        val content = formatSummaryAsMarkdown(summary, query)
        
        // Step 4: Save to file
        val saveResult = researchClient.saveToFile(filename, content)
        
        return ResearchPipelineResult(
            query = query,
            summary = summary,
            savedPath = saveResult.path
        )
    }
    
    /**
     * Summarizes research articles using Gemini.
     */
    private suspend fun summarizeResearch(query: String, articles: List<NewsArticle>): ResearchSummary {
        val prompt = buildString {
            appendLine("You are a research assistant. Summarize the following news articles about: \"$query\"")
            appendLine()
            appendLine("Articles:")
            articles.forEachIndexed { index, article ->
                appendLine("${index + 1}. ${article.title}")
                if (article.description != null) {
                    appendLine("   ${article.description}")
                }
                appendLine("   Source: ${article.source}")
                appendLine("   URL: ${article.url}")
                appendLine("   Published: ${article.publishedAt}")
                appendLine()
            }
            appendLine()
            appendLine("Please provide a comprehensive summary in JSON format with the following structure:")
            appendLine("{")
            appendLine("  \"title\": \"A concise title for this research summary\",")
            appendLine("  \"summary\": \"A detailed summary paragraph (3-5 sentences)\",")
            appendLine("  \"keyPoints\": [\"point 1\", \"point 2\", \"point 3\"],")
            appendLine("  \"sources\": [\"url1\", \"url2\", ...]")
            appendLine("}")
            appendLine()
            appendLine("Output ONLY valid JSON, no markdown, no code blocks.")
        }
        
        val response = geminiClient.generate(prompt)
        
        // Extract JSON from response (might be wrapped in markdown)
        val jsonText = response
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        
        // Parse JSON response
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
        val jsonObject = try {
            json.parseToJsonElement(jsonText).jsonObject
        } catch (e: Exception) {
            // If JSON parsing fails, create a fallback summary
            return ResearchSummary(
                title = "Research Summary: $query",
                summary = response,
                keyPoints = emptyList(),
                sources = articles.map { it.url }
            )
        }
        
        val title = jsonObject["title"]?.jsonPrimitive?.content ?: "Research Summary"
        val summaryText = jsonObject["summary"]?.jsonPrimitive?.content ?: response
        val keyPoints = jsonObject["keyPoints"]?.let {
            if (it is kotlinx.serialization.json.JsonArray) {
                it.mapNotNull { item -> 
                    if (item is kotlinx.serialization.json.JsonPrimitive) {
                        item.content
                    } else {
                        null
                    }
                }
            } else {
                emptyList()
            }
        } ?: emptyList()
        val sources = jsonObject["sources"]?.let {
            if (it is kotlinx.serialization.json.JsonArray) {
                it.mapNotNull { item ->
                    if (item is kotlinx.serialization.json.JsonPrimitive) {
                        item.content
                    } else {
                        null
                    }
                }
            } else {
                emptyList()
            }
        } ?: articles.map { it.url }
        
        return ResearchSummary(
            title = title,
            summary = summaryText,
            keyPoints = keyPoints,
            sources = sources
        )
    }
    
    /**
     * Generates a filename from the query.
     */
    private fun generateFilenameFromQuery(query: String): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateStr = dateFormat.format(Date())
        
        // Sanitize query for filename
        val sanitized = query
            .lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .take(50)
            .trim('-')
        
        return "$dateStr-$sanitized.md"
    }
    
    /**
     * Formats the summary as Markdown.
     */
    private fun formatSummaryAsMarkdown(summary: ResearchSummary, query: String): String {
        return buildString {
            appendLine("# ${summary.title}")
            appendLine()
            appendLine("**Research Query:** $query")
            appendLine()
            appendLine("## Summary")
            appendLine()
            appendLine(summary.summary)
            appendLine()
            appendLine("## Key Points")
            appendLine()
            summary.keyPoints.forEach { point ->
                appendLine("- $point")
            }
            appendLine()
            appendLine("## Sources")
            appendLine()
            summary.sources.forEach { source ->
                appendLine("- [$source]($source)")
            }
            appendLine()
            appendLine("---")
            appendLine("*Generated on ${Date()}*")
        }
    }
}

