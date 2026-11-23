// Fonte de eventos REAL: conecta na live via tiktok-live-connector (biblioteca não oficial).
// Somente LEITURA: não envia mensagens, curtidas nem nada em nome da conta.
import { TikTokLiveConnection, WebcastEvent, ControlEvent } from 'tiktok-live-connector';

export function createTikTokSource({ username, apiKey, onEvent, onStatus }) {
  let conn = null;
  let stopped = false;
  let retryMs = 5000;
  let reconnects = 0;
  const MAX_RETRY = 60000;

  const user = (u) => ({
    id: u?.uniqueId || u?.userId || 'anon',
    nick: u?.nickname || u?.uniqueId || 'anon',
    avatar: u?.profilePicture?.url?.[0] || null,
  });

  function wire(c) {
    c.on(WebcastEvent.GIFT, (d) => {
      const g = d.giftDetails || {};
      // Presentes "em sequência" (giftType 1) disparam vários eventos; só contamos o final.
      if (g.giftType === 1 && !d.repeatEnd) return;
      const count = d.repeatCount || 1;
      const diamonds = g.diamondCount || 0;
      onEvent({ type: 'gift', user: user(d.user), giftName: g.giftName || `gift_${d.giftId}`, diamonds, count, totalDiamonds: diamonds * count });
    });
    c.on(WebcastEvent.CHAT, (d) => onEvent({ type: 'chat', user: user(d.user), text: String(d.comment || '') }));
    c.on(WebcastEvent.LIKE, (d) => onEvent({ type: 'like', user: user(d.user), count: d.likeCount || 1 }));
    c.on(WebcastEvent.FOLLOW, (d) => onEvent({ type: 'follow', user: user(d.user) }));
    c.on(WebcastEvent.SHARE, (d) => onEvent({ type: 'share', user: user(d.user) }));
    c.on(WebcastEvent.MEMBER, (d) => onEvent({ type: 'join', user: user(d.user) }));
    // Ignora eventos de conexões antigas (evita reconexões duplicadas).
    const lost = () => { if (c !== conn) return; onStatus({ connected: false }); schedule(); };
    c.on(WebcastEvent.STREAM_END, lost);
    c.on(ControlEvent.DISCONNECTED, lost);
    c.on(ControlEvent.ERROR, (e) => console.error('[tiktok] erro:', e?.info || e?.message || String(e)));
  }

  let timer = null;
  function schedule() {
    if (stopped || timer) return;
    console.log(`[tiktok] reconectando em ${Math.round(retryMs / 1000)}s`);
    timer = setTimeout(() => { timer = null; connect(); }, retryMs);
    retryMs = Math.min(retryMs * 2, MAX_RETRY); // backoff para não tomar rate-limit
  }

  async function connect() {
    const old = conn;
    conn = null;
    try { old?.disconnect(); } catch { /* ignore */ }
    conn = new TikTokLiveConnection(username, apiKey ? { signApiKey: apiKey } : {});
    wire(conn);
    try {
      const st = await conn.connect();
      retryMs = 5000;
      onStatus({ connected: true, roomId: st.roomId });
      console.log(`[tiktok] conectado na sala ${st.roomId}`);
    } catch (e) {
      onStatus({ connected: false, reconnects: ++reconnects });
      console.warn('[tiktok] falha ao conectar (a live está no ar?):', e?.message || e);
      schedule();
    }
  }

  return {
    start: connect,
    stop() { stopped = true; clearTimeout(timer); try { conn?.disconnect(); } catch { /* ignore */ } },
  };
}
