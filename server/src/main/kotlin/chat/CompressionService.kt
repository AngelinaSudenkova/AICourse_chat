package chat

import ai.GeminiClient
import models.*
import java.util.UUID
import kotlin.math.roundToInt

class CompressionService(
    private val llm: GeminiClient,
    private val segmentWindowSize: Int = 10
) {
    private fun approxTokens(s: String): Int = if (s.isBlank()) 0 else (s.length / 4.0).roundToInt().coerceAtLeast(1)

    suspend fun maybeSummarize(state: ConversationState): Pair<ConversationState, Boolean> {
        val open = state.segments.firstOrNull { it.id == state.openSegmentId }
            ?: return state to false // No open segment found, return as-is

        // Only summarize if segment has no summary yet and has reached window size
        if (open.summary == null && open.messages.size >= segmentWindowSize) {
            val summary = summarize(open.messages)
            val summarized = open.copy(summary = summary, messages = emptyList())
            val newOpen = ConversationSegment(
                id = UUID.randomUUID().toString(),
                summary = null,
                messages = emptyList()
            )
            val newSegments = state.segments.map { if (it.id == open.id) summarized else it } + newOpen
            return state.copy(segments = newSegments, openSegmentId = newOpen.id) to true
        }
        return state to false
    }

    suspend fun forceSummarize(state: ConversationState): Pair<ConversationState, Boolean> {
        val open = state.segments.firstOrNull { it.id == state.openSegmentId }
            ?: return state to false

        // Force summarize even if below window size (but only if there are messages)
        if (open.summary == null && open.messages.isNotEmpty()) {
            val summary = summarize(open.messages)
            val summarized = open.copy(summary = summary, messages = emptyList())
            val newOpen = ConversationSegment(
                id = UUID.randomUUID().toString(),
                summary = null,
                messages = emptyList()
            )
            val newSegments = state.segments.map { if (it.id == open.id) summarized else it } + newOpen
            return state.copy(segments = newSegments, openSegmentId = newOpen.id) to true
        }
        return state to false
    }

    private suspend fun summarize(msgs: List<ChatMessage>): String {
        val transcript = msgs.joinToString("\n") { "${it.role}: ${it.content}" }
        val prompt = """
          You are a dialogue compressor. Summarize the conversation faithfully and concisely.
          Preserve: user goals, decisions, constraints, facts, numbers, action items, tool results, unresolved questions.
          Remove fluff and phatic language. Keep <300 words. Use bullet points if helpful.

          Transcript:

          $transcript

          Output: a single compact summary paragraph(s) or bullets, no metadata.
        """.trimIndent()

        return llm.generate(prompt)
    }

    fun buildContext(state: ConversationState): String {
        val open = state.segments.firstOrNull { it.id == state.openSegmentId }
        val openMessages = open?.messages ?: emptyList()
        
        // If open segment has no summary yet (first 5 messages), send ALL messages from all segments
        val allSegmentsWithMessages = state.segments.filter { it.messages.isNotEmpty() || it.summary != null }
        
        // Check if we're still in the first segment (no summaries exist yet)
        val hasSummaries = state.segments.any { it.summary != null }
        
        val sb = StringBuilder()
        
        if (!hasSummaries) {
            // First 5 messages: send all raw messages
            val allMessages = allSegmentsWithMessages.flatMap { it.messages }
            allMessages.forEach { sb.appendLine("${it.role}: ${it.content}") }
        } else {
            // After first summary: send summaries + current segment messages
            val summaries = state.segments.filter { it.summary != null && it.id != state.openSegmentId }
            if (summaries.isNotEmpty()) {
                sb.appendLine("Conversation summaries (old → new):")
                summaries.forEachIndexed { i, seg -> 
                    sb.appendLine("S${i+1}: ${seg.summary}")
                }
                sb.appendLine()
            }
            if (openMessages.isNotEmpty()) {
                sb.appendLine("Recent turns:")
                openMessages.forEach { sb.appendLine("${it.role}: ${it.content}") }
            }
        }
        
        return sb.toString().ifBlank { "No prior context." }
    }
    
    // Calculate what tokens would be if we sent ALL raw messages (no compression)
    fun measureTokensIfNoCompression(state: ConversationState): Int {
        val allMessages = state.segments.flatMap { it.messages }
        return measureTokensRaw(allMessages)
    }

    fun measureTokensRaw(allMsgs: List<ChatMessage>): Int =
        approxTokens(allMsgs.joinToString("\n") { "${it.role}: ${it.content}" })

    fun measureTokensCompressed(context: String): Int = approxTokens(context)
}

