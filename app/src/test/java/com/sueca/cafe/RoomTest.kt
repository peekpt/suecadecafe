// Testes da mesa: lugares, credenciais, ações e o relógio que faz os parceiros
// automáticos jogarem. Uso: ./gradlew :app:testDebugUnitTest
package com.sueca.cafe

import com.sueca.cafe.net.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

private fun JsonObject.at(vararg path: String): JsonElement {
    var el: JsonElement = this
    for (p in path) el = (el as? JsonObject)?.get(p) ?: return JsonNull
    return el
}

private fun JsonObject.str(key: String): String? {
    val p = this[key] as? JsonPrimitive ?: return null
    return if (p.isString) p.content else null
}

private fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()

private fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()

private fun JsonElement.obj(): JsonObject = this as JsonObject
private fun JsonElement.ints(): List<Int> = jsonArray.map { it.jsonPrimitive.content.toInt() }
private fun JsonObject.t(): String? = str("t")

private class Env(var now: Long = 10_000L) {
    val store = RoomStore().apply { rng = { 0.5 } }
    var id: String = ""
    var tk: String = ""
    val tokens = mutableMapOf<Int, String>()

    fun create(name: String = "Mesa do café", player: String = "Ana"): JsonObject {
        val res = store.createRoom(jsonObj("name" to name, "player" to jsonObj("name" to player)), now)
        assertEquals(200, res.code)
        id = res.json.str("id")!!
        tk = res.json.str("tk")!!
        tokens[0] = tk
        return res.json
    }

    fun room(): Room {
        if (id.isEmpty()) create()
        return store.get(id)!!
    }

    fun join(name: String, team: String): Int {
        if (id.isEmpty()) create()
        val res = store.join(id, jsonObj("name" to name, "team" to team, "avatar" to "d0:2"), now)
        assertEquals(200, res.code)
        val seat = res.json.int("seat")!!
        tokens[seat] = res.json.str("tk")!!
        return seat
    }

    fun view(seat: Int, since: Long = 0, token: String? = tokens[seat]): Http =
        store.state(id, seat, token, since, now)

    fun started(): Room {
        val room = room()
        assertTrue(room.action(0, jsonObj("type" to "start"), now).error == null)
        return room
    }

    fun allBots(room: Room) {
        for (s in room.seats) s!!.bot = true
    }

    fun playOut(room: Room, guard: Int = 4000) {
        var n = 0
        while (!room.hand!!.over && n++ < guard) { now += 2_000; store.tickAll(now) }
        assertTrue("a mão fechou", room.hand!!.over)
    }

    fun untilTricks(room: Room, n: Int, guard: Int = 600): Boolean {
        var g = 0
        while (room.hand!!.history.size < n && g++ < guard) { now += 2_000; room.tick(now) }
        return room.hand!!.history.size >= n
    }
}

class RoomTest {
    @Test
    fun criarMesaDaOLugarEumTokenAoAnfitiao() {
        val e = Env()
        val out = e.create()
        val room = e.room()
        assertEquals(0, out.int("seat"))
        assertEquals(4, out.str("id")!!.length)
        assertEquals(8, out.str("tk")!!.length)
        assertEquals("lobby", room.status)
        assertEquals(0, room.host)
        assertEquals("Ana", room.seats[0]!!.name)
        assertEquals("cafe", room.seats[0]!!.avatar)
        assertEquals(out.str("tk"), room.seats[0]!!.tk)
        assertFalse(out.at("room").obj().toString().contains("\"tk\""))
        assertEquals(1, e.store.openRooms.size)
    }

    @Test
    fun nomeLimpoESemJogoAteComecar() {
        val e = Env()
        e.create(name = "  Mesa    da   varanda  ")
        val room = e.room()
        assertEquals("Mesa da varanda", room.name)
        assertEquals(Rules(), room.rules)
        assertTrue(room.fillBots)
        assertEquals(0, room.handNo)
        assertNull(room.hand)
        assertTrue(room.humanOnline())
        val v = e.view(0).json.at("view").obj()
        assertNull(v["game"])
        assertEquals("sueca-no-cafe", v.str("app"))
        assertEquals(e.id, v.str("id"))
        assertEquals("lobby", v.str("status"))
        assertEquals(0, v.int("host"))
        assertEquals(4, v["seats"]!!.jsonArray.size)
        assertTrue(v["seats"]!!.jsonArray[3] is JsonNull)
        assertEquals("Ana", v.at("you", "name").jsonPrimitive.content)
    }

