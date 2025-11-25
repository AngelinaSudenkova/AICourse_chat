package indexing

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.request.setBody
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class OllamaEmbeddingRequest(
    val model: String,
    val prompt: String
)

@Serializable
private data class OllamaEmbeddingResponse(
    val embedding: List<Double>
)

class OllamaEmbeddingsClient(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "all-minilm" // or "nomic-embed-text"
) : EmbeddingsClient {

    private val client = HttpClient(CIO) {
        expectSuccess = false
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun embed(texts: List<String>): List<List<Double>> {
        if (texts.isEmpty()) return emptyList()

        // Simpler: one request per text
        return texts.map { text ->
            try {
                val payload = OllamaEmbeddingRequest(model = model, prompt = text)

                val res: HttpResponse = client.post("$baseUrl/api/embeddings") {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(OllamaEmbeddingRequest.serializer(), payload))
                }

                val body = res.bodyAsText()

                if (!res.status.isSuccess()) {
                    println("Ollama embeddings error ${res.status.value}: $body")
                    return@map emptyList<Double>()
                }

                val parsed = json.decodeFromString<OllamaEmbeddingResponse>(body)
                parsed.embedding
            } catch (e: Exception) {
                println("Error generating embedding: ${e.message}")
                e.printStackTrace()
                emptyList<Double>()
            }
        }
    }
}

