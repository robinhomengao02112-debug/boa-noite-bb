"use strict";
/* Enredo: histórias interativas gratuitas, offline e privadas. */

// ---------- armazenamento local (nada sai do aparelho) ----------
const KEY = "enredo_v1";
const DB = load();
function load() {
  try { const d = JSON.parse(localStorage.getItem(KEY)); if (d && typeof d === "object") return fill(d); } catch (e) {}
  return fill({});
}
function fill(d) {
  d.settings = Object.assign({ name: "", speed: 2, sound: true, hints: true }, d.settings || {});
  d.saves = d.saves || {}; d.endings = d.endings || {}; d.custom = d.custom || []; d.seen = d.seen || {};
  return d;
}
function save() { try { localStorage.setItem(KEY, JSON.stringify(DB)); } catch (e) { toast("Não foi possível salvar (armazenamento cheio?)"); } }

// ---------- utilidades ----------
const $ = s => document.querySelector(s);
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const clone = o => JSON.parse(JSON.stringify(o));
const pname = () => DB.settings.name.trim() || "Viajante";
const fmt = t => esc(String(t).replace(/\{nome\}/gi, pname()));
let toastT;
function toast(msg) { const t = $("#toast"); t.textContent = msg; t.classList.add("on"); clearTimeout(toastT); toastT = setTimeout(() => t.classList.remove("on"), 2200); }
const COLOR_OK = c => /^#[0-9a-f]{3,8}$/i.test(c || "");

// ---------- sons simples (WebAudio, sem arquivos) ----------
let AC;
function beep(f = 660, d = 0.06, type = "sine", v = 0.05) {
  if (!DB.settings.sound) return;
  try {
    AC = AC || new (window.AudioContext || window.webkitAudioContext)();
    const o = AC.createOscillator(), g = AC.createGain();
    o.type = type; o.frequency.value = f; g.gain.value = v;
    g.gain.exponentialRampToValueAtTime(0.0001, AC.currentTime + d);
    o.connect(g).connect(AC.destination); o.start(); o.stop(AC.currentTime + d);
  } catch (e) {}
}

// ---------- parser do roteiro ----------
const BGS = ["cidade", "quarto", "escola", "ceu", "floresta", "trem", "tunel", "palacio", "praia", "preto", "branco", "laboratorio", "telhado"];
const FXS = ["chuva", "petalas", "faiscas", "neve", "estrelas", "nenhum"];
const MOODS = ["normal", "feliz", "triste", "bravo", "surpreso"];
const POS = { esq: "esq", esquerda: "esq", dir: "dir", direita: "dir", centro: "centro", meio: "centro" };

function parse(script, chars) {
  const scenes = {}, errors = [], order = [];
  let cur = null;
  const lines = String(script).split(/\r?\n/);
  const need = (n, msg) => errors.push(`Linha ${n}: ${msg}`);
  lines.forEach((raw, i) => {
    const n = i + 1, line = raw.trim();
    if (!line || line.startsWith("//")) return;
    if (line.startsWith("#")) {
      cur = line.slice(1).trim();
      if (!cur) return need(n, "cena sem nome");
      if (scenes[cur]) need(n, `cena "${cur}" repetida`);
      scenes[cur] = []; order.push(cur); return;
    }
    if (!cur) { cur = "inicio"; scenes[cur] = []; order.push(cur); }
    const ops = scenes[cur];
    if (line.startsWith("?")) {
      const m = line.match(/^\?\s*(.+?)\s*->\s*([^\s\[{]+)\s*(\[[^\]]*\])?\s*(\{[^}]*\})?\s*$/);
      if (!m) return need(n, "escolha deve ser: ? Texto -> cena");
      ops.push({ t: "choice", text: m[1], to: m[2], fx: m[3] ? parseEffects(m[3].slice(1, -1)) : [], cond: m[4] ? m[4].slice(1, -1).trim() : "", n });
      return;
    }
    if (line.startsWith("@")) {
      const sp = line.indexOf(" ");
      const cmd = (sp < 0 ? line.slice(1) : line.slice(1, sp)).toLowerCase();
      const arg = sp < 0 ? "" : line.slice(sp + 1).trim();
      const a = arg.split(/\s+/);
      switch (cmd) {
        case "bg": ops.push({ t: "bg", v: a[0] }); if (!BGS.includes(a[0])) need(n, `fundo "${a[0]}" não existe`); break;
        case "fx": ops.push({ t: "fx", v: a[0] || "nenhum" }); if (!FXS.includes(a[0] || "nenhum")) need(n, `efeito "${a[0]}" não existe`); break;
        case "show": ops.push({ t: "show", id: a[0], pos: POS[a[1]] || "centro", mood: MOODS.includes(a[2]) ? a[2] : "normal" }); if (chars && !chars[a[0]]) need(n, `personagem "${a[0]}" não cadastrado`); break;
        case "mood": ops.push({ t: "mood", id: a[0], mood: a[1] || "normal" }); break;
        case "hide": ops.push({ t: "hide", id: a[0] || "todos" }); break;
        case "set": ops.push({ t: "set", fx: parseEffects(arg) }); break;
        case "if": { const m = arg.match(/^(.+?)\s*->\s*(\S+)$/); if (!m) need(n, "@if deve ser: @if var>=2 -> cena"); else ops.push({ t: "if", cond: m[1], to: m[2], n }); break; }
        case "goto": ops.push({ t: "goto", to: a[0], n }); break;
        case "cine": ops.push({ t: "cine", text: arg }); break;
        case "flash": ops.push({ t: "flash" }); break;
        case "shake": ops.push({ t: "shake" }); break;
        case "end": { const [title, desc] = arg.split("|").map(s => s.trim()); ops.push({ t: "end", title: title || "Fim", desc: desc || "" }); break; }
        default: need(n, `comando "@${cmd}" desconhecido`);
      }
      return;
    }
    const m = line.match(/^([a-zA-Z0-9_]+)(?:\(([a-z]+)\))?\s*:\s*(.+)$/);
    if (m && (!chars || chars[m[1]])) ops.push({ t: "say", who: m[1], mood: m[2] || "", text: m[3] });
    else ops.push({ t: "say", who: "", text: line });
  });
  // valida destinos
  for (const id of order) for (const op of scenes[id]) {
    if ((op.t === "choice" || op.t === "goto" || op.t === "if") && !scenes[op.to]) errors.push(`Linha ${op.n}: cena "${op.to}" não existe`);
  }
  if (!order.length) errors.push("O roteiro está vazio.");
  return { scenes, start: scenes.inicio ? "inicio" : order[0], order, errors };
}
function parseEffects(s) {
  return s.split(",").map(x => x.trim()).filter(Boolean).map(x => {
    const m = x.match(/^([a-zA-Z0-9_]+)\s*(=|\+|-)\s*(-?\d+)$/);
    return m ? { v: m[1], op: m[2], n: +m[3] } : null;
  }).filter(Boolean);
}
function evalCond(c, vars) {
  if (!c) return true;
  return c.split(/\s*(?:&&|\be\b)\s*/).every(part => {
    const m = part.trim().match(/^([a-zA-Z0-9_]+)\s*(>=|<=|==|!=|>|<)\s*(-?\d+)$/);
    if (!m) return false;
    const v = vars[m[1]] || 0, n = +m[3];
    return { ">=": v >= n, "<=": v <= n, "==": v === n, "!=": v !== n, ">": v > n, "<": v < n }[m[2]];
  });
}

