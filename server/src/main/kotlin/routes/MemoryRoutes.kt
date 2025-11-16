package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import memory.MemoryStore
import org.koin.core.context.GlobalContext

fun Route.memoryRoutes() {
    route("/memory") {
        get("/{conversationId}") {
            val conversationId = call.parameters["conversationId"]
                ?: return@get call.respondText(
                    "Missing id",
                    status = HttpStatusCode.BadRequest
                )

            val koin = GlobalContext.get()
            val store: MemoryStore = koin.get()

            val list = store.list(conversationId)
            call.respond(list)
        }
    }
}


