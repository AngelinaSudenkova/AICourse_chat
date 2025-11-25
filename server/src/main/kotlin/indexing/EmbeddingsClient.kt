package indexing

interface EmbeddingsClient {
    suspend fun embed(texts: List<String>): List<List<Double>>
}

