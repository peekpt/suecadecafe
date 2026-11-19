// Avatares DiceBear feitos a partir do nome. Para a mesa viaja apenas o
// token "d<grupo>:<hipótese>" e o nome que já lá vai: cada cliente desenha
// o seu avatar localmente, por isso não há imagens no fio nem internet.
// Só há um grupo à escolha (Bonecos); os outros números de grupo ficam
// aceitos no parse para tokens antigos não ficarem sem cara.
export const GROUP_LABEL = 'Bonecos';
export const HYPOTHESES = 8;
// os campos de cor, um por hipótese, nas cores da casa
export const BG = ['ffe9c6', 'dfeaff', 'e4f2d8', 'ffe3de', 'ece4ff', 'd9f2ef', 'ffd9ea', 'f5e7cf'];

const LEGACY = {
  cafe: '☕', croissant: '🥐', cupcake: '🧁', donut: '🍩', bule: '🫖', bolo: '🍰',
  gato: '🐈', mocho: '🦉', chapeu: '🎩', barista: '🧑‍🍳', planta: '🌿', fogo: '🔥',
  bolacha: '🍪', galao: '🥛', robot: '🤖',
};

export const seedFor = (name, v) => {
  const n = String(name ?? '').trim() || 'cafe';
  return v ? `${n}·${v}` : n;
};
export const parseToken = (t) => {
  const m = /^d([0-4]):([0-7])$/.exec(String(t ?? ''));
  return m ? { g: +m[1], v: +m[2] } : null;
};
export const tokenFor = (g, v) => `d${g}:${v}`;
export const legacyEmoji = (id) => LEGACY[id] || null;

const cache = new Map();
export function avatarURL(g, v, name) {
  const lib = typeof window !== 'undefined' && window.SuecaAvatars;
  if (!lib) return null;
  const key = `${g}:${v}:${seedFor(name, v)}`;
  let url = cache.get(key);
  if (!url) {
    const svg = lib.svg(g, seedFor(name, v), BG[v]);
    url = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
    if (cache.size > 600) cache.clear();
    cache.set(key, url);
  }
  return url;
}

// devolve sempre alguma coisa: sem bundle (cache antiga, ficheiro em falta)
// cai no emoji do café em vez de deixar a cadeira vazia
export function avatarHTML(token, name, cls = '') {
  const p = parseToken(token);
  const url = p && avatarURL(p.g, p.v, name);
  if (url) return `<img class="av-img ${cls}" src="${url}" alt="" draggable="false">`;
  return `<span class="av-emoji ${cls}">${legacyEmoji(token) || '☕'}</span>`;
}