package com.sueca.cafe

// Sueca — regras de Portugal. Motor puro (sem Android): o servidor usa-o para
// distribuir e validar, os testes usam-no para verificar as regras, a UI lê-o
// para desenhar. Espelho fiel do engine.js que a app web deixou de usar.

const val TOTAL_POINTS = 120
const val TURNED_TRICKS = 3

val SUIT_KEYS = listOf("e", "c", "o", "p")
val RANK_ORDER = listOf("A", "7", "R", "V", "D", "6", "5", "4", "3", "2")

data class SuitInfo(val name: String, val glyph: String, val dark: Boolean)
val SUIT_INFO = mapOf(
    "e" to SuitInfo("Espadas", "♠", true),
    "c" to SuitInfo("Copas", "♥", false),
    "o" to SuitInfo("Ouros", "♦", false),
    "p" to SuitInfo("Paus", "♣", true),
)

data class RankInfo(val name: String, val label: String, val value: Int)
val RANK_INFO = mapOf(
    "A" to RankInfo("Ás", "A", 11),
    "7" to RankInfo("Sete", "7", 10),
    "R" to RankInfo("Rei", "R", 4),
    "V" to RankInfo("Valete", "V", 3),
    "D" to RankInfo("Dama", "D", 2),
    "6" to RankInfo("Seis", "6", 0),
    "5" to RankInfo("Cinco", "5", 0),
    "4" to RankInfo("Quatro", "4", 0),
    "3" to RankInfo("Três", "3", 0),
    "2" to RankInfo("Dois", "2", 0),
)

data class TeamInfo(val key: String, val name: String, val seats: List<Int>)
val TEAMS = mapOf(
    "a" to TeamInfo("a", "Bica", listOf(0, 2)),
    "b" to TeamInfo("b", "Galão", listOf(1, 3)),
)

data class Rules(
    var virarTrunfo: Boolean = true,
    var trunfarSeVazio: Boolean = false,
    var subirSempre: Boolean = false,
    var jogosParaGanhar: Int = 4,
    var bandeiraFecha: Boolean = true,
)

fun suitOf(card: String) = card.substring(0, 1)
fun rankOf(card: String) = card.substring(1)
fun valueOf(card: String) = RANK_INFO.getValue(rankOf(card)).value

// 0 = mais forte
fun strengthOf(card: String) = RANK_ORDER.indexOf(rankOf(card))
fun teamOfSeat(seat: Int) = if (seat % 2 == 0) "a" else "b"
fun nextSeat(seat: Int) = (seat + 1) % 4
fun seatFrom(seat: Int, from: Int) = (seat - from + 4) % 4

fun cardName(card: String) =
    "${RANK_INFO.getValue(rankOf(card)).name} de ${SUIT_INFO.getValue(suitOf(card)).name.lowercase()}"

fun makeDeck(): List<String> =
    SUIT_KEYS.flatMap { s -> RANK_ORDER.map { r -> s + r } }

fun shuffle(list: List<String>, rng: () -> Double): List<String> {
    val a = list.toMutableList()
    for (i in a.size - 1 downTo 1) {
        val j = (rng() * (i + 1)).toInt()
        val t = a[i]; a[i] = a[j]; a[j] = t
    }
    return a
}

private val SUIT_DISPLAY = mapOf("e" to 0, "c" to 1, "o" to 2, "p" to 3)
fun sortHand(hand: List<String>): List<String> = hand.sortedWith(
    compareBy({ SUIT_DISPLAY.getValue(suitOf(it)) }, { strengthOf(it) })
)

// a) a carta vence mesmo?
fun winsOver(card: String, best: String?, trump: String): Boolean {
    if (best == null) return true
    val tA = suitOf(card) == trump
    val tB = suitOf(best) == trump
    if (tA != tB) return tA
    if (tA) return strengthOf(card) < strengthOf(best)
    return suitOf(card) == suitOf(best) && strengthOf(card) < strengthOf(best)
}

fun trickWinner(cards: List<String>, trump: String): Int {
    var w = 0
    for (i in 1 until cards.size) {
        if (winsOver(cards[i], cards[w], trump)) w = i
    }
    return w
}

fun pointsIn(cards: List<String>) = cards.sumOf { valueOf(it) }

data class Play(val seat: Int, val card: String)

