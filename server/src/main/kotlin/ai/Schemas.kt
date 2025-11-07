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

val JournalResponseSchema: JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        put("status", buildJsonObject { put("type", "string") })
        put("missing", buildJsonObject {
            put("type", "array")
            put("items", buildJsonObject { put("type", "string") })
        })
        put("journal", buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("date", buildJsonObject { put("type", "string") })
                put("title", buildJsonObject { put("type", "string") })
                put("mood", buildJsonObject { put("type", "string") })
                put("moodScore", buildJsonObject { put("type", "integer") })
                put("keyMoments", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject { put("type", "string") })
                })
                put("lessons", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject { put("type", "string") })
                })
                put("gratitude", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject { put("type", "string") })
                })
                put("nextSteps", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject { put("type", "string") })
                })
                put("reflectionSummary", buildJsonObject { put("type", "string") })
            })
            put("required", buildJsonArray {
                add(JsonPrimitive("date"))
                add(JsonPrimitive("title"))
                add(JsonPrimitive("mood"))
                add(JsonPrimitive("moodScore"))
                add(JsonPrimitive("keyMoments"))
                add(JsonPrimitive("lessons"))
                add(JsonPrimitive("gratitude"))
                add(JsonPrimitive("nextSteps"))
                add(JsonPrimitive("reflectionSummary"))
            })
        })
    })
    put("required", buildJsonArray {
        add(JsonPrimitive("status"))
        add(JsonPrimitive("missing"))
    })
}