    @Test
    fun lugaresPorEquipaEoLugarCheio() {
        val e = Env()
        e.create()
        assertEquals(1, e.join("Bruno", "b"))
        assertEquals(2, e.join("Carla", "a"))
        assertEquals(3, e.join("Duarte", "b"))
        val cheio = e.store.join(e.id, jsonObj("name" to "Ema", "team" to "a"), e.now)
        assertEquals(409, cheio.code)
        assertEquals("Essa equipa já tem os dois jogadores.", cheio.json.str("error"))
        val room = e.room()
        assertEquals(listOf("a", "b", "a", "b"), room.seats.map { it!!.team })
        assertEquals("d0:2", room.seats[1]!!.avatar)
        val public = e.store.listRooms().json["rooms"]!!.jsonArray
            .first { it.obj().str("id") == e.id }.obj()
        assertEquals(4, public.int("taken"))
        assertEquals("Mesa do café", public.str("name"))
        assertEquals("lobby", public.str("status"))
    }

    @Test
    fun aVistaSoMostraAMaoDeQuemPergunta() {
        val e = Env()
        val room = e.started()
        val g = room.hand!!
        val view = e.view(0).json.at("view").obj()
        assertEquals(10, view.at("game", "yourHand").jsonArray.size)
        assertEquals(listOf(10, 10, 10, 10), view.at("game", "left").ints())
        assertTrue(view.at("game", "legal").jsonArray.isEmpty())   // abre quem está à direita de quem dá
        assertEquals(g.trump, view.at("game", "trump").jsonPrimitive.content)
        val seats = view["seats"]!!.jsonArray
        assertEquals(10, seats[1].obj().int("cards"))
        assertTrue(seats[2].obj().bool("bot")!!)
        assertFalse(seats[0].obj().bool("bot")!!)
        assertTrue(seats[0].obj().bool("host")!!)
        // a mão dos outros não aparece no JSON
        for (s in 1..3)
            for (card in g.hands[s])
                assertFalse("a mão de $s saiu na vista", view.toString().contains("\"$card\""))
    }

    @Test
    fun jogadasLegaisSoQuandoEaVez() {
        val e = Env()
        val room = e.started()
        val g = room.hand!!
        g.turn = 0
        assertEquals(10, legalPlays(g, 0).size)
        val view = e.view(0).json.at("view", "game").obj()
        assertEquals(10, view["legal"]!!.jsonArray.size)
        assertTrue(view.bool("yourTurn")!!)
    }

    @Test
    fun semTokenCertoNaoHaNada() {
        val e = Env()
        val seatB = e.join("Bruno", "b")
        // lugar ainda vazio: não há vista possível
        assertEquals(409, e.store.state(e.id, 3, null, 0, e.now).code)
        assertEquals("Saíste da mesa.", e.store.state(e.id, 3, null, 0, e.now).json.str("error"))
        e.started()
        assertEquals(403, e.store.state(e.id, 0, "lixo", 0, e.now).code)
        assertEquals("Credencial errada.", e.store.state(e.id, 0, "lixo", 0, e.now).json.str("error"))
        assertEquals(403, e.store.state(e.id, 0, null, 0, e.now).code)
        assertEquals(403, e.store.act(e.id, jsonObj("seat" to 0, "tk" to "lixo", "type" to "ready"), e.now).code)
        assertEquals(404, e.store.state("ZZZZ", 0, e.tk, 0, e.now).code)
        assertEquals(404, e.store.join("ZZZZ", jsonObj("name" to "X"), e.now).code)
        // os lugares automáticos não têm credencial: qualquer um vê o que jogam
        assertTrue(e.room().seats[2]!!.bot)
        assertEquals(200, e.store.state(e.id, 2, null, 0, e.now).code)
        assertEquals(403, e.store.state(e.id, seatB, null, 0, e.now).code)
    }