// ---------- retratos em SVG gerados por código ----------
function shade(hex, amt) {
  let h = hex.replace("#", ""); if (h.length === 3) h = h.split("").map(c => c + c).join("");
  const num = parseInt(h.slice(0, 6), 16);
  const ch = s => Math.max(0, Math.min(255, ((num >> s) & 255) + amt));
  return "#" + ((ch(16) << 16) | (ch(8) << 8) | ch(0)).toString(16).padStart(6, "0");
}
function portrait(c, mood = "normal") {
  const hair = COLOR_OK(c.hair) ? c.hair : "#333", skin = COLOR_OK(c.skin) ? c.skin : "#f1d2bd";
  const eye = COLOR_OK(c.eyes) ? c.eyes : "#5a3d2b", acc = COLOR_OK(c.accent) ? c.accent : hair;
  const hd = shade(hair, -35), cloth = shade(acc, -90), cloth2 = shade(acc, -50);
  const st = c.style || "curto";
  let back = "", front = "", top = "";
  if (st === "longo") back = `<path d="M40 110 Q38 40 100 38 Q162 40 160 110 L172 270 Q100 290 28 270 Z" fill="${hd}"/>`;
  if (st === "chiquinhas") back = `<path d="M52 95 Q10 120 22 220 Q34 250 50 230 Q40 160 66 110Z M148 95 Q190 120 178 220 Q166 250 150 230 Q160 160 134 110Z" fill="${hair}"/><circle cx="56" cy="92" r="9" fill="${acc}"/><circle cx="144" cy="92" r="9" fill="${acc}"/>`;
  if (st === "coque") back = `<circle cx="100" cy="42" r="26" fill="${hair}"/><path d="M48 105 Q46 48 100 46 Q154 48 152 105 Z" fill="${hd}"/>`;
  if (st === "curto" || st === "espetado" || st === "chapeu") back = `<path d="M46 112 Q42 44 100 42 Q158 44 154 112 Z" fill="${hd}"/>`;
  if (st === "espetado") front = `<path d="M44 108 L40 62 L62 78 L66 36 L86 66 L100 26 L114 66 L134 36 L138 78 L160 62 L156 108 Q140 80 100 76 Q60 80 44 108Z" fill="${hair}"/>`;
  else if (st === "chapeu") top = `<path d="M30 74 Q100 58 170 74 L168 84 Q100 70 32 84Z" fill="#111"/><path d="M58 76 Q60 20 100 18 Q140 20 142 76Z" fill="#161616"/><rect x="58" y="62" width="84" height="8" fill="${acc}"/>`;
  else front = `<path d="M46 110 Q44 50 100 48 Q156 50 154 110 Q146 84 128 78 Q120 96 100 92 Q86 98 74 82 Q56 88 46 110Z" fill="${hair}"/>`;
  const eyeY = 122;
  let eyes, brows, mouth, extra = "";
  const iris = (x, s = 1) => `<ellipse cx="${x}" cy="${eyeY}" rx="${10 * s}" ry="${13 * s}" fill="#fff"/><ellipse cx="${x}" cy="${eyeY + 1}" rx="${8 * s}" ry="${11 * s}" fill="${eye}"/><ellipse cx="${x}" cy="${eyeY + 2}" rx="${4 * s}" ry="${6 * s}" fill="${shade(eye, -90)}"/><circle cx="${x - 3}" cy="${eyeY - 4}" r="${3 * s}" fill="#fff"/>`;
  eyes = iris(78) + iris(122);
  brows = `<path d="M64 102 Q78 96 90 101" stroke="${hd}" stroke-width="4" fill="none" stroke-linecap="round"/><path d="M110 101 Q122 96 136 102" stroke="${hd}" stroke-width="4" fill="none" stroke-linecap="round"/>`;
  mouth = `<path d="M92 160 Q100 164 108 160" stroke="#8a4a45" stroke-width="3" fill="none" stroke-linecap="round"/>`;
  if (mood === "feliz") {
    eyes = `<path d="M66 124 Q78 110 90 124" stroke="${shade(eye, -80)}" stroke-width="5" fill="none" stroke-linecap="round"/><path d="M110 124 Q122 110 134 124" stroke="${shade(eye, -80)}" stroke-width="5" fill="none" stroke-linecap="round"/>`;
    mouth = `<path d="M88 156 Q100 172 112 156 Z" fill="#a6514b"/>`;
    extra = `<ellipse cx="66" cy="146" rx="10" ry="5" fill="#ff7b9c" opacity=".45"/><ellipse cx="134" cy="146" rx="10" ry="5" fill="#ff7b9c" opacity=".45"/>`;
  } else if (mood === "triste") {
    brows = `<path d="M66 100 Q78 104 90 98" stroke="${hd}" stroke-width="4" fill="none" stroke-linecap="round"/><path d="M110 98 Q122 104 134 100" stroke="${hd}" stroke-width="4" fill="none" stroke-linecap="round"/>`;
    mouth = `<path d="M92 164 Q100 158 108 164" stroke="#8a4a45" stroke-width="3" fill="none" stroke-linecap="round"/>`;
    extra = `<path d="M126 134 q4 8 0 12 q-4 -4 0 -12" fill="#9fd8ff" opacity=".9"/>`;
  } else if (mood === "bravo") {
    brows = `<path d="M64 98 L90 108" stroke="${hd}" stroke-width="5" stroke-linecap="round"/><path d="M110 108 L136 98" stroke="${hd}" stroke-width="5" stroke-linecap="round"/>`;
    mouth = `<path d="M90 162 Q100 156 110 162" stroke="#8a4a45" stroke-width="3.5" fill="none" stroke-linecap="round"/>`;
  } else if (mood === "surpreso") {
    eyes = iris(78, 1.15) + iris(122, 1.15);
    brows = `<path d="M64 96 Q78 88 90 95" stroke="${hd}" stroke-width="4" fill="none" stroke-linecap="round"/><path d="M110 95 Q122 88 136 96" stroke="${hd}" stroke-width="4" fill="none" stroke-linecap="round"/>`;
    mouth = `<ellipse cx="100" cy="162" rx="6" ry="8" fill="#8a4a45"/>`;
  }
  return `<svg viewBox="0 0 200 300" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
  ${back}
  <path d="M28 300 Q30 220 100 210 Q170 220 172 300Z" fill="${cloth}"/>
  <path d="M78 212 L100 250 L122 212Z" fill="${cloth2}"/>
  <rect x="86" y="170" width="28" height="44" rx="12" fill="${shade(skin, -18)}"/>
  <ellipse cx="100" cy="122" rx="54" ry="62" fill="${skin}"/>
  <ellipse cx="46" cy="126" rx="7" ry="12" fill="${shade(skin, -10)}"/><ellipse cx="154" cy="126" rx="7" ry="12" fill="${shade(skin, -10)}"/>
  ${eyes}${brows}
  <path d="M100 132 L97 146 L102 146" stroke="${shade(skin, -40)}" stroke-width="2" fill="none" stroke-linecap="round"/>
  ${mouth}${extra}${front}${top}
</svg>`;
}

