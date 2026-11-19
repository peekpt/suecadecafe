// O caminho de entrada do jogo: host e convidado falam os dois por HTTP, o
// anfitrião para 127.0.0.1 e o convidado para o endereço do anfitrião. Assim há
// uma única forma de a interface pedir, seja quem for que abriu a mesa.
package com.sueca.cafe.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

const val LOOPBACK = "http://127.0.0.1"

// Aceita "192.168.1.20", "192.168.1.20:8743" ou "http://192.168.1.20:8743".
fun normalizeBase(input: String, port: Int = PORT): String {
    val t = input.trim().trimEnd('/')
    if (t.isEmpty()) return ""
    val withScheme = if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
    val host = withScheme.substringAfter("://")
    if (host.isEmpty() || host.contains('/') || host.contains(' ')) return ""
    return (if (host.contains(':')) withScheme else "$withScheme:$port").trimEnd('/')
}

class TableClient(address: String) {
    private val base = normalizeBase(address).ifEmpty { LOOPBACK }
    private val http = HttpClient(CIO) { engine { requestTimeout = 25_000 } }

    val root: String get() = base

    private suspend fun ask(
        path: String,
        body: JsonObject? = null,
        query: String = "",
    ): Http {
        val url = base + path + query
        return try {
            val resp = when {
                body != null -> http.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
                else -> http.get(url)
            }
            val json = Json.parseToJsonElement(resp.bodyAsText()) as? JsonObject ?: EMPTY_JSON
            Http(resp.status.value, json)
        } catch (e: Exception) {
            Http(0, jsonObj("error" to (e.message ?: "sem ligação")))
        }
    }

    suspend fun info() = ask("/api/info")

    suspend fun rooms() = ask("/api/rooms")

    suspend fun create(name: String, player: String, avatar: String) = ask(
        "/api/rooms",
        jsonObj("name" to name, "player" to jsonObj("name" to player, "avatar" to avatar)),
    )

    suspend fun join(id: String, player: String, avatar: String, team: String? = null) =
        ask("/api/rooms/$id/join", jsonObj("name" to player, "avatar" to avatar, "team" to team))

    // O servidor segura o pedido até haver novidades, por isso a interface não
    // tem de andar a perguntar a toda a hora.
    suspend fun state(id: String, seat: Int, tk: String, since: Long) =
        ask("/api/rooms/$id/state", query = "?seat=$seat&tk=$tk&since=$since")

    suspend fun act(id: String, seat: Int, tk: String, vararg fields: Pair<String, Any?>) =
        ask("/api/rooms/$id/action", jsonObj(*fields, *arrayOf("seat" to seat, "tk" to tk)))

    fun close() {
        try {
            http.close()
        } catch (e: Exception) {
        }
    }
}