    @Test
    fun comAutomaticosBastaUmJogadorParaComecar() {
        val e = Env()
        val room = e.started()
        assertEquals("playing", room.status)
        assertEquals(1, room.handNo)
        assertEquals(4, room.seats.count { it != null })
        assertEquals(3, room.seats.count { it!!.bot })
        assertEquals("d0:0", room.seats[1]!!.avatar)
        assertEquals("Parceiro 3", room.seats[2]!!.name)
        val g = room.hand!!
        assertEquals(10, g.hands[0].size)
        assertEquals(suitOf(g.turned!!), g.trump)
        assertEquals(0, g.turnedSeat)
        assertTrue(g.turnedLive)
        assertTrue(g.hands[room.dealer].contains(g.turned))    // a última carta fica com quem dá
        assertEquals(nextSeat(room.dealer), g.leader)
        assertEquals(1, room.feed.count { it.t() == "deal" })
        assertEquals(g.trump, room.feed.last { it.t() == "deal" }.str("trump"))
    }

    @Test
    fun semAutomaticosFaltamJogadores() {
        val e = Env()
        val room = e.room()
        room.action(0, jsonObj("type" to "rules", "key" to "fillBots", "value" to false), e.now)
        assertFalse(room.fillBots)
        assertEquals("Faltam jogadores para as duas equipas.",
            room.action(0, jsonObj("type" to "start"), e.now).error)
        assertEquals("lobby", room.status)
        e.join("Bruno", "b"); e.join("Carla", "a"); e.join("Duarte", "b")
        assertTrue(room.action(0, jsonObj("type" to "start"), e.now).error == null)
        assertEquals(4, room.seats.filterNotNull().count { !it.bot })
        assertEquals(0, room.seats.filterNotNull().count { it.bot })
    }

    @Test
    fun regrasSdoAnfitiaoEAntesDeComecar() {
        val e = Env()
        val room = e.room()
        e.join("Bruno", "b")
        assertEquals("Só o anfitrião muda as regras.",
            room.action(1, jsonObj("type" to "rules", "key" to "subirSempre", "value" to true), e.now).error)
        val up = room.action(0, jsonObj("type" to "rules", "key" to "subirSempre", "value" to true), e.now)
        assertTrue(up.error == null)
        assertTrue(room.rules.subirSempre)
        assertEquals(true, up.fields.at("rules", "subirSempre").jsonPrimitive.content.toBooleanStrictOrNull())
        room.action(0, jsonObj("type" to "rules", "key" to "jogosParaGanhar", "value" to 6), e.now)
        assertEquals(6, room.rules.jogosParaGanhar)
        room.action(0, jsonObj("type" to "rules", "key" to "jogosParaGanhar", "value" to 7), e.now)
        assertEquals(4, room.rules.jogosParaGanhar)
        room.action(0, jsonObj("type" to "rules", "key" to "virarTrunfo", "value" to false), e.now)
        assertFalse(room.rules.virarTrunfo)
        assertEquals("regra desconhecida", room.action(0, jsonObj("type" to "rules", "key" to "trapaça"), e.now).error)
        room.action(0, jsonObj("type" to "start"), e.now)
        assertEquals("As regras só se combinam antes de começar.",
            room.action(0, jsonObj("type" to "rules", "key" to "subirSempre", "value" to true), e.now).error)
        // e valem para a mão que foi dada
        assertFalse(room.hand!!.rules.virarTrunfo)
        assertNull(room.hand!!.turned)
        assertFalse(room.hand!!.turnedLive)
        assertTrue(room.feed.any { it.t() == "rules" })
    }

    @Test
    fun oRelogioPercorreAmaoInteira() {
        val e = Env()
        val room = e.started()
        e.allBots(room)
        e.playOut(room)
        val g = room.hand!!
        assertEquals(10, g.history.size)
        assertEquals(TOTAL_POINTS, g.points.a + g.points.b)
        assertEquals(10, g.tricks.a + g.tricks.b)
        assertEquals(0, g.hands.sumOf { it.size })
        assertNotNull(g.result)
        assertEquals(1, room.feed.count { it.t() == "hand" })
        assertTrue(room.nextHandAt > room.startedAt)
        assertEquals(40, room.feed.count { it.t() == "play" })
        val handEv = room.feed.last { it.t() == "hand" }
        assertEquals(g.points.a, handEv.at("result", "points", "a").jsonPrimitive.content.toInt())
        assertEquals(10, handEv.at("result", "tricks", "a").jsonPrimitive.content.toInt() +
            handEv.at("result", "tricks", "b").jsonPrimitive.content.toInt())
    }