// ---------- cenários ----------
function rand(seed) { let s = seed; return () => (s = (s * 9301 + 49297) % 233280) / 233280; }
function skyline(seed, color, win, h = 55) {
  const r = rand(seed); let x = 0, out = "";
  while (x < 400) {
    const w = 20 + r() * 40, bh = 20 + r() * h; out += `<rect x="${x}" y="${100 - bh}" width="${w}" height="${bh}" fill="${color}"/>`;
    for (let wy = 100 - bh + 5; wy < 96; wy += 7) for (let wx = x + 4; wx < x + w - 4; wx += 7) if (r() > 0.55) out += `<rect x="${wx}" y="${wy}" width="3" height="3" fill="${win}" opacity="${0.4 + r() * 0.6}"/>`;
    x += w + 2;
  }
  return `<svg class="bgart" viewBox="0 0 400 100" preserveAspectRatio="xMidYMax slice">${out}</svg>`;
}
function bgArt(name) {
  switch (name) {
    case "cidade": return skyline(7, "#120a26", "#ff5ca8") + `<div class="neon n1">ABERTO 24H</div><div class="neon n2">ホテル</div>`;
    case "telhado": return skyline(3, "#0b0820", "#56d6ff", 30) + `<svg class="bgart" viewBox="0 0 400 100" preserveAspectRatio="xMidYMax slice"><rect x="0" y="86" width="400" height="14" fill="#07060f"/><path d="M300 86 L310 20 L320 86" stroke="#555" stroke-width="2" fill="none"/><circle cx="310" cy="20" r="3" fill="#ff3b6b" class="blink"/></svg>`;
    case "floresta": { const r = rand(5); let t = ""; for (let i = 0; i < 26; i++) { const x = r() * 400, h = 40 + r() * 50; t += `<path d="M${x} 100 L${x + 14} ${100 - h} L${x + 28} 100Z" fill="${i % 2 ? "#123a2a" : "#0d2c20"}"/>`; } return `<svg class="bgart" viewBox="0 0 400 100" preserveAspectRatio="xMidYMax slice">${t}</svg>`; }
    case "escola": return `<svg class="bgart" viewBox="0 0 400 100" preserveAspectRatio="xMidYMax slice"><rect x="90" y="30" width="220" height="70" fill="#e8dcc8"/><path d="M80 32 L200 0 L320 32Z" fill="#7b3b4b"/><rect x="190" y="58" width="20" height="42" fill="#6b4a33"/>${[110, 140, 230, 260].map(x => `<rect x="${x}" y="44" width="16" height="20" fill="#9cc6e6"/>`).join("")}<rect x="0" y="92" width="400" height="8" fill="#6aa86a"/></svg>`;
    case "palacio": return `<svg class="bgart" viewBox="0 0 400 100" preserveAspectRatio="xMidYMax slice">${[30, 100, 170, 230, 300, 370].map(x => `<rect x="${x - 8}" y="10" width="16" height="90" fill="#3a2a66" opacity=".7"/>`).join("")}<ellipse cx="200" cy="100" rx="190" ry="14" fill="#2a1d4d"/></svg>`;
    case "trem": return `<div class="train-win"><i></i><i></i><i></i></div><svg class="bgart" viewBox="0 0 400 100" preserveAspectRatio="xMidYMax slice"><rect x="0" y="78" width="400" height="22" fill="#1d2a2a"/>${[20, 120, 220, 320].map(x => `<rect x="${x}" y="70" width="70" height="10" rx="3" fill="#2f5a52"/>`).join("")}<rect x="0" y="0" width="400" height="6" fill="#283838"/></svg>`;
    case "tunel": return `<div class="tunnel"></div>`;
    case "laboratorio": return `<svg class="bgart" viewBox="0 0 400 100" preserveAspectRatio="xMidYMax slice">${[20, 90, 290, 350].map((x, i) => `<rect x="${x}" y="${30 + i * 4}" width="46" height="34" rx="3" fill="#0d1a24" stroke="#2fe0c0" stroke-width="1.5"/><path d="M${x + 4} ${50 + i * 4} l8 -6 l8 8 l8 -10 l10 6" stroke="#2fe0c0" fill="none"/>`).join("")}<rect x="0" y="86" width="400" height="14" fill="#091018"/></svg>`;
    case "praia": return `<svg class="bgart" viewBox="0 0 400 100" preserveAspectRatio="xMidYMax slice"><circle cx="300" cy="55" r="22" fill="#ffd27a"/><rect x="0" y="62" width="400" height="20" fill="#2f8fb5"/><path d="M0 80 Q200 70 400 80 L400 100 L0 100Z" fill="#f2d49b"/></svg>`;
    case "quarto": return `<svg class="bgart" viewBox="0 0 400 100" preserveAspectRatio="xMidYMax slice"><rect x="40" y="16" width="70" height="50" fill="#20304d" stroke="#e6cfa8" stroke-width="3"/><circle cx="90" cy="32" r="7" fill="#f4f0d8"/><rect x="220" y="66" width="150" height="26" rx="6" fill="#d98d9a"/><rect x="220" y="56" width="40" height="14" rx="5" fill="#f2e6e8"/><rect x="0" y="92" width="400" height="8" fill="#6d4c41"/></svg>`;
    default: return "";
  }
}

// ---------- partículas ----------
const FX = { kind: "nenhum", parts: [], raf: 0, cv: null };
function setFx(kind) {
  FX.kind = kind; FX.parts = [];
  const cv = FX.cv; if (!cv) return;
  const n = { chuva: 140, petalas: 40, faiscas: 60, neve: 90, estrelas: 90 }[kind] || 0;
  for (let i = 0; i < n; i++) FX.parts.push(newPart(kind, true));
  if (!FX.raf && n) FX.raf = requestAnimationFrame(fxLoop);
}
function newPart(k, init) {
  const W = FX.cv.width, H = FX.cv.height, r = Math.random;
  const p = { x: r() * W, y: init ? r() * H : -20, s: r() };
  if (k === "chuva") Object.assign(p, { vx: -1.5, vy: 14 + r() * 8, l: 10 + r() * 14 });
  if (k === "petalas") Object.assign(p, { vx: 0.6 + r(), vy: 1 + r() * 1.2, a: r() * 6 });
  if (k === "faiscas") Object.assign(p, { y: init ? r() * H : H + 10, vx: (r() - .5) * 1.5, vy: -(1 + r() * 2.5) });
  if (k === "neve") Object.assign(p, { vx: (r() - .5) * .6, vy: .6 + r() * 1.2 });
  if (k === "estrelas") Object.assign(p, { y: r() * H * .8, vx: 0, vy: 0, a: r() * 6 });
  return p;
}
function fxLoop(t) {
  const cv = FX.cv, g = cv.getContext("2d"), W = cv.width, H = cv.height, k = FX.kind;
  g.clearRect(0, 0, W, H);
  if (!FX.parts.length) { FX.raf = 0; return; }
  for (const p of FX.parts) {
    p.x += p.vx || 0; p.y += p.vy || 0;
    if (k === "chuva") { g.strokeStyle = "rgba(180,210,255,.45)"; g.lineWidth = 1; g.beginPath(); g.moveTo(p.x, p.y); g.lineTo(p.x + p.vx, p.y - p.l); g.stroke(); }
    if (k === "petalas") { p.a += .03; p.x += Math.sin(p.a) * .6; g.fillStyle = "rgba(255,170,205,.85)"; g.beginPath(); g.ellipse(p.x, p.y, 5, 3, p.a, 0, 7); g.fill(); }
    if (k === "faiscas") { g.fillStyle = `rgba(255,${150 + p.s * 100 | 0},60,${.5 + p.s * .5})`; g.fillRect(p.x, p.y, 2 + p.s * 2, 2 + p.s * 2); }
    if (k === "neve") { g.fillStyle = "rgba(255,255,255,.8)"; g.beginPath(); g.arc(p.x, p.y, 1 + p.s * 2.5, 0, 7); g.fill(); }
    if (k === "estrelas") { p.a += .04; g.fillStyle = `rgba(255,255,230,${.25 + (Math.sin(p.a) + 1) * .35})`; g.fillRect(p.x, p.y, 1 + p.s * 2, 1 + p.s * 2); }
    if (p.y > H + 30 || p.y < -40 || p.x < -30 || p.x > W + 30) Object.assign(p, newPart(k, false));
  }
  FX.raf = requestAnimationFrame(fxLoop);
}
function sizeFx() { if (!FX.cv) return; const r = FX.cv.getBoundingClientRect(); FX.cv.width = r.width; FX.cv.height = r.height; }
window.addEventListener("resize", () => { sizeFx(); if (FX.cv) setFx(FX.kind); });

