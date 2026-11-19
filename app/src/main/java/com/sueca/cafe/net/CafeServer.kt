// O servidor que corre no telemóvel de quem abre a mesa. Mesma API do
// anunciador à parte, mas agora dentro da app: enquanto a mesa estiver aberta,
// este processo distribui cartas, valida jogadas e toca pelos parceiros automáticos.
package com.sueca.cafe.net

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

private const val MAX_BODY = 100_000
private const val WAIT_MS = 1_200L        // o cliente fica suspenso até haver novidades
private const val WAIT_STEP = 80L
private const val TICK_MS = 250L

class CafeServer(val store: RoomStore = RoomStore()) {
    val port = store.port

    @Volatile
    var engine: ApplicationEngine? = null
        private set

    var lastError: Throwable? = null
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    val isRunning get() = engine != null

    fun start() {
        if (engine != null) return
        val e = embeddedServer(CIO, port = port, host = "0.0.0.0") { routes() }
        e.start(wait = false)
        engine = e
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                try {
                    synchronized(store) { store.tickAll(System.currentTimeMillis()) }
                } catch (t: Throwable) {
                    lastError = t
                }
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        val e = engine
        engine = null
        if (e != null) try { e.stop(200, 500) } catch (t: Throwable) { lastError = t }
    }

    private suspend fun io.ktor.server.application.ApplicationCall.readBody(): JsonObject {
        val text = receiveText()
        if (text.length > MAX_BODY) throw IllegalArgumentException("body grande")
        if (text.isBlank()) return EMPTY_JSON
        val el = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("json inválido")
        }
        return el as? JsonObject ?: throw IllegalArgumentException("json inválido")
    }

    private suspend fun io.ktor.server.application.ApplicationCall.reply(http: Http) {
        respondText(
            http.json.toString(),
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.fromValue(http.code),
        )
    }

    // Um pedido malformado responde como respondia o anunciador: 400 com a explicação.
    private suspend fun io.ktor.server.application.ApplicationCall.guarded(
        block: suspend io.ktor.server.application.ApplicationCall.() -> Http,
    ) {
        val out = try {
            block()
        } catch (e: IllegalArgumentException) {
            Http(400, jsonObj("error" to (e.message ?: "pedido inválido")))
        } catch (e: Exception) {
            Http(400, jsonObj("error" to (e.message ?: "pedido inválido")))
        }
        reply(out)
    }

    private fun Application.routes() {
        routing {
            get("/api/info") { call.reply(store.info()) }

            get("/api/rooms") { call.reply(store.listRooms()) }

            post("/api/rooms") {
                call.guarded {
                    val body = readBody()
                    synchronized(store) { store.createRoom(body, System.currentTimeMillis()) }
                }
            }

            route("/api/rooms/{id}") {
                post("/join") {
                    call.guarded {
                        val id = call.parameters["id"]
                        val body = readBody()
                        synchronized(store) { store.join(id, body, System.currentTimeMillis()) }
                    }
                }

                get("/state") {
                    val id = call.parameters["id"]
                    val seat = call.request.queryParameters["seat"]?.toIntOrNull()
                    val tk = call.request.queryParameters["tk"]
                    val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                    val deadline = System.currentTimeMillis() + if (since > 0) WAIT_MS else 0
                    while (true) {
                        val res = synchronized(store) {
                            store.state(id, seat, tk, since, System.currentTimeMillis())
                        }
                        val events = res.json["events"] as? JsonArray
                        if (res.code != 200 || (events != null && events.isNotEmpty()) ||
                            System.currentTimeMillis() >= deadline
                        ) {
                            call.reply(res)
                            break
                        }
                        delay(WAIT_STEP)
                    }
                }

                post("/action") {
                    call.guarded {
                        val id = call.parameters["id"]
                        val body = readBody()
                        synchronized(store) { store.act(id, body, System.currentTimeMillis()) }
                    }
                }
            }
        }
    }
}