data class TrickRecord(
    val winner: Int,
    val team: String,
    val cards: List<String>,
    val points: Int,
    val plays: List<Play>,
)

data class TeamCount(var a: Int = 0, var b: Int = 0) {
    fun add(team: String, n: Int) { if (team == "a") a += n else b += n }
    fun of(team: String) = if (team == "a") a else b
}

data class HandResult(
    val pointsA: Int, val pointsB: Int,
    val tricksA: Int, val tricksB: Int,
    val gamesA: Int, val gamesB: Int,
    val winner: String?,
    val kind: String,
    val label: String,
    val bandeira: Boolean,
)

data class Match(
    val gamesA: Int = 0,
    val gamesB: Int = 0,
    val hands: List<HandResult> = emptyList(),
    val winner: String? = null,
    val via: String? = null,
)

sealed class GameEvent {
    data class Deal(val handNo: Int, val dealer: Int, val trump: String, val turned: String?) : GameEvent()
    data class Played(val seat: Int, val card: String) : GameEvent()
    data class TrickWon(val winner: Int, val team: String, val points: Int, val cards: List<String>) : GameEvent()
    data class TurnedPlayed(val seat: Int, val card: String) : GameEvent()
    data class TurnedTaken(val seat: Int, val trick: Int) : GameEvent()
    data class HandDone(val result: HandResult) : GameEvent()
}

class GameState(
    val rules: Rules,
    val dealer: Int,
    val leader0: Int,
    var trump: String,
    var turned: String?,
    var turnedSeat: Int?,
    handsIn: List<List<String>>,
) {
    var leader = leader0
    var turn = leader0
    var turnedLive = turned != null
    val hands = handsIn.map { it.toMutableList() }.toMutableList()
    val trick = mutableListOf<Play>()
    val points = TeamCount()
    val tricks = TeamCount()
    val history = mutableListOf<TrickRecord>()
    val events = mutableListOf<GameEvent>()
    var over = false
    var result: HandResult? = null

    fun cardsLeft(seat: Int) = hands[seat].size
    fun leadSuit(): String? = if (trick.isEmpty()) null else suitOf(trick[0].card)
}

sealed class PlayResult {
    data class Error(val message: String) : PlayResult()
    data object Pending : PlayResult()
    data class TrickDone(val winner: Int, val points: Int) : PlayResult()
    data class Finished(val result: HandResult) : PlayResult()
}

fun createHand(rules: Rules = Rules(), dealer: Int = 0, rng: () -> Double): GameState {
    val deck = shuffle(makeDeck(), rng)
    val hands = List(4) { mutableListOf<String>() }
    // dois lotes de 5, começando no jogador à direita de quem dá
    val order = listOf(nextSeat(dealer), nextSeat(nextSeat(dealer)), nextSeat(nextSeat(nextSeat(dealer))), dealer)
    var i = 0
    for (batch in 0 until 2)
        for (seat in order)
            for (k in 0 until 5) hands[seat].add(deck[i++])

    val trump: String
    val turned: String?
    if (rules.virarTrunfo) {
        turned = deck[deck.size - 1]          // a última carta é sempre de quem dá
        trump = suitOf(turned)
    } else {
        turned = null
        trump = SUIT_KEYS[((rng() * 4).toInt()).coerceAtMost(3)]
    }

    val leader = nextSeat(dealer)
    val sorted = hands.map { sortHand(it) }
    return GameState(rules, dealer, leader, trump, turned, if (turned != null) dealer else null, sorted)
}

private fun bestPlay(g: GameState): String? =
    if (g.trick.isEmpty()) null else g.trick[trickWinner(g.trick.map { it.card }, g.trump)].card

// Cartas que o lugar pode legalmente jogar agora.
fun legalPlays(g: GameState, seat: Int): List<String> {
    if (g.over || seat != g.turn) return emptyList()
    val hand = g.hands[seat]
    if (g.trick.isEmpty()) return hand.toList()

    val lead = suitOf(g.trick[0].card)
    val matching = hand.filter { suitOf(it) == lead }
    var pool = matching
    if (pool.isEmpty()) {
        pool = hand.toList()
        if (g.rules.trunfarSeVazio) {
            val trumps = pool.filter { suitOf(it) == g.trump }
            if (trumps.isNotEmpty()) pool = trumps
        }
    }
    if (g.rules.subirSempre) {
        val best = bestPlay(g)!!
        val beating = pool.filter { winsOver(it, best, g.trump) }
        if (beating.isNotEmpty()) pool = beating
    }
    return pool
}

