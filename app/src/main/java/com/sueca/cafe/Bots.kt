package com.sueca.cafe

// Parceiro automático — preenche vagas e joga de forma simpática, sem trapaças:
// só vê as cartas que lhe pertencem e escolhe sempre dentro das jogadas legais.
// Espelho do bots.js da app web.

private fun isPointCard(c: String) = valueOf(c) > 0

private fun grouped(hand: List<String>): Map<String, List<String>> =
    hand.groupBy { suitOf(it) }.mapValues { (_, v) -> v.sortedBy { strengthOf(it) } }

private fun cheapest(list: List<String>) = list.maxByOrNull { strengthOf(it) }

fun chooseCard(g: GameState, seat: Int): String? {
    val legal = legalPlays(g, seat)
    if (legal.isEmpty()) return null
    val trump = g.trump
    val myTeam = teamOfSeat(seat)
    val trumps = legal.filter { suitOf(it) == trump }
    val off = legal.filter { suitOf(it) != trump }

    // ---- abrir a vaza ----
    if (g.trick.isEmpty()) {
        val hand = g.hands[seat]
        // 1. chamar trunfo alto quando mandamos nele
        val myTrumps = hand.filter { suitOf(it) == trump }.sortedBy { strengthOf(it) }
        if (myTrumps.isNotEmpty() && strengthOf(myTrumps[0]) <= 2 && myTrumps.size >= 2) return myTrumps[0]
        // 2. ás de trunfo: é para ganhar
        if (myTrumps.firstOrNull()?.let { rankOf(it) == "A" } == true) return myTrumps[0]
        // 3. sair do naipe mais comprido, pela carta com pontos
        val by = grouped(hand)
        val suits = by.keys.sortedWith(
            compareByDescending<String> { by.getValue(it).size }
                .thenBy { strengthOf(by.getValue(it).first()) }
        )
        for (s in suits) {
            if (s == trump) continue
            val pts = by.getValue(s).filter { isPointCard(it) }
            if (pts.size >= 2) return pts.last()                 // soltar uma figura baixa
            if (pts.size == 1 && by.getValue(s).size == 1) return by.getValue(s).first() // singleton
        }
        val plain = suits.firstOrNull { it != trump } ?: suits.firstOrNull() ?: return cheapest(legal)
        return cheapest(by.getValue(plain)) ?: cheapest(legal)
    }

    // ---- responder ao que está na mesa ----
    val plays = g.trick.map { it.card }
    val bestIdx = trickWinner(plays, trump)
    val best = plays[bestIdx]
    val bestSeat = g.trick[bestIdx].seat
    val partnerWins = teamOfSeat(bestSeat) == myTeam
    val lastToPlay = g.trick.size == 3
    val beating = legal.filter { winsOver(it, best, trump) }

    if (partnerWins) {
        if (lastToPlay) {
            // o parceiro leva: carregar-lhe a vaza com pontos, se não custar caro
            val gift = legal.filter { isPointCard(it) }.maxByOrNull { valueOf(it) }
            if (gift != null && valueOf(gift) <= 4) return gift
        }
        return cheapest(if (beating.isNotEmpty()) beating else legal)
    }

    // adversário na frente
    if (beating.isNotEmpty()) {
        val cheapTrump = trumps.firstOrNull { it in beating }
        val safe = beating.filter { suitOf(it) == suitOf(best) }
        val pick = cheapTrump ?: (if (safe.isNotEmpty()) cheapest(safe) else cheapest(beating)) ?: return cheapest(legal)
        // não gastar o Ás nem o 7 para ganhar uma vaza sem pontos
        if (!isPointCard(best) && valueOf(pick) >= 10 && safe.isNotEmpty()) return cheapest(safe) ?: pick
        return pick
    }
    // não há forma de ganhar: destralhar o que não vale nada
    val waste = legal.filter { !isPointCard(it) && suitOf(it) != trump }
    if (waste.isNotEmpty()) return cheapest(waste)
    return (if (off.isNotEmpty()) cheapest(off) else null) ?: cheapest(legal)
}

// Quem é bot joga ao fim de um pequeno intervalo, para a mesa parecer viva.
fun botDelay(): Int = 500 + (Math.random() * 550).toInt()