// ---------- catálogo ----------
function allStories() { return [...STORIES, ...DB.custom]; }
function getStory(id) { return allStories().find(s => s.id === id); }
const compiled = {};
function compile(story) {
  if (!compiled[story.id] || compiled[story.id].src !== story.script) compiled[story.id] = Object.assign(parse(story.script, story.chars), { src: story.script });
  return compiled[story.id];
}
function endingsOf(story) { const c = compile(story); const out = []; for (const id of c.order) for (const op of c.scenes[id]) if (op.t === "end") out.push(op); return out; }

// ---------- roteamento ----------
const view = () => $("#view");
function go(hash) { location.hash = hash; }
window.addEventListener("hashchange", route);
function route() {
  stopPlayer();
  const h = location.hash.replace(/^#\/?/, "").split("/");
  document.querySelectorAll("#tabs button").forEach(b => b.classList.toggle("on", b.dataset.tab === (h[0] || "inicio")));
  document.body.classList.toggle("playing", h[0] === "jogar");
  if (h[0] === "jogar") return play(decodeURIComponent(h[1] || ""), h[2] === "novo");
  if (h[0] === "finais") return endingsView();
  if (h[0] === "criar") return h[1] ? editorView(decodeURIComponent(h[1])) : creatorList();
  if (h[0] === "config") return settingsView();
  if (h[0] === "historia") return storyView(decodeURIComponent(h[1] || ""));
  homeView();
}
document.querySelectorAll("#tabs button").forEach(b => b.onclick = () => go("#/" + (b.dataset.tab === "inicio" ? "" : b.dataset.tab)));

// ---------- telas ----------
function cover(s, big) {
  const c = s.cover || {}; const a = COLOR_OK(c.from) ? c.from : "#222", b = COLOR_OK(c.to) ? c.to : "#666";
  const ch = Object.values(s.chars || {})[0];
  return `<div class="cover ${big ? "big" : ""}" style="background:linear-gradient(160deg,${a},${b})">
    <div class="cover-art">${bgArt(c.art || "cidade")}</div>
    ${ch ? `<div class="cover-char">${portrait(ch, "normal")}</div>` : ""}
  </div>`;
}
function progressOf(s) { const all = endingsOf(s).length, got = Object.keys(DB.endings[s.id] || {}).length; return { all, got }; }

function homeView() {
  if (!DB.settings.name) return welcomeView();
  const list = allStories();
  const cont = list.filter(s => DB.saves[s.id]);
  view().innerHTML = `
  <header class="top"><div><h1>Enredo</h1><p class="muted">Olá, ${esc(pname())}. Que história vamos viver hoje?</p></div>
    <span class="pill free">100% grátis</span></header>
  ${cont.length ? `<h2>Continuar</h2><div class="row-scroll">${cont.map(s => `
    <button class="cont" data-play="${esc(s.id)}">${cover(s)}<b>${esc(s.title)}</b><small>Continuar ▶</small></button>`).join("")}</div>` : ""}
  <h2>Histórias</h2>
  <div class="grid">${list.map(s => { const p = progressOf(s); return `
    <button class="card" data-story="${esc(s.id)}">${cover(s)}
      <div class="card-b"><b>${esc(s.title)}</b><small>${esc(s.genre || "Original")}</small>
      <div class="bar"><i style="width:${p.all ? p.got / p.all * 100 : 0}%"></i></div><small class="muted">${p.got}/${p.all} finais</small></div>
    </button>`; }).join("")}
    <button class="card new" data-href="#/criar"><span>＋</span><b>Criar a sua história</b><small>Escreva, teste e compartilhe</small></button>
  </div>
  <p class="safe">🔒 Sem anúncios, sem conta e sem rastreamento. Tudo fica salvo só neste aparelho.</p>`;
  view().querySelectorAll("[data-story]").forEach(b => b.onclick = () => go("#/historia/" + encodeURIComponent(b.dataset.story)));
  view().querySelectorAll("[data-play]").forEach(b => b.onclick = () => go("#/jogar/" + encodeURIComponent(b.dataset.play)));
}

function welcomeView() {
  view().innerHTML = `<section class="welcome">
    <div class="logo">✦</div><h1>Bem-vindo ao Enredo</h1>
    <p>Histórias interativas em que <b>você</b> é o protagonista. Cada escolha muda o rumo, as relações e o final.</p>
    <ul class="feat"><li>🎬 Cenas cinematográficas animadas</li><li>💗 Afinidade com personagens</li><li>🔀 Vários finais por história</li><li>✍️ Crie e compartilhe as suas próprias histórias</li><li>🔒 Grátis, offline e privado</li></ul>
    <label>Como devemos te chamar?<input id="nm" maxlength="20" placeholder="Seu nome ou apelido" autocomplete="nickname"></label>
    <button class="btn primary" id="startBtn">Começar</button></section>`;
  const f = () => { const v = $("#nm").value.trim(); if (!v) return toast("Digite um nome"); DB.settings.name = v; save(); homeView(); };
  $("#startBtn").onclick = f; $("#nm").onkeydown = e => e.key === "Enter" && f(); $("#nm").focus();
}

function storyView(id) {
  const s = getStory(id); if (!s) return homeView();
  const p = progressOf(s), got = DB.endings[s.id] || {};
  view().innerHTML = `
  <button class="back" data-back>‹ Voltar</button>
  ${cover(s, true)}
  <h1>${esc(s.title)}</h1><p class="muted">${esc(s.genre || "")}</p>
  <p>${esc(s.synopsis || "")}</p>
  <div class="chars">${Object.values(s.chars || {}).map(c => `<div class="chip"><div class="mini">${portrait(c)}</div>${esc(c.name)}</div>`).join("")}</div>
  <div class="actions">
    ${DB.saves[s.id] ? `<button class="btn primary" id="cont">▶ Continuar</button><button class="btn" id="novo">↻ Recomeçar</button>` : `<button class="btn primary" id="novo">▶ Começar</button>`}
  </div>
  <h2>Finais (${p.got}/${p.all})</h2>
  <div class="endlist">${endingsOf(s).map(e => got[e.title] ? `<div class="end got"><b>🏆 ${esc(e.title)}</b><small>${esc(e.desc)}</small></div>` : `<div class="end"><b>🔒 ???</b><small>Final ainda não descoberto</small></div>`).join("")}</div>`;
  if ($("#cont")) $("#cont").onclick = () => go("#/jogar/" + encodeURIComponent(s.id));
  $("#novo").onclick = () => { if (DB.saves[s.id] && !confirm("Recomeçar apaga o progresso atual desta história. Continuar?")) return; delete DB.saves[s.id]; save(); go("#/jogar/" + encodeURIComponent(s.id) + "/novo"); };
}

function endingsView() {
  const list = allStories();
  let tot = 0, got = 0;
  const html = list.map(s => { const p = progressOf(s); tot += p.all; got += p.got; const g = DB.endings[s.id] || {};
    return `<h2>${esc(s.title)} <small class="muted">${p.got}/${p.all}</small></h2><div class="endlist">${endingsOf(s).map(e => g[e.title] ? `<div class="end got"><b>🏆 ${esc(e.title)}</b><small>${esc(e.desc)}</small><small class="muted">${new Date(g[e.title]).toLocaleDateString("pt-BR")}</small></div>` : `<div class="end"><b>🔒 ???</b></div>`).join("")}</div>`; }).join("");
  view().innerHTML = `<header class="top"><div><h1>Coleção de finais</h1><p class="muted">${got} de ${tot} finais descobertos</p></div></header>
  <div class="bar big"><i style="width:${tot ? got / tot * 100 : 0}%"></i></div>${html}`;
}

function settingsView() {
  const st = DB.settings;
  view().innerHTML = `<header class="top"><div><h1>Ajustes</h1></div></header>
  <div class="form">
    <label>Seu nome na história<input id="sName" maxlength="20" value="${esc(st.name)}"></label>
    <label>Velocidade do texto<select id="sSpeed">${["Lenta", "Normal", "Rápida", "Instantânea"].map((t, i) => `<option value="${i + 1}" ${st.speed === i + 1 ? "selected" : ""}>${t}</option>`).join("")}</select></label>
    <label class="check"><input type="checkbox" id="sSound" ${st.sound ? "checked" : ""}> Sons de interface</label>
    <label class="check"><input type="checkbox" id="sHints" ${st.hints ? "checked" : ""}> Mostrar quando um personagem reage às suas escolhas</label>
  </div>
  <h2>Seus dados</h2>
  <p class="muted">O Enredo não tem servidor. Nada é enviado para a internet: progresso, finais e histórias criadas ficam só neste navegador. Faça um backup para levar para outro aparelho.</p>
  <div class="actions"><button class="btn" id="exp">⬇ Fazer backup</button><button class="btn" id="imp">⬆ Restaurar backup</button><button class="btn danger" id="wipe">🗑 Apagar tudo</button></div>
  <input type="file" id="impFile" accept="application/json,.json" hidden>
  <h2>Instalar</h2><p class="muted">No Android (Chrome): menu ⋮ → <b>Adicionar à tela inicial</b>. O app funciona sem internet depois de aberto uma vez.</p>
  <p class="muted small">Enredo é código aberto. Histórias e personagens originais.</p>`;
  $("#sName").onchange = e => { st.name = e.target.value.trim(); save(); toast("Nome salvo"); };
  $("#sSpeed").onchange = e => { st.speed = +e.target.value; save(); };
  $("#sSound").onchange = e => { st.sound = e.target.checked; save(); };
  $("#sHints").onchange = e => { st.hints = e.target.checked; save(); };
  $("#exp").onclick = () => download("enredo-backup.json", JSON.stringify(DB, null, 1));
  $("#imp").onclick = () => $("#impFile").click();
  $("#impFile").onchange = e => readJson(e.target.files[0], d => {
    if (!d || typeof d !== "object" || !d.settings) return toast("Arquivo de backup inválido");
    if (!confirm("Substituir seus dados atuais pelo backup?")) return;
    Object.keys(DB).forEach(k => delete DB[k]); Object.assign(DB, fill(sanitizeBackup(d))); save(); toast("Backup restaurado"); settingsView();
  });
  $("#wipe").onclick = () => { if (confirm("Apagar progresso, finais e histórias criadas? Isso não pode ser desfeito.")) { localStorage.removeItem(KEY); location.hash = "#/"; location.reload(); } };
}
function sanitizeBackup(d) {
  return { settings: d.settings, saves: d.saves || {}, endings: d.endings || {}, seen: d.seen || {}, custom: (Array.isArray(d.custom) ? d.custom : []).map(sanitizeStory).filter(Boolean) };
}
function download(name, text) {
  const a = document.createElement("a"); a.href = URL.createObjectURL(new Blob([text], { type: "application/json" })); a.download = name;
  document.body.appendChild(a); a.click(); setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 500);
}
function readJson(file, cb) { if (!file) return; if (file.size > 2e6) return toast("Arquivo grande demais"); const r = new FileReader(); r.onload = () => { try { cb(JSON.parse(r.result)); } catch (e) { toast("Arquivo inválido"); } }; r.readAsText(file); }

