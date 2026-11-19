// Entrada do bundle de avatares: o DiceBear corre no browser, sem rede.
// O jogo promete "sem internet" — por isso a coleção vem empacotada e
// cada cliente desenha o avatar a partir do nome, em vez de pedir imagens.
import { createAvatar } from '@dicebear/core';
import * as adventurer from '@dicebear/adventurer';

window.SuecaAvatars = {
  // o g do token ignora-se: só há Bonecos, e assim os tokens antigos
  // (outros grupos) continuam a desenhar-se em vez de caírem no emoji
  svg(g, seed, bg) {
    const s = createAvatar(adventurer, { seed, size: 96 }).toString();
    return bg ? s.replace(/<svg([^>]*)>/, `<svg$1><rect width="100%" height="100%" fill="#${bg}"/>`) : s;
  },
};