// Parceiro automático — preenche vagas e joga de forma simpática, sem trapaças:
// só vê as cartas que lhe pertencem e escolhe sempre dentro das jogadas legais.
import {
  legalPlays, teamOfSeat, trickWinner, suitOf, rankOf, valueOf, strengthOf, winsOver,
} from './engine.js';

const RANK_VALUE = valueOf;

function handOf(g, seat) {
  return g.hands[seat];
}

function grouped(hand) {
  const by = {};
  for (const c of hand) (by[suitOf(c)] ||= []).push(c);
  for (const s of Object.keys(by)) by[s].sort((a, b) => strengthOf(a) - strengthOf(b));
  return by;
}

// Cartas por ponto que ainda podem valer (Ás, 7, figuras).
const isPointCard = (c) => RANK_VALUE(c) > 0;

export function chooseCard(g, seat) {
  const legal = legalPlays(g, seat);
  if (!legal.length) return null;
  const trump = g.trump;
  const myTeam = teamOfSeat(seat);
  const trumps = legal.filter((c) => suitOf(c) === trump);
  const off = legal.filter((c) => suitOf(c) !== trump);
  const cheapest = (list) => list.slice().sort((a, b) => strengthOf(b) - strengthOf(a))[0];
  const dearest = (list) => list.slice().sort((a, b) => strengthOf(a) - strengthOf(b))[0];

  // ---- abrir a vaza ----
  if (!g.trick.length) {
    const hand = handOf(g, seat);
    // 1. chamar trunfo alto quando mandamos nele
    const myTrumps = hand.filter((c) => suitOf(c) === trump).sort((a, b) => strengthOf(a) - strengthOf(b));
    if (myTrumps.length && strengthOf(myTrumps[0]) <= 2 && myTrumps.length >= 2) return myTrumps[0];
    // 2. ás de trunfo: é para ganhar
    if (myTrumps[0] && rankOf(myTrumps[0]) === 'A') return myTrumps[0];
    // 3. sair do naipe mais comprido, pela carta com pontos
    const by = grouped(hand);
    const suits = Object.keys(by).sort((a, b) => by[b].length - by[a].length || strengthOf(by[a][0]) - strengthOf(by[b][0]));
    for (const s of suits) {
      if (s === trump) continue;
      const pts = by[s].filter(isPointCard);
      if (pts.length >= 2) return pts[pts.length - 1];       // soltar uma figura baixa
      if (pts.length === 1 && by[s].length === 1) return by[s][0]; //singleton
    }
    const plain = suits.find((s) => s !== trump) || suits[0];
    return cheapest(by[plain]);
  }

  // ---- responder ao que está na mesa ----
  const plays = g.trick.map((p) => p.card);
  const bestIdx = trickWinner(plays, trump);
  const best = plays[bestIdx];
  const bestSeat = g.trick[bestIdx].seat;
  const partnerWins = teamOfSeat(bestSeat) === myTeam;
  const lastToPlay = g.trick.length === 3;
  const beating = legal.filter((c) => winsOver(c, best, trump));

  if (partnerWins) {
    if (lastToPlay) {
      // o parceiro leva: carregar-lhe a vaza com pontos, se não custar caro
      const gift = legal.filter(isPointCard).sort((a, b) => RANK_VALUE(b) - RANK_VALUE(a))[0];
      if (gift && RANK_VALUE(gift) <= 4) return gift;
    }
    return cheapest(beating.length ? beating : legal);
  }

  // adversário na frente
  if (beating.length) {
    const cheapTrump = trumps.find((c) => beating.includes(c));
    const safe = beating.filter((c) => suitOf(c) === suitOf(best));
    const pick = cheapTrump || (safe.length ? cheapest(safe) : cheapest(beating));
    // não gastar o Ás nem o 7 para ganhar uma vaza sem pontos
    if (!isPointCard(best) && RANK_VALUE(pick) >= 10 && safe.length) return cheapest(safe) || pick;
    return pick;
  }
  // não há forma de ganhar: destralhar o que não vale nada
  const waste = legal.filter((c) => !isPointCard(c) && suitOf(c) !== trump);
  if (waste.length) return cheapest(waste);
  return cheapest(legal.filter((c) => suitOf(c) !== trump)) || cheapest(legal);
}

// Quem é bot joga ao fim de um pequeno intervalo, para a mesa parecer viva.
export const botDelay = () => 500 + Math.floor(Math.random() * 550);