// ---------- player ----------
let P = null; // estado em execução
function stopPlayer() { if (P) { clearInterval(P.typer); clearTimeout(P.autoT); P = null; } cancelAnimationFrame(FX.raf); FX.raf = 0; FX.cv = null; }

function play(id, fresh) {
  const story = getStory(id); if (!story) return homeView();
  const c = compile(story);
  if (c.errors.length && !c.scenes[c.start]) { toast("Roteiro com erro"); return go("#/criar/" + encodeURIComponent(id)); }
  view().innerHTML = `
  <div id="stage" class="stage">
    <div id="bg" class="bg"></div>
    <canvas id="fx"></canvas>
    <div id="charsL" class="chars-layer"></div>
    <div class="bars"><i></i><i></i></div>
    <div id="cine" class="cine"></div>
    <div class="topbar">
      <button id="pHome" aria-label="Sair">✕</button>
      <span class="ttl">${esc(story.title)}</span>
      <button id="pBack" aria-label="Voltar fala">↶</button>
      <button id="pLog" aria-label="Histórico">☰</button>
      <button id="pAuto" aria-label="Automático">▶︎▶︎</button>
    </div>
    <div id="choices" class="choices"></div>
    <div id="box" class="box"><div id="who" class="who"></div><div id="txt" class="txt"></div><span class="next">▼</span></div>
    <div id="endc" class="endcard" hidden></div>
    <div id="logp" class="logp" hidden></div>
  </div>`;
  FX.cv = $("#fx"); sizeFx();
  const saved = !fresh && DB.saves[id];
  P = { story, c, auto: false, typing: false, full: "", typer: 0, autoT: 0, stack: [],
        st: saved ? clone(saved) : { scene: c.start, ip: 0, vars: {}, bg: "preto", fx: "nenhum", shown: {}, log: [] } };
  P.st.log = P.st.log || [];
  if (saved && saved.stack) P.stack = saved.stack;
  renderVisual();
  $("#stage").addEventListener("click", e => { if (e.target.closest(".topbar,.choices,.endcard,.logp")) return; advance(); });
  document.onkeydown = e => { if (!P) return; if (e.key === " " || e.key === "Enter") { e.preventDefault(); advance(); } if (e.key === "Backspace") rewind(); };
  $("#pHome").onclick = () => go("#/historia/" + encodeURIComponent(id));
  $("#pAuto").onclick = () => { P.auto = !P.auto; $("#pAuto").classList.toggle("on", P.auto); toast(P.auto ? "Modo automático ligado" : "Modo automático desligado"); if (P.auto && !P.typing && P.waiting === "say") scheduleAuto(); };
  $("#pLog").onclick = showLog;
  $("#pBack").onclick = rewind;
  run();
}

