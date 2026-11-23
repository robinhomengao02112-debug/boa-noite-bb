// Fonte de eventos SIMULADA: gera comentários, curtidas e presentes falsos
// para testar o jogo e o overlay sem estar ao vivo.
const GIFTS = [
  { giftName: 'Rose', diamonds: 1, w: 50 },
  { giftName: 'TikTok', diamonds: 1, w: 20 },
  { giftName: 'Finger Heart', diamonds: 5, w: 15 },
  { giftName: 'Doughnut', diamonds: 30, w: 8 },
  { giftName: 'Hand Hearts', diamonds: 100, w: 4 },
  { giftName: 'Galaxy', diamonds: 1000, w: 1 },
];
const NAMES = ['ana', 'joao', 'bia', 'leo', 'duda', 'caio', 'mari', 'rafa', 'lu', 'gabi', 'theo', 'nina', 'pedro', 'julia', 'enzo'];

function pick(arr) { return arr[Math.floor(Math.random() * arr.length)]; }
function weighted(arr) {
  const total = arr.reduce((s, x) => s + x.w, 0);
  let r = Math.random() * total;
  for (const x of arr) { if ((r -= x.w) <= 0) return x; }
  return arr[0];
}

export function createSimulatorSource({ onEvent, onStatus }) {
  let timer = null;
  return {
    start() {
      onStatus({ connected: true, roomId: 'SIMULADOR' });
      timer = setInterval(() => {
        const name = pick(NAMES);
        const user = { id: name, nick: name, avatar: null };
        const r = Math.random();
        if (r < 0.35) onEvent({ type: 'chat', user, text: Math.random() < 0.6 ? String(1 + Math.floor(Math.random() * 4)) : 'bora!!' });
        else if (r < 0.75) onEvent({ type: 'like', user, count: 1 + Math.floor(Math.random() * 15) });
        else if (r < 0.80) onEvent({ type: 'follow', user });
        else if (r < 0.82) onEvent({ type: 'share', user });
        else if (r < 0.90) {
          const g = weighted(GIFTS);
          const count = g.diamonds === 1 ? 1 + Math.floor(Math.random() * 5) : 1;
          onEvent({ type: 'gift', user, giftName: g.giftName, diamonds: g.diamonds, count, totalDiamonds: g.diamonds * count });
        } else onEvent({ type: 'join', user });
      }, 400);
    },
    stop() { clearInterval(timer); },
  };
}