fun scoreHand(pa: Int, pb: Int, rules: Rules, tricksA: Int, tricksB: Int): HandResult {
    var gamesA = 0; var gamesB = 0
    val leader = if (pa == pb) null else if (pa > pb) "a" else "b"
    val winnerPoints = when (leader) { "a" -> pa; "b" -> pb; else -> 0 }
    var label = "Empate a 60 — ninguém marca"
    var kind = "empate"
    if (leader != null) {
        if (winnerPoints == TOTAL_POINTS) {
            if (leader == "a") gamesA = 4 else gamesB = 4
            label = if (rules.bandeiraFecha) "Bandeira — fecha a partida" else "Bandeira — 4 jogos"
            kind = "bandeira"
        } else if (winnerPoints >= 91) {
            if (leader == "a") gamesA = 2 else gamesB = 2
            label = "$winnerPoints pontos — 2 jogos"
            kind = "dois jogos"
        } else {
            if (leader == "a") gamesA = 1 else gamesB = 1
            label = "$winnerPoints pontos — 1 jogo"
            kind = "um jogo"
        }
    }
    return HandResult(pa, pb, tricksA, tricksB, gamesA, gamesB, leader, kind, label, kind == "bandeira")
}

fun playCard(g: GameState, seat: Int, card: String): PlayResult {
    if (g.over) return PlayResult.Error("A mão já terminou.")
    if (seat != g.turn) return PlayResult.Error("Ainda não é a tua vez.")
    if (card !in g.hands[seat]) return PlayResult.Error("Essa carta não está na tua mão.")
    if (card !in legalPlays(g, seat)) return PlayResult.Error("Tens de assistir ao naipe de saída.")

    g.hands[seat].remove(card)
    g.trick.add(Play(seat, card))

    if (g.turnedLive && card == g.turned) {
        g.turnedLive = false
        g.events.add(GameEvent.TurnedPlayed(seat, card))
    }

    if (g.trick.size < 4) {
        g.turn = nextSeat(seat)
        g.events.add(GameEvent.Played(seat, card))
        return PlayResult.Pending
    }

    val cards = g.trick.map { it.card }
    val wIdx = trickWinner(cards, g.trump)
    val wSeat = g.trick[wIdx].seat
    val team = teamOfSeat(wSeat)
    val pts = pointsIn(cards)
    g.points.add(team, pts)
    g.tricks.add(team, 1)
    g.history.add(TrickRecord(wSeat, team, cards, pts, g.trick.toList()))
    g.events.add(GameEvent.TrickWon(wSeat, team, pts, cards))
    g.trick.clear()

    if (g.turnedLive && g.history.size >= TURNED_TRICKS) {
        g.turnedLive = false
        g.events.add(GameEvent.TurnedTaken(g.turnedSeat!!, g.history.size))
    }

    if (g.hands.all { it.isEmpty() }) {
        g.over = true
        val res = scoreHand(g.points.a, g.points.b, g.rules, g.tricks.a, g.tricks.b)
        g.result = res
        g.events.add(GameEvent.HandDone(res))
        return PlayResult.Finished(res)
    }
    g.leader = wSeat
    g.turn = wSeat
    return PlayResult.TrickDone(wSeat, pts)
}

fun newMatch() = Match()

fun applyHand(match: Match, result: HandResult, rules: Rules): Match {
    val gamesA = match.gamesA + result.gamesA
    val gamesB = match.gamesB + result.gamesB
    val next = Match(gamesA, gamesB, match.hands + result, null, null)
    val target = rules.jogosParaGanhar
    return when {
        result.bandeira && rules.bandeiraFecha && result.winner != null ->
            next.copy(winner = result.winner, via = "bandeira")
        gamesA >= target && gamesA > gamesB -> next.copy(winner = "a", via = "jogos")
        gamesB >= target && gamesB > gamesA -> next.copy(winner = "b", via = "jogos")
        else -> next
    }
}

fun trumpName(trump: String) = SUIT_INFO.getValue(trump).name