function renderVisual() {
  const s = P.st, bg = $("#bg");
  bg.className = "bg bg-" + s.bg; bg.innerHTML = bgArt(s.bg);
  setFx(s.fx);
  renderChars();
}
function renderChars(speaker) {
  const layer = $("#charsL"); if (!layer) return;
  const s = P.st, chars = P.story.chars || {};
  layer.innerHTML = Object.entries(s.shown).map(([id, v]) => chars[id] ? `<div class="ch pos-${v.pos} ${speaker && speaker !== id ? "dim" : ""} ${speaker === id ? "talk" : ""}" data-id="${esc(id)}">${portrait(chars[id], v.mood)}</div>` : "").join("");
}
function snapshot() { const s = clone(P.st); return s; }
function persist() { const s = snapshot(); s.stack = P.stack.slice(-40); DB.saves[P.story.id] = s; save(); }

function run() {
  if (!P) return;
  const { c, st } = P;
  let guard = 0;
  while (guard++ < 5000) {
    const ops = c.scenes[st.scene];
    if (!ops) return finish({ title: "Fim", desc: "" });
    const op = ops[st.ip];
    if (!op) return finish({ title: "Fim", desc: "A história terminou aqui." });
    switch (op.t) {
      case "bg": st.bg = op.v; { const bg = $("#bg"); bg.classList.add("fade"); setTimeout(() => { if (P) { bg.className = "bg bg-" + st.bg; bg.innerHTML = bgArt(st.bg); } }, 200); } st.ip++; break;
      case "fx": st.fx = op.v; setFx(op.v); st.ip++; break;
      case "show": st.shown[op.id] = { pos: op.pos, mood: op.mood }; for (const [k, v] of Object.entries(st.shown)) if (k !== op.id && v.pos === op.pos) delete st.shown[k]; renderChars(); st.ip++; break;
      case "mood": if (st.shown[op.id]) st.shown[op.id].mood = op.mood; renderChars(); st.ip++; break;
      case "hide": if (op.id === "todos") st.shown = {}; else delete st.shown[op.id]; renderChars(); st.ip++; break;
      case "set": applyFx(op.fx); st.ip++; break;
      case "if": if (evalCond(op.cond, st.vars)) { st.scene = op.to; st.ip = 0; } else st.ip++; break;
      case "goto": st.scene = op.to; st.ip = 0; break;
      case "flash": { const g = $("#stage"); g.classList.remove("flash"); void g.offsetWidth; g.classList.add("flash"); beep(1200, .15, "triangle", .03); } st.ip++; break;
      case "shake": { const g = $("#stage"); g.classList.remove("shake"); void g.offsetWidth; g.classList.add("shake"); beep(90, .25, "sawtooth", .05); } st.ip++; break;
      case "say": return say(op);
      case "cine": return cine(op);
      case "choice": return choices();
      case "end": return finish(op);
    }
  }
  toast("Loop infinito no roteiro");
}
function applyFx(list) {
  const chars = P.story.chars || {};
  for (const e of list) {
    const old = P.st.vars[e.v] || 0;
    P.st.vars[e.v] = e.op === "=" ? e.n : e.op === "+" ? old + e.n : old - e.n;
    const d = P.st.vars[e.v] - old;
    if (DB.settings.hints && chars[e.v] && d) setTimeout(() => toast(d > 0 ? `💗 ${chars[e.v].name} vai lembrar disso` : `💔 ${chars[e.v].name} não gostou`), 300);
  }
}
function pushHistory() { P.stack.push(snapshot()); if (P.stack.length > 60) P.stack.shift(); }
function say(op) {
  pushHistory(); persist();
  const chars = P.story.chars || {}, ch = chars[op.who];
  if (ch && op.mood && P.st.shown[op.who]) P.st.shown[op.who].mood = op.mood;
  renderChars(ch ? op.who : null);
  const box = $("#box"); box.classList.remove("hide"); box.classList.toggle("narr", !ch);
  $("#cine").classList.remove("on"); $("#stage").classList.remove("cinema");
  $("#who").textContent = ch ? ch.name : "";
  $("#who").style.background = ch && COLOR_OK(ch.accent) ? ch.accent : "";
  $("#who").hidden = !ch;
  const text = String(op.text).replace(/\{nome\}/gi, pname());
  P.st.log.push({ who: ch ? ch.name : "", text }); if (P.st.log.length > 200) P.st.log.shift();
  type(text, "#txt"); P.waiting = "say";
}
function type(text, sel) {
  const el = $(sel); clearInterval(P.typer); clearTimeout(P.autoT);
  P.full = text; P.typing = true; P.typeSel = sel;
  const sp = DB.settings.speed;
  if (sp >= 4) { el.textContent = text; P.typing = false; return scheduleAuto(); }
  let i = 0; el.textContent = "";
  const step = [0, 3, 2, 1][sp] || 2; // caracteres por tick
  P.typer = setInterval(() => { i += step; el.textContent = text.slice(0, i); if (i % 6 === 0) beep(520 + Math.random() * 80, .015, "square", .008); if (i >= text.length) { clearInterval(P.typer); P.typing = false; scheduleAuto(); } }, 28);
}
function scheduleAuto() { clearTimeout(P.autoT); if (P.auto && P.waiting !== "choice") P.autoT = setTimeout(() => P && P.auto && advance(), 900 + P.full.length * 35); }
function advance() {
  if (!P) return;
  if (P.typing) { clearInterval(P.typer); $(P.typeSel).textContent = P.full; P.typing = false; return scheduleAuto(); }
  if (P.waiting === "say" || P.waiting === "cine") { P.waiting = null; P.st.ip++; run(); }
}
function cine(op) {
  pushHistory(); persist();
  $("#box").classList.add("hide");
  const st = $("#stage"); st.classList.add("cinema");
  const el = $("#cine"); el.innerHTML = `<span>${fmt(op.text)}</span>`; el.classList.remove("on"); void el.offsetWidth; el.classList.add("on");
  beep(220, .6, "sine", .04); setTimeout(() => beep(330, .8, "sine", .03), 180);
  P.waiting = "cine"; P.full = op.text; P.typing = false; scheduleAuto();
}
function choices() {
  const ops = P.c.scenes[P.st.scene]; const list = [];
  let i = P.st.ip; while (ops[i] && ops[i].t === "choice") { list.push(ops[i]); i++; }
  const vis = list.filter(o => evalCond(o.cond, P.st.vars));
  pushHistory(); persist();
  P.waiting = "choice"; clearTimeout(P.autoT);
  const seenKey = P.story.id; DB.seen[seenKey] = DB.seen[seenKey] || {};
  const box = $("#choices");
  box.innerHTML = vis.map((o, k) => { const sk = P.st.scene + ">" + o.to; return `<button data-k="${k}" class="${DB.seen[seenKey][sk] ? "seen" : ""}">${fmt(o.text)}${list.length !== vis.length && o.cond ? ' <em>✦ desbloqueada</em>' : ""}</button>`; }).join("");
  box.classList.add("on");
  box.querySelectorAll("button").forEach(b => b.onclick = e => {
    e.stopPropagation(); const o = vis[+b.dataset.k];
    beep(880, .08, "sine", .05);
    DB.seen[seenKey][P.st.scene + ">" + o.to] = 1;
    P.st.log.push({ who: "➜", text: o.text.replace(/\{nome\}/gi, pname()) });
    box.classList.remove("on"); box.innerHTML = "";
    applyFx(o.fx); P.st.scene = o.to; P.st.ip = 0; P.waiting = null; run();
  });
}
function finish(op) {
  const id = P.story.id;
  DB.endings[id] = DB.endings[id] || {};
  const isNew = !DB.endings[id][op.title];
  DB.endings[id][op.title] = DB.endings[id][op.title] || Date.now();
  delete DB.saves[id]; save();
  $("#box").classList.add("hide");
  const p = progressOf(P.story);
  const e = $("#endc"); e.hidden = false;
  e.innerHTML = `<div class="endbox"><small>${isNew ? "NOVO FINAL DESBLOQUEADO" : "FINAL"}</small><h2>${fmt(op.title)}</h2><p>${fmt(op.desc)}</p>
    <p class="muted">${p.got}/${p.all} finais desta história</p>
    <div class="actions"><button class="btn primary" id="again">↻ Jogar de novo</button><button class="btn" id="out">Voltar</button></div></div>`;
  beep(523, .2); setTimeout(() => beep(659, .2), 160); setTimeout(() => beep(784, .4), 320);
  $("#again").onclick = () => { stopPlayer(); play(id, true); };
  $("#out").onclick = () => go("#/historia/" + encodeURIComponent(id));
  P.waiting = "end";
}
function rewind() {
  if (!P || P.stack.length < 2) return toast("Não dá para voltar mais");
  P.stack.pop(); const prev = P.stack.pop();
  clearInterval(P.typer); clearTimeout(P.autoT);
  P.st = clone(prev); $("#choices").classList.remove("on"); $("#choices").innerHTML = ""; $("#endc").hidden = true;
  renderVisual(); run();
}
function showLog() {
  const l = $("#logp"); if (!l.hidden) { l.hidden = true; return; }
  l.hidden = false;
  l.innerHTML = `<div class="loghead"><b>Histórico</b><button id="lc">✕</button></div><div class="logs">${P.st.log.map(x => `<p>${x.who ? `<b>${esc(x.who)}</b> ` : ""}${esc(x.text)}</p>`).join("") || "<p class='muted'>Nada ainda.</p>"}</div>`;
  $("#lc").onclick = () => l.hidden = true;
  const ls = l.querySelector(".logs"); ls.scrollTop = ls.scrollHeight;
}