    @Test
    fun jogarSoOQuePodeQuandoPode() {
        val e = Env()
        val room = e.started()
        val g = room.hand!!
        val bot = (1..3).first { room.seats[it]!!.bot }
        assertEquals("Esse lugar é automático.",
            room.action(bot, jsonObj("type" to "play", "card" to g.hands[bot][0]), e.now).error)
        // passa a vez ao humano, a abrir a vaza
        g.turn = 0
        val estranha = (makeDeck() - g.hands[0].toSet()).first()
        assertEquals("Essa carta não está na tua mão.",
            room.action(0, jsonObj("type" to "play", "card" to estranha), e.now).error)
        val legal = legalPlays(g, 0)
        assertEquals(10, legal.size)
        assertNull(room.action(0, jsonObj("type" to "play", "card" to legal[0]), e.now).error)
        assertEquals("Ainda não é a tua vez.",
            room.action(0, jsonObj("type" to "play", "card" to g.hands[0].first()), e.now).error)
        // assistir ao naipe de saída
        val mao = g.hands[0].toList()
        val naipes = mao.map { suitOf(it) }.distinct()
        assertTrue("o humano tem mais de um naipe", naipes.size > 1)
        g.trick.clear(); g.trick.add(Play(3, mao.first { suitOf(it) == naipes[1] })); g.turn = 0
        val wrong = mao.first { suitOf(it) != naipes[1] }
        assertEquals("Tens de assistir ao naipe de saída.",
            room.action(0, jsonObj("type" to "play", "card" to wrong), e.now).error)
        assertNull(room.action(0, jsonObj("type" to "play", "card" to mao.first { suitOf(it) == naipes[1] }), e.now).error)
        assertEquals(2, room.feed.count { it.t() == "play" })
    }

    @Test
    fun aCartaViradaSaiDaMostraNaTerceiraVaza() {
        val e = Env()
        val room = e.started()
        e.allBots(room)
        assertTrue(e.untilTricks(room, 3))
        assertFalse(room.hand!!.turnedLive)
        assertTrue(room.feed.any { it.t() == "play" })
        assertEquals(room.hand!!.trump, room.feed.last { it.t() == "play" }.str("trump"))
    }

    @Test
    fun sairDaSalaDeEsperaLibertaOLugar() {
        val e = Env()
        val room = e.room()
        val seatB = e.join("Bruno", "b")
        assertNull(room.action(seatB, jsonObj("type" to "leave"), e.now).error)
        assertNull(room.seats[seatB])
        assertEquals("lobby", room.status)
        room.action(0, jsonObj("type" to "leave"), e.now)
        assertEquals("closed", room.status)
        assertEquals(404, e.store.state(e.id, 0, e.tk, 0, e.now).code)
        assertTrue(e.store.listRooms().json["rooms"]!!.jsonArray.none { it.obj().str("id") == e.id })
    }

    @Test
    fun emJogoQuemSaiDeixaACadeiraGuardadaEVolta() {
        val e = Env()
        val room = e.room()
        val seatB = e.join("Bruno", "b")
        val tkB = e.tokens[seatB]!!
        e.started()
        val cartas = room.hand!!.hands[seatB].size
        assertNull(room.action(seatB, jsonObj("type" to "leave"), e.now).error)
        val s = room.seats[seatB]!!
        assertTrue(s.bot)
        assertTrue(s.away)
        assertFalse(s.online)
        assertEquals(cartas, room.hand!!.hands[seatB].size)
        assertEquals(tkB, s.tk)
        // o mesmo token volta a tomar o lugar
        val st = e.store.state(e.id, seatB, tkB, 0, e.now)
        assertEquals(200, st.code)
        assertTrue(st.json["events"]!!.jsonArray.any { it.obj().t() == "back" })
        assertFalse(room.seats[seatB]!!.bot)
        assertTrue(room.seats[seatB]!!.online)
        assertFalse(room.seats[seatB]!!.away)
    }

