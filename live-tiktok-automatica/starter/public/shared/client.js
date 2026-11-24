// Código comum a todos os overlays: conexão, troca automática de jogo, status e utilidades.
// Ajusta a tela de 1080x1920 ao tamanho da janela (ex.: fonte Link do TikTok LIVE Studio em 720x1280).
// No OBS (1080x1920) a escala fica 1 e nada muda.
(function fitToWindow() {
  const fit = () => {
    const z = Math.min(window.innerWidth / 1080, window.innerHeight / 1920);
    document.documentElement.style.zoom = (z > 0 && Math.abs(z - 1) > 0.01) ? String(z) : '';
  };
  fit();
  window.addEventListener('resize', fit);
})();

window.Live = (function () {
  const socket = io();
  const MY = location.pathname.split('/').filter(Boolean)[0];
  socket.on('game', (id) => { if (id && id !== MY) location.replace('/' + id + '/'); });
  socket.on('status', (st) => {
    const el = document.getElementById('status');
    if (el) el.textContent = `${st.mode} · ${st.connected ? 'conectado' : 'reconectando…'}`;
  });
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  let toastTimer;
  function toast(msg, ms = 1800) {
    const el = document.getElementById('toast'); if (!el) return;
    el.textContent = msg; el.style.opacity = 1;
    clearTimeout(toastTimer); toastTimer = setTimeout(() => (el.style.opacity = 0), ms);
  }
  // ---------- trilha sonora + efeitos (música original gerada no navegador) ----------
  socket.on('music', (opts) => { if (window.LiveMusic) window.LiveMusic.start(MY, opts); });
  socket.on('fx', (fx) => {
    const M = window.LiveMusic; if (!M) return;
    if (fx.kind === 'gift') M.sfx(fx.diamonds >= 100 ? 'big' : 'gift', fx.diamonds || 1);
    else if (fx.kind === 'follow' || fx.kind === 'share') M.sfx('follow');
    else if (fx.kind === 'band') M.sfx('hit');
    else if (fx.kind === 'hit') {
      if (fx.fx === 'ultimate' || fx.fx === 'meteor') M.sfx('big');
      else if (fx.fx === 'lightning') M.sfx('gift', 30);
      else if (fx.fx === 'crit') M.sfx('follow');
      else M.sfx('hit');
    }
  });
  let lastPhase = null; // fim de rodada (vitória, chefe derrotado, estouro) = explosão sonora
  socket.on('state', (st) => {
    const end = ['intermission', 'defeated', 'exploded'].includes(st.phase);
    if (end && lastPhase !== null && lastPhase !== st.phase && window.LiveMusic) window.LiveMusic.sfx('boom');
    lastPhase = st.phase;
  });

  return { socket, esc, toast };
})();