// ---------- criador de histórias ----------
const TEMPLATE = `# inicio
@bg quarto
@fx petalas
@cine CAPÍTULO 1
Você acorda com uma mensagem misteriosa no celular.
@show ami centro feliz
ami: Bom dia, {nome}! Você viu a mensagem também?
? "Vi! O que será?" -> curioso [ami+1]
? "Não quero saber." -> fim_ruim

# curioso
ami(surpreso): Diz que tem um tesouro no telhado da escola!
@if ami>=1 -> fim_bom
@goto fim_ruim

# fim_bom
@bg telhado
@fx estrelas
ami(feliz): O tesouro era a amizade. Brincadeira... era um gato!
@end Amigo Felino | Você e Ami adotaram um gato.

# fim_ruim
@end Dia Comum | Nada aconteceu. Talvez amanhã.`;

function creatorList() {
  view().innerHTML = `<header class="top"><div><h1>Criar</h1><p class="muted">Escreva histórias interativas e compartilhe o arquivo com amigos.</p></div></header>
  <div class="actions"><button class="btn primary" id="nova">＋ Nova história</button><button class="btn" id="imp">⬆ Importar história</button></div>
  <input type="file" id="impFile" accept="application/json,.json" hidden>
  <div class="list">${DB.custom.map(s => `<div class="item"><div><b>${esc(s.title)}</b><small class="muted">${esc(s.genre || "")}</small></div>
    <div><button class="btn sm" data-ed="${esc(s.id)}">Editar</button><button class="btn sm primary" data-pl="${esc(s.id)}">▶</button></div></div>`).join("") || `<p class="muted">Você ainda não criou nenhuma história.</p>`}</div>
  <details class="help"><summary>Como escrever o roteiro</summary><pre>${esc(HELP)}</pre></details>`;
  $("#nova").onclick = () => {
    const s = { id: "u" + Date.now().toString(36), title: "Minha história", genre: "Original", synopsis: "Uma história criada por mim.", cover: { from: "#2b1055", to: "#d53369", art: "quarto" },
      chars: { ami: { name: "Ami", hair: "#ffb347", eyes: "#3d7bff", skin: "#f3d6c4", style: "chiquinhas", accent: "#ffb347" } }, script: TEMPLATE, custom: true };
    DB.custom.push(s); save(); go("#/criar/" + s.id);
  };
  $("#imp").onclick = () => $("#impFile").click();
  $("#impFile").onchange = e => readJson(e.target.files[0], d => {
    const s = sanitizeStory(d); if (!s) return toast("Arquivo de história inválido");
    s.id = "u" + Date.now().toString(36); DB.custom.push(s); save(); toast("História importada!"); creatorList();
  });
  view().querySelectorAll("[data-ed]").forEach(b => b.onclick = () => go("#/criar/" + encodeURIComponent(b.dataset.ed)));
  view().querySelectorAll("[data-pl]").forEach(b => b.onclick = () => { delete DB.saves[b.dataset.pl]; go("#/jogar/" + encodeURIComponent(b.dataset.pl) + "/novo"); });
}
const HELP = `# nome_da_cena        começa uma cena (a primeira se chama "inicio")
Texto solto           narração
ami: Olá!             fala do personagem "ami"
ami(feliz): Oba!      fala com expressão (normal, feliz, triste, bravo, surpreso)
@bg cidade            fundo: ${BGS.join(", ")}
@fx chuva             efeito: ${FXS.join(", ")}
@show ami esq feliz   mostra personagem (esq, dir, centro)
@hide ami             esconde (ou @hide todos)
@cine TÍTULO          momento cinematográfico
@flash  /  @shake     clarão / tremor
@set ami +1           muda afinidade ou variável
@if ami>=2 -> cena    pula se a condição for verdadeira
@goto cena            vai para outra cena
@end Título | texto   final da história
? Escolha -> cena [ami+1] {ami>=1}
                      escolha; [efeitos] e {condição} são opcionais
{nome}                vira o nome do jogador`;