    @Test
    fun outroTomaaCadeiraGuardadaEoAntigoPerdeOAcesso() {
        val e = Env()
        val room = e.room()
        val seatB = e.join("Bruno", "b")
        val tkB = e.tokens[seatB]!!
        e.started()
        room.action(seatB, jsonObj("type" to "leave"), e.now)
        val novo = e.store.join(e.id, jsonObj("name" to "Quem chega", "team" to "b"), e.now)
        assertEquals(200, novo.code)
        assertEquals(seatB, novo.json.int("seat"))
        assertNotEquals(tkB, room.seats[seatB]!!.tk)
        assertEquals("Quem chega", room.seats[seatB]!!.name)
        assertTrue(room.seats[seatB]!!.ready)
        assertFalse(room.seats[seatB]!!.bot)
        assertEquals(403, e.store.state(e.id, seatB, tkB, 0, e.now).code)
        // mesa a jogar sem cadeira guardada: não há lugar
        room.seats[seatB]!!.away = false
        assertEquals(409, e.store.join(e.id, jsonObj("name" to "Atabalhoada"), e.now).code)
    }

    @Test
    fun oRelogioTapaQuemDesapareceaMeioDoJogo() {
        val e = Env()
        val room = e.started()
        room.seats[0]!!.lastSeen = e.now
        e.now += GAME_TTL + 1
        e.store.tickAll(e.now)
        assertTrue(room.seats[0]!!.bot)
        assertTrue(room.seats[0]!!.away)
        assertEquals(0, room.host)              // não havia outro humano para receber a coroa
        assertTrue(room.feed.any { it.t() == "gone" && it.bool("timeout")!! })
        assertTrue(room.feed.any { it.t() == "botIn" })
        assertEquals(200, e.store.state(e.id, 0, e.tk, 0, e.now).code)
        assertFalse(room.seats[0]!!.bot)
    }

    @Test
    fun aSalaDeEsperaLibertaQuemTarda() {
        val e = Env()
        val room = e.room()
        val seatB = e.join("Bruno", "b")
        room.seats[seatB]!!.lastSeen = e.now
        e.now += LOBBY_TTL + 1
        room.seats[0]!!.lastSeen = e.now      // a anfitriã continua a dar sinais
        e.store.tickAll(e.now)
        assertNull(room.seats[seatB])
        assertEquals("lobby", room.status)
        assertTrue(room.humanOnline())
        // sem humanos nenhuns a mesa morre
        room.seats[0]!!.lastSeen = e.now
        e.now += LOBBY_TTL + 1
        e.store.tickAll(e.now)
        assertNull(room.seats[0])
        assertEquals("closed", room.status)
    }

    @Test
    fun aCoroaPassaQuandoOAnfitiaoDesaparece() {
        val e = Env()
        val room = e.room()
        val seatB = e.join("Bruno", "b")
        e.started()
        room.seats[0]!!.lastSeen = e.now
        e.now += GAME_TTL + 1
        e.store.tickAll(e.now)
        assertEquals(seatB, room.host)
        val ev = room.feed.last { it.t() == "host" }
        assertEquals(seatB, ev.int("seat"))
        assertEquals("Bruno", ev.str("name"))
    }

    @Test
    fun mudarDeEquipaTrocaOLugar() {
        val e = Env()
        val room = e.room()
        val seatB = e.join("Bruno", "b")
        val tkB = e.tokens[seatB]!!
        val res = room.action(seatB, jsonObj("type" to "team", "value" to "a"), e.now)
        assertTrue(res.error == null)
        assertEquals(2, res.seat)
        assertNull(room.seats[seatB])
        assertEquals("a", room.seats[2]!!.team)
        assertEquals(tkB, room.seats[2]!!.tk)
        assertEquals(0, room.host)
        val http = e.store.act(e.id,
            jsonObj("seat" to 2, "tk" to tkB, "type" to "team", "value" to "b"), e.now)
        assertEquals(200, http.code)
        assertEquals(1, http.json.int("seat"))
        assertEquals(1, http.json.at("view", "seat").jsonPrimitive.content.toInt())
        room.action(0, jsonObj("type" to "start"), e.now)
        assertEquals("A partida já começou.", room.action(1, jsonObj("type" to "team", "value" to "b"), e.now).error)
    }

