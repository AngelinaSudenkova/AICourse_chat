package indexing

class TextChunker(
    private val chunkSize: Int = 1000,
    private val chunkOverlap: Int = 200
) {
    fun chunk(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        var start = 0

        while (start < trimmed.length) {
            val end = (start + chunkSize).coerceAtMost(trimmed.length)
            val chunk = trimmed.substring(start, end).trim()

            if (chunk.isNotEmpty()) chunks += chunk

            if (end >= trimmed.length) break

            start = end - chunkOverlap
            if (start < 0) start = 0
        }

        return chunks
    }
}

