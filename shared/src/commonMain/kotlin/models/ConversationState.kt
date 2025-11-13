package models

import kotlinx.serialization.Serializable

@Serializable
data class ConversationState(
    val conversationId: String,
    val segments: List<ConversationSegment>,   // committed summaries + open segment
    val openSegmentId: String                  // current segment collecting raw turns
)