    @Test
    fun estarDentroEConversas() {
        val e = Env()
        val room = e.room()
        val seatB = e.join("Bruno", "b")
        assertTrue(room.action(seatB, jsonObj("type" to "ready", "value" to true), e.now).error == null)
        assertTrue(room.seats[seatB]!!.ready)
        assertEquals("Tira o \"estou dentro\" primeiro.",
            room.action(seatB, jsonObj("type" to "team", "value" to "a"), e.now).error)
        assertTrue(room.action(seatB, jsonObj("type" to "ready"), e.now).error == null)
        assertFalse(room.seats[seatB]!!.ready)        // sem valor, é alternar
        assertEquals("vazio", room.action(seatB, jsonObj("type" to "chat", "text" to "   "), e.now).error)
        room.action(seatB, jsonObj("type" to "chat", "text" to "  olá   mundo  "), e.now)
        val ev = room.feed.last { it.t() == "chat" }
        assertEquals("olá mundo", ev.str("text"))
        assertEquals(seatB, ev.int("seat"))
        room.action(seatB, jsonObj("type" to "emote", "text" to "cafe"), e.now)
        assertEquals("emote", room.feed.last().t())
    }

    @Test
    fun maoSeguintePorAcaoRodaODador() {
        val e = Env()
        val room = e.started()
        room.rules.jogosParaGanhar = 20            // uma mão nunca fecha a partida
        e.allBots(room)
        e.playOut(room)
        val dealer = room.dealer
        assertEquals(1, room.handNo)
        assertTrue(room.action(0, jsonObj("type" to "next"), e.now).error == null)
        assertEquals(2, room.handNo)
        assertEquals((dealer + 1) % 4, room.dealer)
        assertFalse(room.hand!!.over)
        assertEquals("Ainda não acabaste esta mão.",
            room.action(0, jsonObj("type" to "next"), e.now).error)
        // o relógio não volta a dar nada a meio da mão nova
        e.now += NEXT_HAND_MS + 5
        e.store.tickAll(e.now)
        assertEquals(2, room.handNo)
    }

    @Test
    fun oRelogioDaaMaoSeguinteAposAPausa() {
        val e = Env()
        val room = e.started()
        room.rules.jogosParaGanhar = 20
        e.allBots(room)
        e.playOut(room)
        val dealer = room.dealer
        e.now += NEXT_HAND_MS + 1
        e.store.tickAll(e.now)
        assertEquals(2, room.handNo)
        assertEquals((dealer + 1) % 4, room.dealer)
        assertEquals(0L, room.nextHandAt)
        assertEquals(2, room.feed.count { it.t() == "deal" })
    }

    @Test
    fun bandeiraFechaAPartidaERematchRecomea() {
        val e = Env()
        val room = e.started()
        val g = room.hand!!
        g.over = true
        g.result = scoreHand(TOTAL_POINTS, 0, room.rules, 10, 0)
        room.settleHand(e.now)
        assertEquals("over", room.status)
        assertEquals("a", room.match.winner)
        assertEquals("bandeira", room.match.via)
        assertEquals(4, room.match.gamesA)
        val ev = room.feed.last { it.t() == "hand" }
        assertEquals("bandeira", ev.at("result", "kind").jsonPrimitive.content)
        assertEquals("a", ev.str("winner"))
        assertEquals(0, ev.int("at"))
        assertEquals("A partida já acabou.", room.action(0, jsonObj("type" to "next"), e.now).error)
        val maos = room.handNo
        e.now += NEXT_HAND_MS * 3
        e.store.tickAll(e.now)
        assertEquals(maos, room.handNo)
        assertEquals("over", room.status)
        val bot = (1..3).first { room.seats[it]!!.bot }
        assertEquals("Só o anfitrião reinicia.", room.action(bot, jsonObj("type" to "rematch"), e.now).error)
        assertTrue(room.action(0, jsonObj("type" to "rematch"), e.now).error == null)
        assertEquals(1, room.handNo)
        assertEquals("playing", room.status)
        assertNull(room.match.winner)
        assertEquals(0, room.match.gamesA)
        assertTrue(room.feed.last { it.t() == "start" }.bool("rematch")!!)
    }

    @Test
    fun semBandeiraFechaPelaPortaDosJogos() {
        val e = Env()
        val room = e.started()
        room.rules.bandeiraFecha = false
        room.rules.jogosParaGanhar = 4
        val g = room.hand!!
        g.over = true
        g.result = scoreHand(TOTAL_POINTS, 0, room.rules, 10, 0)
        room.settleHand(e.now)
        assertEquals(4, room.match.gamesA)
        assertEquals("a", room.match.winner)
        assertEquals("jogos", room.match.via)
        assertEquals("over", room.status)
    }

