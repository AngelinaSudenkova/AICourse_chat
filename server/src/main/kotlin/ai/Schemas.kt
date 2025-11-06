package ai

import kotlinx.serialization.json.*

val ReadingSummarySchema: JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        put("title", buildJsonObject { put("type", "string") })
        put("theSourceOfTheText", buildJsonObject { put("type", "string") })
        put("timeOfReading", buildJsonObject { put("type", "string") })
        put("summary", buildJsonObject { put("type", "string") })
    })
    put("required", buildJsonArray {
        add(JsonPrimitive("title"))
        add(JsonPrimitive("theSourceOfTheText"))
        add(JsonPrimitive("timeOfReading"))
        add(JsonPrimitive("summary"))
    })
    // Note: additionalProperties is not supported by Gemini API
}

