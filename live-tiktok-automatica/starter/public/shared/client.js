// Código comum a todos os overlays: conexão, troca automática de jogo, status e utilidades.
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
  return { socket, esc, toast };
})();
