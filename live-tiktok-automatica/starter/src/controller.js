// CONTROLADOR: o "piloto automático" da live.
// Roda escondido desde que o Windows liga e cuida de tudo:
//  - mantém o servidor do jogo vivo (reinicia se cair/travar)
//  - abre e configura o OBS sozinho (perfil vertical, cena, fonte do jogo)
//  - liga/desliga a transmissão quando você manda (painel, atalho ou horário)
//  - se a transmissão ou o OBS cair, religa sozinho
//  - painel de controle em http://127.0.0.1:3001
import express from 'express';
import { spawn } from 'node:child_process';
import { readFileSync, writeFileSync, existsSync, mkdirSync, appendFileSync, statSync, renameSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { ObsController } from './obs.js';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const CONFIG = path.join(ROOT, 'config.json');
const DATA = path.join(ROOT, 'data');
const STATE = path.join(DATA, 'estado.json');
const LOGF = path.join(DATA, 'controlador.log');
mkdirSync(DATA, { recursive: true });
const SIMULATE = process.argv.includes('--simulate');

let cfg = JSON.parse(readFileSync(CONFIG, 'utf8'));
const saveCfg = () => writeFileSync(CONFIG, JSON.stringify(cfg, null, 2) + '\n');

// ---------- log ----------
const recent = [];
function log(msg) {
  const line = `${new Date().toLocaleString('pt-BR')}  ${msg}`;
  console.log(line);
  recent.unshift(line); recent.length = Math.min(recent.length, 60);
  try {
    if (existsSync(LOGF) && statSync(LOGF).size > 5 * 1024 * 1024) renameSync(LOGF, LOGF + '.old');
    appendFileSync(LOGF, line + '\n');
  } catch { /* ignore */ }
}

// ---------- estado persistente (sobrevive a reinício do PC) ----------
const defaults = { wantLive: false, mode: cfg.obs?.enabled === false ? 'studio' : 'obs', server: '', key: '', autoStopAt: '', stopAtTs: 0 };
let state = { ...defaults };
try { if (existsSync(STATE)) state = { ...defaults, ...JSON.parse(readFileSync(STATE, 'utf8')) }; } catch { /* ignore */ }
const saveState = () => writeFileSync(STATE, JSON.stringify(state, null, 2));

function nextTimestamp(hhmm) {
  const m = /^(\d{1,2}):(\d{2})$/.exec(hhmm || '');
  if (!m) return 0;
  const d = new Date(); d.setSeconds(0, 0); d.setHours(+m[1], +m[2]);
  if (d.getTime() <= Date.now()) d.setDate(d.getDate() + 1);
  return d.getTime();
}

// ---------- servidor do jogo (processo filho) ----------
const game = { proc: null, restarts: 0, fails: 0, health: null, startedAt: 0 };
function startGame() {
  const args = [path.join(ROOT, 'src', 'server.js'), ...(SIMULATE ? ['--simulate'] : [])];
  game.proc = spawn(process.execPath, args, { cwd: ROOT, stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true });
  game.startedAt = Date.now();
  game.fails = 0;
  const pipe = (s) => s.on('data', (d) => String(d).split(/\r?\n/).filter(Boolean).forEach((l) => { if (/\[(jogo|tiktok|fatal|live)\]/.test(l)) log('jogo › ' + l); }));
  pipe(game.proc.stdout); pipe(game.proc.stderr);
  game.proc.on('exit', (code) => {
    log(`servidor do jogo saiu (código ${code}); reiniciando em 3s`);
    game.proc = null; game.restarts++;
    setTimeout(startGame, 3000);
  });
}
function restartGame() { if (game.proc) game.proc.kill(); else startGame(); }

async function gameFetch(p, opts) {
  const r = await fetch(`http://127.0.0.1:${cfg.port}${p}`, { signal: AbortSignal.timeout(5000), ...opts });
  return r.json();
}

async function checkGame() {
  if (!game.proc || Date.now() - game.startedAt < 8000) return;
  try { game.health = await gameFetch('/health'); game.fails = 0; }
  catch {
    game.fails++;
    log(`servidor do jogo não respondeu (${game.fails}/3)`);
    if (game.fails >= 3) { log('servidor do jogo travado: reiniciando'); game.fails = 0; game.proc.kill(); }
  }
}

// ---------- OBS ----------
const o = cfg.obs || {};
const obs = new ObsController({
  exe: o.path || null,
  password: o.password,
  port: o.port || 4455,
  overlayUrl: `http://127.0.0.1:${cfg.port}/`,
  profile: o.profile || 'LiveTikTok',
  collection: o.collection || 'LiveTikTok',
  scene: o.scene || 'Jogo',
  inputName: o.inputName || 'Jogo (overlay)',
  video: o.video || { baseWidth: 1080, baseHeight: 1920, outputWidth: 720, outputHeight: 1280, fps: 30 },
  videoBitrate: o.videoBitrate || 3000,
  audioBitrate: o.audioBitrate || 128,
  extraArgs: o.extraArgs || [],
  logger: (m) => log(m),
});
const stream = { failures: 0, message: '', lastStartTry: 0, wasStreaming: false, obsStatus: { connected: false, streaming: false } };

async function manageObs() {
  if (state.mode !== 'obs') { stream.message = 'Modo LIVE Studio: inicie/encerre a live pelo TikTok LIVE Studio.'; return; }
  if (!(await obs.ensureRunning(60000))) { stream.message = obs.lastError || 'OBS não abriu'; return; }
  try {
    await obs.ensureSetup();
  } catch (e) { log('erro ao configurar OBS: ' + e.message); obs.setupDone = false; }

  const st = await obs.status();
  stream.obsStatus = st;

  if (state.wantLive) {
    if (st.streaming) {
      if (!stream.wasStreaming) log('✅ transmissão no ar');
      stream.wasStreaming = true; stream.failures = 0; stream.message = st.reconnecting ? 'Reconectando ao TikTok…' : 'AO VIVO';
      return;
    }
    if (stream.wasStreaming) { log('⚠️ transmissão caiu; religando'); stream.wasStreaming = false; }
    if (!state.server || !state.key) { stream.message = 'Falta a chave: cole o Servidor e a Chave do LIVE Center no painel.'; return; }
    const wait = stream.failures >= 3 ? 60000 : 5000;
    if (Date.now() - stream.lastStartTry < wait) return;
    stream.lastStartTry = Date.now();
    try {
      await obs.setStreamTarget(state.server, state.key);
      log('iniciando transmissão…');
      const ok = await obs.startStream(30000);
      if (ok) { stream.failures = 0; stream.wasStreaming = true; stream.message = 'AO VIVO'; log('✅ transmissão no ar'); }
      else {
        stream.failures++;
        stream.message = stream.failures >= 3
          ? 'Não consegui conectar. A chave do TikTok muda a cada live: gere uma nova no LIVE Center e cole no painel.'
          : `Tentando conectar (tentativa ${stream.failures})…`;
        log(`falha ao iniciar transmissão (${stream.failures})`);
        try { await obs.stopStream(); } catch { /* ignore */ }
      }
    } catch (e) { stream.failures++; stream.message = 'Erro: ' + e.message; log('erro ao iniciar: ' + e.message); }
  } else {
    stream.message = 'Live desligada';
    if (st.streaming) { log('encerrando transmissão…'); await obs.stopStream(); log('⏹ transmissão encerrada'); }
    stream.wasStreaming = false;
  }
}

// ---------- ciclo principal ----------
let busy = false;
async function tick() {
  if (busy) return;
  busy = true;
  try {
    if (state.wantLive && state.stopAtTs && Date.now() >= state.stopAtTs) {
      log(`⏰ horário de desligar (${state.autoStopAt}) atingido`);
      state.wantLive = false; state.stopAtTs = 0; saveState();
    }
    await checkGame();
    await manageObs();
  } catch (e) { log('erro no ciclo: ' + e.message); }
  busy = false;
}

// ---------- ações ----------
function ligar({ server, key, autoStopAt } = {}) {
  if (server !== undefined && String(server).trim()) state.server = String(server).trim();
  if (key !== undefined && String(key).trim()) state.key = String(key).trim();
  if (autoStopAt !== undefined) state.autoStopAt = String(autoStopAt || '');
  state.stopAtTs = nextTimestamp(state.autoStopAt);
  state.wantLive = true;
  stream.failures = 0; stream.lastStartTry = 0;
  saveState();
  log('▶ LIGAR live' + (state.autoStopAt ? ` (desliga às ${state.autoStopAt})` : ''));
  setTimeout(tick, 100);
}
function desligar() {
  state.wantLive = false; state.stopAtTs = 0; saveState();
  log('■ DESLIGAR live');
  setTimeout(tick, 100);
}

// ---------- painel ----------
const app = express();
app.use(express.json());
app.use((req, res, next) => { // só aceita acesso do próprio PC
  const host = (req.headers.host || '').split(':')[0];
  if (!['127.0.0.1', 'localhost'].includes(host)) return res.status(403).send('somente local');
  next();
});
app.use(express.static(path.join(ROOT, 'painel')));

app.get('/api/status', async (_req, res) => {
  let stats = null, learning = null;
  try { stats = await gameFetch('/api/stats'); } catch { /* ignore */ }
  try { learning = await gameFetch('/api/aprendizado'); } catch { /* ignore */ }
  res.json({
    wantLive: state.wantLive, mode: state.mode, server: state.server, hasKey: !!state.key,
    autoStopAt: state.autoStopAt, stopAtTs: state.stopAtTs,
    stream: { message: stream.message, failures: stream.failures, ...stream.obsStatus },
    game: { running: !!game.proc, restarts: game.restarts, health: game.health },
    config: { user: cfg.tiktokUsername, activeGame: cfg.activeGame, rotation: cfg.rotation, games: Object.keys(cfg.games), overlayUrl: `http://127.0.0.1:${cfg.port}/` },
    stats, learning, log: recent.slice(0, 25),
  });
});
app.post('/api/ligar', (req, res) => { ligar(req.body || {}); res.json({ ok: true }); });
app.post('/api/desligar', (_req, res) => { desligar(); res.json({ ok: true }); });
app.post('/api/modo', (req, res) => {
  const m = req.body?.mode;
  if (!['obs', 'studio'].includes(m)) return res.status(400).json({ error: 'modo inválido' });
  state.mode = m; saveState(); log(`modo: ${m}`); res.json({ ok: true });
});
app.post('/api/jogo', async (req, res) => {
  const id = String(req.body?.id || '');
  if (!cfg.games[id]) return res.status(400).json({ error: 'jogo inválido' });
  try { res.json(await gameFetch(`/api/game/${encodeURIComponent(id)}`)); log(`jogo trocado para ${id}`); }
  catch (e) { res.status(500).json({ error: e.message }); }
});
app.post('/api/config', (req, res) => {
  const b = req.body || {};
  if (typeof b.user === 'string' && b.user.trim()) cfg.tiktokUsername = b.user.trim().replace(/^@/, '');
  if (typeof b.activeGame === 'string' && cfg.games[b.activeGame]) cfg.activeGame = b.activeGame;
  if (typeof b.rotationEnabled === 'boolean') cfg.rotation.enabled = b.rotationEnabled;
  if (typeof b.smart === 'boolean') cfg.rotation.smart = b.smart;
  if (Number(b.minutesPerGame) > 0) cfg.rotation.minutesPerGame = Number(b.minutesPerGame);
  saveCfg();
  log('configuração salva; reiniciando o jogo');
  restartGame();
  res.json({ ok: true });
});

const PANEL_PORT = cfg.panelPort || 3001;

// Evita rodar dois controladores ao mesmo tempo (ex.: clicou duas vezes no atalho).
async function alreadyRunning() {
  try { const r = await fetch(`http://127.0.0.1:${PANEL_PORT}/api/status`, { signal: AbortSignal.timeout(2000) }); return r.ok; }
  catch { return false; }
}

if (await alreadyRunning()) {
  console.log(`O controlador já está rodando. Painel: http://127.0.0.1:${PANEL_PORT}/`);
  process.exit(0);
}
const server = app.listen(PANEL_PORT, '127.0.0.1', () => {
  log(`painel em http://127.0.0.1:${PANEL_PORT}/`);
  log(`controlador iniciado (modo ${state.mode}${SIMULATE ? ', SIMULADOR' : ''}; live ${state.wantLive ? 'LIGADA' : 'desligada'})`);
  startGame();
  setTimeout(tick, 3000);
  setInterval(tick, 10000);
});
server.on('error', (e) => { log(`não consegui abrir o painel na porta ${PANEL_PORT}: ${e.message}`); process.exit(1); });

for (const sig of ['SIGINT', 'SIGTERM']) process.on(sig, () => { log('controlador encerrado'); game.proc?.removeAllListeners('exit'); game.proc?.kill(); process.exit(0); });
process.on('uncaughtException', (e) => log('erro inesperado: ' + (e.stack || e.message)));
process.on('unhandledRejection', (e) => log('erro inesperado: ' + (e?.stack || e)));
