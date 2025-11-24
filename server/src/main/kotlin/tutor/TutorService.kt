package tutor

import ai.GeminiClient
import kotlinx.serialization.json.*
import mcp.WikipediaMcpClient
import mcp.YouTubeMcpClient
import mcp.NotionNotesMcpClient
import models.*

class TutorService(
    private val wikipediaClient: WikipediaMcpClient,
    private val youtubeClient: YouTubeMcpClient,
    private val notionClient: NotionNotesMcpClient,
    private val geminiClient: GeminiClient
) {
    suspend fun teach(req: TutorRequest): TutorResponse {
        val topic = req.topic.trim()
        
        // 1. Wikipedia summary
        val wiki = wikipediaClient.getSummary(topic)
        
        // 2. YouTube explanation videos
        val videos = youtubeClient.searchExplainVideos(
            topic = topic,
            maxResults = 5
        )
        
        // 3. Ask Gemini to generate a simple explanation + key points
        val (simpleExplanation, keyPoints) = generateExplanationAndKeyPoints(
            topic = topic,
            wiki = wiki,
            videos = videos,
            level = req.level
        )
        
        // 4. Save structured study note to Notion
        val resources = buildList {
            add(wiki.url)
            videos.forEach { add(it.url) }
        }
        
        val draftNote = TutorStudyNote(
            notionPageId = null,
            title = "${if (topic.isNotEmpty()) topic[0].uppercase() + topic.substring(1) else topic} - Study Note",
            topic = topic,
            keyPoints = keyPoints,
            explanation = simpleExplanation,
            resources = resources
        )
        
        val notionPageId = notionClient.createStudyNote(draftNote)
        val finalNote = draftNote.copy(notionPageId = notionPageId)
        
        return TutorResponse(
            topic = topic,
            wikipedia = wiki,
            videos = videos,
            simpleExplanation = simpleExplanation,
            studyNote = finalNote
        )
    }
    
    private suspend fun generateExplanationAndKeyPoints(
        topic: String,
        wiki: WikipediaSnippet,
        videos: List<YouTubeVideo>,
        level: String?
    ): Pair<String, List<String>> {
        val levelText = level ?: "beginner"
        
        val resourcesText = buildString {
            appendLine("Wikipedia summary:")
            appendLine(wiki.summary)
            appendLine()
            appendLine("YouTube videos:")
            videos.take(3).forEachIndexed { index, v ->
                appendLine("${index + 1}. ${v.title} (${v.url})")
            }
        }
        
        val prompt = """
            You are a friendly learning tutor.
            
            Topic: $topic
            User level: $levelText
            You have the following resources:
            $resourcesText
            
            Task:
            1) Explain this topic in very simple, clear language, tailored to the described level.
            2) Extract 5–10 bullet-point key ideas a learner should remember.
            
            Respond in JSON only, with the following structure:
            {
              "simpleExplanation": "string",
              "keyPoints": ["point1", "point2", ...]
            }
        """.trimIndent()
        
        @kotlinx.serialization.Serializable
        data class ExplanationResult(
            val simpleExplanation: String,
            val keyPoints: List<String>
        )
        
        val schema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("simpleExplanation", buildJsonObject {
                    put("type", "string")
                })
                put("keyPoints", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject {
                        put("type", "string")
                    })
                })
            })
            put("required", buildJsonArray {
                add(JsonPrimitive("simpleExplanation"))
                add(JsonPrimitive("keyPoints"))
            })
        }
        
        val result: ExplanationResult = geminiClient.generateStructured(
            userText = prompt,
            responseSchema = schema
        )
        
        return result.simpleExplanation to result.keyPoints
    }
}