    @Test
    fun empateA60NaoMarca() {
        val e = Env()
        val room = e.started()
        room.rules.jogosParaGanhar = 1
        val g = room.hand!!
        g.over = true
        g.result = scoreHand(60, 60, room.rules, 5, 5)
        room.settleHand(e.now)
        assertEquals(0, room.match.gamesA)
        assertEquals(0, room.match.gamesB)
        assertNull(room.match.winner)
        assertTrue(room.nextHandAt > 0)
        assertEquals("playing", room.status)
    }

    @Test
    fun feedCortaEmOitentaEDaSoOMaisNovo() {
        val e = Env()
        e.create()
        val room = e.room()
        repeat(120) { i -> room.emit(e.now, "t" to "ping", "n" to i) }
        assertEquals(FEED_MAX, room.feed.size)
        assertEquals(120L, room.seq)
        val since = room.feed[room.feed.size - 3].int("seq")!!.toLong()
        val events = e.view(0, since).json["events"]!!.jsonArray
        assertEquals(2, events.size)
        assertTrue(events.all { it.obj().int("seq")!!.toLong() > since })
        assertEquals(0, e.view(0, 120).json["events"]!!.jsonArray.size)
    }

    @Test
    fun mesaFechadaSaiDoMapaUmMinutoDepois() {
        val e = Env()
        e.create()
        val room = e.room()
        assertTrue(room.action(0, jsonObj("type" to "cancel"), e.now).error == null)
        assertEquals("closed", room.status)
        assertEquals("cancel", room.feed.last().str("reason"))
        e.store.tickAll(e.now + CLOSED_KEEP - 1)
        assertEquals(1, e.store.openRooms.size)
        e.now += CLOSED_KEEP + 1
        e.store.tickAll(e.now)
        assertEquals(0, e.store.openRooms.size)
        assertNull(e.store.get(e.id))
        val e2 = Env(); e2.create(); val s = e2.join("Bruno", "b")
        assertEquals("Só o anfitrião fecha a mesa.", e2.room().action(s, jsonObj("type" to "cancel"), e2.now).error)
    }

    @Test
    fun limiteDeMesasNaMaquina() {
        val store = RoomStore()
        store.rng = { 0.5 }
        repeat(MAX_ROOMS) { i ->
            assertEquals(200, store.createRoom(jsonObj("name" to "Mesa $i", "player" to jsonObj("name" to "P$i")), 1L).code)
        }
        val cheio = store.createRoom(jsonObj("name" to "Mais uma"), 1L)
        assertEquals(503, cheio.code)
        assertEquals("Demasiadas mesas abertas.", cheio.json.str("error"))
        assertEquals(MAX_ROOMS, store.openRooms.size)
        assertEquals(MAX_ROOMS, store.listRooms().json["rooms"]!!.jsonArray.size)
        assertEquals(MAX_ROOMS, store.openRooms.map { it.id }.toSet().size)
        assertTrue(store.openRooms.all { it.id.length == 4 })
    }

    @Test
    fun accoesDesconhecidasELugarVazio() {
        val e = Env()
        e.create()
        val room = e.room()
        assertEquals("ação desconhecida", room.action(0, jsonObj("type" to "piquenique"), e.now).error)
        assertEquals("Lugar livre — entra na mesa primeiro.",
            room.action(3, jsonObj("type" to "ready"), e.now).error)
        assertEquals(409, e.store.act(e.id, jsonObj("tk" to e.tk, "type" to "ready"), e.now).code)
        assertEquals(409, e.store.act(e.id, jsonObj("seat" to 9, "tk" to e.tk, "type" to "ready"), e.now).code)
        assertEquals(409, e.store.act(e.id, jsonObj("seat" to "x", "tk" to e.tk, "type" to "ready"), e.now).code)
    }

    @Test
    fun infoDaPortaEDoLan() {
        val store = RoomStore(8743)
        store.ip = { "192.168.100.2" }
        val info = store.info().json
        assertEquals("sueca-no-cafe", info.str("app"))
        assertEquals(8743, info.int("port"))
        assertEquals("192.168.100.2", info.str("lanIp"))
        assertEquals(4, info.int("players"))
    }

    @Test
    fun limparFechaTudo() {
        val e = Env()
        e.create()
        e.join("Bruno", "b")
        e.store.clear(e.now)
        assertEquals(0, e.store.openRooms.size)
        assertEquals(404, e.store.state(e.id, 0, e.tk, 0, e.now).code)
    }
}
