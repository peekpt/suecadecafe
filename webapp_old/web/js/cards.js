// Desenho das cartas em HTML puro, como o baralho do Paciência: índice grande no
// canto esquerdo, naipe no canto direito e uma figura ou um naipe enorme a
// preencher o corpo — a mão tem de se ler ao sol, sem letras minúsculas.
import { RANK_INFO, SUIT_INFO } from './engine.js';

const ART = {
  R: `<svg class="cc-art" viewBox="0 0 44 58" aria-hidden="true">
        <path class="a-crown" d="M8.5 19 11.6 8.4l5.2 5.2L22 5.5l5.2 8.1 5.2-5.2L35.5 19z"/>
        <path class="a-hair" d="M11 31c-1.4-11.6 4-16.6 11-16.6S34.4 19.4 33 31z"/>
        <circle class="a-face" cx="22" cy="28" r="9"/>
        <path class="a-body" d="M4.5 58c1.6-12.4 8.4-17.6 17.5-17.6S37.9 45.6 39.5 58z"/>
        <circle class="a-dot" cx="18.6" cy="26.8" r="1.25"/>
        <circle class="a-dot" cx="25.4" cy="26.8" r="1.25"/>
        <path class="a-line" d="M18 32.4c1.7 1.9 6.3 1.9 8 0"/>
        <path class="a-line" d="M13.6 45.4c3-2.8 13.8-2.8 16.8 0"/>
      </svg>`,
  D: `<svg class="cc-art" viewBox="0 0 44 58" aria-hidden="true">
        <path class="a-hair" d="M9.5 36c-2.6-14 2.4-23.4 12.5-23.4S37.1 22 34.5 36c-1.4-8.4-4.4-12.4-12.5-12.4S10.9 27.6 9.5 36z"/>
        <path class="a-crown" d="M12.6 16.8c1.4-3.6 3.4-5.6 4.6-3.4 1.2 2.2 2.4-3 4.8-3s3.6 5.2 4.8 3c1.2-2.2 3.2-.2 4.6 3.4z"/>
        <circle class="a-face" cx="22" cy="28" r="8.6"/>
        <path class="a-body" d="M5.5 58c1.4-11.6 8-16.4 16.5-16.4S37.1 46.4 38.5 58z"/>
        <circle class="a-dot" cx="18.8" cy="27" r="1.2"/>
        <circle class="a-dot" cx="25.2" cy="27" r="1.2"/>
        <path class="a-line" d="M19 32c1.5 1.6 4.5 1.6 6 0"/>
        <path class="a-line" d="M15.4 44.6c2.2 3.4 11 3.4 13.2 0"/>
      </svg>`,
  V: `<svg class="cc-art" viewBox="0 0 44 58" aria-hidden="true">
        <path class="a-crown" d="M10.5 21c0-8.6 5-13.6 11.5-13.6S33.5 12.4 33.5 21z"/>
        <path class="a-line" d="M31 12.6c3.6-3.4 7.4-3 8.4.8-3.4 1.6-6.6.6-8.4-.8z"/>
        <circle class="a-face" cx="22" cy="28.4" r="8.8"/>
        <path class="a-hair" d="M13.4 24.6c-.6-3.4 1.4-5.6 3.4-6.4 4-1.6 9.4-1 13.4 1.6-1.4 3-4.4 4.6-8.4 4.6-4.4 0-7.4.8-8.4.2z"/>
        <path class="a-body" d="M5 58c1.4-11.4 8-16 17-16s15.6 4.6 17 16z"/>
        <circle class="a-dot" cx="18.8" cy="27.6" r="1.2"/>
        <circle class="a-dot" cx="25.2" cy="27.6" r="1.2"/>
        <path class="a-line" d="M19 32.6c1.5 1.6 4.5 1.6 6 0"/>
        <path class="a-line" d="M14 43.6c2.6 3.8 12.4 3.8 16 0"/>
      </svg>`,
};

export const suitGlyph = (card) => SUIT_INFO[card[0]].glyph;

export function cardHTML(card, o = {}) {
  const {
    trump = null, who = '', cls = '', playable = false, dim = false,
    fresh = false, delay = 0, btn = false, val = false,
  } = o;
  const s = card[0];
  const r = card.slice(1);
  const info = RANK_INFO[r];
  const isTrump = trump && s === trump;
  const classes = ['cc', `s-${s}`];
  if (isTrump) classes.push('trump');
  if (playable) classes.push('playable');
  if (dim) classes.push('dim');
  if (fresh) classes.push('fresh');
  if (cls) classes.push(cls);
  const tag = btn ? 'button' : 'div';
  const attrs = btn ? ` type="button" aria-label="${info.name} de ${SUIT_INFO[s].name}"` : '';
  const art = ART[r] ? ART[r] : `<span class="cc-pip${r === 'A' ? ' ace' : ''}">${SUIT_INFO[s].glyph}</span>`;
  return `<${tag} class="${classes.join(' ')}" data-card="${card}"${attrs}${delay ? ` style="animation-delay:${delay}ms"` : ''}>
    <span class="cc-hd"><b>${info.label}</b><i>${SUIT_INFO[s].glyph}</i></span>
    <span class="cc-mid">${art}</span>
    ${val && info.value ? `<span class="cc-val">${info.value}</span>` : ''}
    ${who ? `<span class="cc-who">${who}</span>` : ''}
  </${tag}>`;
}

// miniatura só com o naipe, para o trunfo no topo do ecrã
export function trumpChipHTML(suit) {
  return `<span class="trump-chip s-${suit}">${SUIT_INFO[suit].glyph}<b>${SUIT_INFO[suit].name}</b></span>`;
}
