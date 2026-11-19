import test from 'node:test';
import assert from 'node:assert/strict';

// o bundle é IIFE de browser: dá-lhe uma janela e ele instala-se lá
globalThis.window = {};
await import('../web/vendor/avatars.js');
const { GROUP_LABEL, HYPOTHESES, BG, seedFor, parseToken, tokenFor, avatarURL, avatarHTML } =
  await import('../web/js/avatars.js');

test('o token do avatar vai e volta', () => {
  for (let v = 0; v < HYPOTHESES; v++) {
    assert.deepEqual(parseToken(tokenFor(0, v)), { g: 0, v });
  }
  // tokens de outros grupos continuam a ser aceites (desenham-se como bonecos)
  assert.deepEqual(parseToken('d3:2'), { g: 3, v: 2 });
  assert.equal(parseToken('cafe'), null);
  assert.equal(parseToken('robot'), null);
  assert.equal(parseToken('d9:0'), null);
  assert.equal(parseToken('d0:8'), null);
  assert.equal(parseToken(''), null);
});

test('a semente nasce do nome e da hipótese', () => {
  assert.equal(seedFor('Zé do Café', 0), 'Zé do Café');
  assert.equal(seedFor('Zé do Café', 3), 'Zé do Café·3');
  assert.equal(seedFor('  ', 0), 'cafe');
  assert.equal(seedFor(null, 2), 'cafe·2');
});

test('uma fila de oito hipóteses, todas diferentes e nas cores da casa', () => {
  assert.equal(GROUP_LABEL, 'Bonecos');
  assert.equal(HYPOTHESES, 8);
  assert.equal(BG.length, 8);
  const urls = new Set();
  for (let v = 0; v < HYPOTHESES; v++) {
    const url = avatarURL(0, v, 'Zé do Café');
    assert.ok(url.startsWith('data:image/svg+xml'), `hipótese ${v}`);
    assert.ok(decodeURIComponent(url).includes(`#${BG[v]}`), 'o campo de cor da hipótese');
    urls.add(url);
  }
  assert.equal(urls.size, 8);
});

test('o mesmo nome dá sempre o mesmo desenho', () => {
  const a = avatarURL(0, 1, 'Maria');
  const b = avatarURL(0, 1, 'Maria');
  assert.equal(a, b);
  assert.notEqual(a, avatarURL(0, 1, 'Mari'));
  assert.notEqual(a, avatarURL(0, 2, 'Maria'));
});

test('sem bundle cai no emoji do café, nunca numa cadeira vazia', () => {
  const lib = window.SuecaAvatars;
  delete window.SuecaAvatars;
  assert.match(avatarHTML('d0:0', 'Zé'), /av-emoji/);
  assert.match(avatarHTML('cafe', 'Zé'), /☕/);
  assert.match(avatarHTML('robot', 'Parceiro 2'), /🤖/);
  assert.match(avatarHTML('qualquer-coisa', 'Zé'), /☕/);
  window.SuecaAvatars = lib;
  assert.match(avatarHTML('d1:0', 'Parceiro 2'), /<img class="av-img/);
});
