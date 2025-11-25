package routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext
import wiki.WikiFetcher
import wiki.WikiIndexer
import wiki.WikiSearcher
import models.*

fun Route.wikiRoutes() {
    route("/wiki") {
        post("/fetch") {
            try {
                val koin = GlobalContext.get()
                val fetcher: WikiFetcher = koin.get()
                val req = call.receive<WikiFetchRequest>()
                val meta = fetcher.fetchAndSave(req.topic)
                call.respond(meta)
            } catch (e: Exception) {
                println("Wiki fetch error: ${e.message}")
                e.printStackTrace()
                call.respond(
                    io.ktor.http.HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }

        post("/index") {
            try {
                val koin = GlobalContext.get()
                val indexer: WikiIndexer = koin.get()
                val req = call.receive<WikiIndexRequest>()
                val index = if (req.topics.isNotEmpty()) {
                    indexer.buildIndexForTopics(req.topics)
                } else {
                    indexer.buildIndexFromLocal()
                }
                call.respond(index)
            } catch (e: Exception) {
                println("Wiki index error: ${e.message}")
                e.printStackTrace()
                call.respond(
                    io.ktor.http.HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }

        post("/search") {
            try {
                val koin = GlobalContext.get()
                val indexer: WikiIndexer = koin.get()
                val searcher: WikiSearcher = koin.get()
                val req = call.receive<WikiSearchRequest>()
                val index = indexer.loadIndex()

                if (index == null) {
                    call.respond(
                        io.ktor.http.HttpStatusCode.BadRequest,
                        mapOf("error" to "Wiki index not found. Fetch & index first.")
                    )
                    return@post
                }

                val resp = searcher.search(index, req)
                call.respond(resp)
            } catch (e: Exception) {
                println("Wiki search error: ${e.message}")
                e.printStackTrace()
                call.respond(
                    io.ktor.http.HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }

        get("/index") {
            try {
                val koin = GlobalContext.get()
                val indexer: WikiIndexer = koin.get()
                val index = indexer.loadIndex()

                if (index == null) {
                    call.respond(
                        io.ktor.http.HttpStatusCode.NotFound,
                        mapOf("error" to "Wiki index not found")
                    )
                } else {
                    call.respond(index)
                }
            } catch (e: Exception) {
                println("Wiki get index error: ${e.message}")
                e.printStackTrace()
                call.respond(
                    io.ktor.http.HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }
    }
}

