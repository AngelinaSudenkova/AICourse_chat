package news

import ai.GeminiClient
import mcp.NewsMcpClient
import models.TopHeadlinesResult
import kotlinx.coroutines.*
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import models.NewsSummaryResponse
import models.NewsArticle

class NewsScheduler(
    private val newsClient: NewsMcpClient,
    private val geminiClient: GeminiClient,
    private val storageFile: File = File("server/data/news-summary.json")
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    
    init {
        // Ensure data directory exists
        storageFile.parentFile?.mkdirs()
    }
    
    /**
     * Builds a prompt for Gemini to summarize news headlines.
     */
    private fun buildNewsSummaryPrompt(headlines: TopHeadlinesResult): String {
        val topArticles = headlines.articles.take(10)
        
        return buildString {
            appendLine("You are a news analyst. Summarize the following top news headlines:")
            appendLine()
            appendLine("Headlines:")
            topArticles.forEachIndexed { index, article ->
                appendLine("${index + 1}. ${article.title}")
                if (article.description != null) {
                    appendLine("   ${article.description}")
                }
                appendLine("   Source: ${article.source} | Published: ${article.publishedAt}")
                appendLine()
            }
            appendLine("Please provide a concise summary (2-3 paragraphs) covering:")
            appendLine("- Main themes and trends")
            appendLine("- Key risks or concerns")
            appendLine("- Notable developments")
            appendLine()
            appendLine("Be concise and focus on the most important information.")
        }
    }
    
    /**
     * Fetches news, generates AI summary, and stores it.
     */
    suspend fun fetchAndSummarize(): NewsSummaryResponse {
        try {
            // Fetch headlines
            val headlines = newsClient.getTopHeadlines(
                country = "us", // Default to US, can be made configurable
                pageSize = 20
            )
            
            // Generate AI summary
            val prompt = buildNewsSummaryPrompt(headlines)
            val aiSummary = try {
                geminiClient.generate(prompt)
            } catch (e: Exception) {
                println("Failed to generate AI summary: ${e.message}")
                null
            }
            
            // Create response
            val response = NewsSummaryResponse(
                articles = headlines.articles,
                aiSummary = aiSummary,
                fetchedAt = headlines.fetchedAt,
                totalResults = headlines.totalResults
            )
            
            // Store response
            saveSummary(response)
            
            return response
        } catch (e: Exception) {
            println("Error in fetchAndSummarize: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    /**
     * Saves the summary to JSON file.
     */
    private fun saveSummary(summary: NewsSummaryResponse) {
        try {
            storageFile.writeText(json.encodeToString(NewsSummaryResponse.serializer(), summary))
        } catch (e: Exception) {
            println("Failed to save news summary: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Loads the latest summary from storage.
     */
    fun loadLatestSummary(): NewsSummaryResponse? {
        return try {
            if (storageFile.exists()) {
                val content = storageFile.readText()
                json.decodeFromString(NewsSummaryResponse.serializer(), content)
            } else {
                null
            }
        } catch (e: Exception) {
            println("Failed to load news summary: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Starts the scheduler to fetch news every 15 minutes.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            // Fetch immediately on startup
            try {
                println("NewsScheduler: Fetching initial news summary...")
                fetchAndSummarize()
                println("NewsScheduler: Initial news summary fetched successfully")
            } catch (e: Exception) {
                println("NewsScheduler: Failed to fetch initial news summary: ${e.message}")
                e.printStackTrace()
            }
            
            // Then fetch every 15 minutes
            while (true) {
                delay(15 * 60 * 1000) // 15 minutes in milliseconds
                try {
                    println("NewsScheduler: Fetching news summary...")
                    fetchAndSummarize()
                    println("NewsScheduler: News summary fetched successfully")
                } catch (e: Exception) {
                    println("NewsScheduler: Failed to fetch news summary: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
}