function sanitizeStory(d) {
  if (!d || typeof d !== "object" || typeof d.script !== "string") return null;
  const chars = {};
  for (const [k, v] of Object.entries(d.chars || {}).slice(0, 12)) {
    if (!/^[a-zA-Z0-9_]{1,20}$/.test(k) || !v) continue;
    chars[k] = { name: String(v.name || k).slice(0, 30), hair: COLOR_OK(v.hair) ? v.hair : "#333333", eyes: COLOR_OK(v.eyes) ? v.eyes : "#553322", skin: COLOR_OK(v.skin) ? v.skin : "#f1d2bd", style: ["curto", "longo", "chiquinhas", "coque", "espetado", "chapeu"].includes(v.style) ? v.style : "curto", accent: COLOR_OK(v.accent) ? v.accent : "#888888" };
  }
  const c = d.cover || {};
  return { id: String(d.id || "u" + Date.now().toString(36)).replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 40), title: String(d.title || "Sem título").slice(0, 60), genre: String(d.genre || "").slice(0, 60), synopsis: String(d.synopsis || "").slice(0, 400),
    cover: { from: COLOR_OK(c.from) ? c.from : "#222244", to: COLOR_OK(c.to) ? c.to : "#884488", art: BGS.includes(c.art) ? c.art : "quarto" }, chars, script: d.script.slice(0, 200000), custom: true };
}

function editorView(id) {
  const s = DB.custom.find(x => x.id === id); if (!s) return creatorList();
  const charRows = () => Object.entries(s.chars).map(([k, c]) => `
    <div class="charrow" data-k="${esc(k)}"><div class="mini">${portrait(c)}</div>
      <div class="cf"><input data-f="id" value="${esc(k)}" title="código usado no roteiro"><input data-f="name" value="${esc(c.name)}" title="nome">
      <select data-f="style">${["curto", "longo", "chiquinhas", "coque", "espetado", "chapeu"].map(o => `<option ${c.style === o ? "selected" : ""}>${o}</option>`).join("")}</select>
      <span class="colors"><input type="color" data-f="hair" value="${esc(c.hair)}" title="cabelo"><input type="color" data-f="eyes" value="${esc(c.eyes)}" title="olhos"><input type="color" data-f="skin" value="${esc(c.skin)}" title="pele"><input type="color" data-f="accent" value="${esc(c.accent)}" title="roupa / destaque"></span>
      <button class="btn sm danger" data-del>✕</button></div></div>`).join("");
  view().innerHTML = `<button class="back" data-href="#/criar">‹ Minhas histórias</button>
  <div class="form">
    <label>Título<input id="eT" maxlength="60" value="${esc(s.title)}"></label>
    <label>Gênero<input id="eG" maxlength="60" value="${esc(s.genre)}"></label>
    <label>Sinopse<textarea id="eS" rows="2" maxlength="400">${esc(s.synopsis)}</textarea></label>
    <div class="row"><label>Capa (cor 1)<input type="color" id="eC1" value="${esc(s.cover.from)}"></label><label>Capa (cor 2)<input type="color" id="eC2" value="${esc(s.cover.to)}"></label>
    <label>Arte da capa<select id="eA">${BGS.map(b => `<option ${s.cover.art === b ? "selected" : ""}>${b}</option>`).join("")}</select></label></div>
  </div>
  <h2>Personagens</h2><div id="chs">${charRows()}</div><button class="btn sm" id="addCh">＋ Personagem</button>
  <h2>Roteiro</h2>
  <textarea id="eScript" class="code" spellcheck="false">${esc(s.script)}</textarea>
  <div id="errs" class="errs"></div>
  <div class="actions sticky"><button class="btn primary" id="eSave">💾 Salvar</button><button class="btn" id="ePlay">▶ Testar</button><button class="btn" id="eExp">⬇ Exportar</button><button class="btn danger" id="eDel">🗑</button></div>
  <details class="help"><summary>Guia rápido do roteiro</summary><pre>${esc(HELP)}</pre></details>`;
  const check = () => { const r = parse($("#eScript").value, s.chars); $("#errs").innerHTML = r.errors.length ? r.errors.slice(0, 8).map(e => `<div>⚠ ${esc(e)}</div>`).join("") : `<div class="ok">✓ Roteiro sem erros · ${r.order.length} cenas</div>`; return r; };
  const collect = () => {
    s.title = $("#eT").value.trim() || "Sem título"; s.genre = $("#eG").value.trim(); s.synopsis = $("#eS").value.trim();
    s.cover = { from: $("#eC1").value, to: $("#eC2").value, art: $("#eA").value }; s.script = $("#eScript").value;
    const nc = {};
    document.querySelectorAll(".charrow").forEach(r => { const g = f => r.querySelector(`[data-f="${f}"]`).value; const k = g("id").trim().replace(/[^a-zA-Z0-9_]/g, "") || "p" + Math.random().toString(36).slice(2, 6); nc[k] = { name: g("name").trim() || k, style: g("style"), hair: g("hair"), eyes: g("eyes"), skin: g("skin"), accent: g("accent") }; });
    s.chars = nc;
  };
  const doSave = () => { collect(); save(); delete compiled[s.id]; check(); toast("Salvo"); };
  $("#eScript").addEventListener("input", () => { clearTimeout(s._t); s._t = setTimeout(check, 400); });
  $("#chs").addEventListener("input", e => { const r = e.target.closest(".charrow"); if (!r) return; collect(); const k = Object.keys(s.chars)[[...document.querySelectorAll(".charrow")].indexOf(r)]; r.querySelector(".mini").innerHTML = portrait(s.chars[k]); });
  $("#chs").addEventListener("click", e => { if (!e.target.closest("[data-del]")) return; e.target.closest(".charrow").remove(); collect(); });
  $("#addCh").onclick = () => { collect(); let i = 1; while (s.chars["p" + i]) i++; s.chars["p" + i] = { name: "Personagem " + i, style: "curto", hair: "#5b3a29", eyes: "#2e7d32", skin: "#e8c1a0", accent: "#4a90e2" }; save(); editorView(id); };
  $("#eSave").onclick = doSave;
  $("#ePlay").onclick = () => { doSave(); const r = check(); if (r.errors.length && !confirm("O roteiro tem avisos. Testar mesmo assim?")) return; delete DB.saves[s.id]; save(); go("#/jogar/" + encodeURIComponent(s.id) + "/novo"); };
  $("#eExp").onclick = () => { collect(); const o = clone(s); delete o._t; download((s.title || "historia").replace(/[^\w-]+/g, "_") + ".enredo.json", JSON.stringify(o, null, 1)); };
  $("#eDel").onclick = () => { if (!confirm("Excluir esta história?")) return; DB.custom = DB.custom.filter(x => x.id !== id); delete DB.saves[id]; delete DB.endings[id]; save(); go("#/criar"); };
  check();
}

// ---------- início ----------
if ("serviceWorker" in navigator && location.protocol !== "file:") navigator.serviceWorker.register("sw.js").catch(() => {});
route();
document.addEventListener("click", e => {
  const h = e.target.closest("[data-href]"); if (h) return go(h.dataset.href);
  if (e.target.closest("[data-back]")) { if (history.length > 1) history.back(); else go("#/"); }
});
