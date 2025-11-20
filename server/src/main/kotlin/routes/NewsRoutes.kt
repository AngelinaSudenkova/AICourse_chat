package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mcp.NewsMcpClient
import mcp.McpException
import models.NewsSummaryResponse
import news.NewsScheduler
import ai.GeminiClient
import org.koin.core.context.GlobalContext

fun Route.newsRoutes() {
    val koin = GlobalContext.get()
    val newsClient: NewsMcpClient = koin.get()
    val geminiClient: GeminiClient = koin.get()
    val newsScheduler: NewsScheduler = koin.get()
    
    /**
     * GET /api/news/latest
     * Returns the latest stored headlines and AI summary.
     */
    get("/news/latest") {
        try {
            val summary = newsScheduler.loadLatestSummary()
            if (summary != null) {
                call.respond(summary)
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    NewsSummaryResponse(
                        articles = emptyList(),
                        aiSummary = null,
                        fetchedAt = "",
                        totalResults = 0
                    )
                )
            }
        } catch (e: Exception) {
            println("News Latest Error: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                NewsSummaryResponse(
                    articles = emptyList(),
                    aiSummary = "Error: ${e.message}",
                    fetchedAt = "",
                    totalResults = 0
                )
            )
        }
    }
    
    /**
     * POST /api/news/refresh
     * Immediately fetches fresh headlines, generates AI summary, and returns it.
     */
    post("/news/refresh") {
        try {
            val summary = newsScheduler.fetchAndSummarize()
            call.respond(summary)
        } catch (e: McpException) {
            println("News Refresh Error (MCP): ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                NewsSummaryResponse(
                    articles = emptyList(),
                    aiSummary = "Error: Failed to fetch news: ${e.message}",
                    fetchedAt = "",
                    totalResults = 0
                )
            )
        } catch (e: Exception) {
            println("News Refresh Error: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                NewsSummaryResponse(
                    articles = emptyList(),
                    aiSummary = "Error: ${e.message ?: "Unknown error occurred"}",
                    fetchedAt = "",
                    totalResults = 0
                )
            )
        }
    }
    
    /**
     * GET /api/news/search?q=...
     * Searches news articles by query.
     */
    get("/news/search") {
        try {
            val query = call.parameters["q"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                NewsSummaryResponse(
                    articles = emptyList(),
                    aiSummary = "Error: Missing query parameter 'q'",
                    fetchedAt = "",
                    totalResults = 0
                )
            )
            
            val result = newsClient.searchNews(query = query, pageSize = 20)
            
            // Generate a quick summary for search results
            val prompt = buildString {
                appendLine("Summarize the following news search results:")
                appendLine()
                result.articles.take(10).forEachIndexed { index, article ->
                    appendLine("${index + 1}. ${article.title}")
                    if (article.description != null) {
                        appendLine("   ${article.description}")
                    }
                }
                appendLine()
                appendLine("Provide a brief summary of the main themes.")
            }
            
            val aiSummary = try {
                geminiClient.generate(prompt)
            } catch (e: Exception) {
                null
            }
            
            val response = NewsSummaryResponse(
                articles = result.articles,
                aiSummary = aiSummary,
                fetchedAt = result.fetchedAt,
                totalResults = result.totalResults
            )
            
            call.respond(response)
        } catch (e: McpException) {
            println("News Search Error (MCP): ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                NewsSummaryResponse(
                    articles = emptyList(),
                    aiSummary = "Error: Failed to search news: ${e.message}",
                    fetchedAt = "",
                    totalResults = 0
                )
            )
        } catch (e: Exception) {
            println("News Search Error: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                NewsSummaryResponse(
                    articles = emptyList(),
                    aiSummary = "Error: ${e.message ?: "Unknown error occurred"}",
                    fetchedAt = "",
                    totalResults = 0
                )
            )
        }
    }
}

