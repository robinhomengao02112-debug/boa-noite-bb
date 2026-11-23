// Servidor principal: recebe eventos da live (TikTok ou simulador),
// roda o jogo ativo de forma autoritativa, faz rodízio entre jogos e envia o estado ao overlay.
import express from 'express';
import { createServer } from 'node:http';
import { Server } from 'socket.io';
import { readFileSync, mkdirSync, appendFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { GAMES } from './games/index.js';
import { createTikTokSource } from './sources/tiktok.js';
import { createSimulatorSource } from './sources/simulator.js';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const config = JSON.parse(readFileSync(path.join(ROOT, 'config.json'), 'utf8'));
const arg = (name) => process.argv.find((a) => a.startsWith(`--${name}=`))?.split('=')[1];
const SIMULATE = process.argv.includes('--simulate') || process.env.SIMULATE === '1';
const DATA_DIR = path.join(ROOT, 'data');
mkdirSync(DATA_DIR, { recursive: true });

const app = express();
const http = createServer(app);
const io = new Server(http);

// ---------- Jogo ativo + rodízio ----------
let gameId = arg('game') || config.activeGame || 'corrida';
let game = null;
let gameStartedAt = Date.now();
function loadGame(id) {
  const Cls = GAMES[id];
  if (!Cls) throw new Error(`Jogo desconhecido: "${id}". Disponíveis: ${Object.keys(GAMES).join(', ')}`);
  gameId = id;
  game = new Cls(config.games[id]);
  gameStartedAt = Date.now();
  console.log(`[jogo] ativo: ${id}`);
  io.emit('game', gameId);
}
loadGame(gameId);

function maybeRotate() {
  const r = config.rotation;
  if (!r?.enabled || !r.games?.length) return;
  if (Date.now() - gameStartedAt < r.minutesPerGame * 60000) return;
  // troca só entre rodadas, para não cortar uma partida no meio
  const between = ['intermission', 'defeated', 'exploded'].includes(game.snapshot().phase);
  if (!between) return;
  const i = r.games.indexOf(gameId);
  loadGame(r.games[(i + 1) % r.games.length]);
}

// "/" sempre mostra o jogo ativo (a URL do OBS nunca muda)
app.get('/', (_req, res) => res.redirect(`/${gameId}/`));
app.use(express.static(path.join(ROOT, 'public')));

const status = { mode: SIMULATE ? 'simulator' : 'tiktok', connected: false, lastEventAt: null, startedAt: new Date().toISOString(), reconnects: 0 };

// ---------- Registro de receita (estimada) ----------
function today() { return new Date().toISOString().slice(0, 10); }
function ledgerFile(day = today()) { return path.join(DATA_DIR, `ledger-${day}.jsonl`); }
function logGift(ev) {
  const line = { t: new Date().toISOString(), game: gameId, user: ev.user.id, gift: ev.giftName, diamonds: ev.totalDiamonds, sim: SIMULATE };
  appendFileSync(ledgerFile(), JSON.stringify(line) + '\n');
}
function dayStats(day = today()) {
  const f = ledgerFile(day);
  if (!existsSync(f)) return { day, gifts: 0, diamonds: 0, estUsd: 0, uniqueGifters: 0, byGame: {}, byHour: {} };
  const rows = readFileSync(f, 'utf8').trim().split('\n').filter(Boolean).map((l) => JSON.parse(l));
  const sum = (rs) => rs.reduce((s, r) => s + r.diamonds, 0);
  const group = (key) => rows.reduce((acc, r) => { const k = key(r); acc[k] = (acc[k] || 0) + r.diamonds; return acc; }, {});
  const diamonds = sum(rows);
  return {
    day,
    gifts: rows.length,
    diamonds,
    estUsd: +(diamonds * config.estimatedUsdPerDiamond).toFixed(2),
    uniqueGifters: new Set(rows.map((r) => r.user)).size,
    byGame: group((r) => r.game || 'corrida'),       // moedas por jogo -> qual jogo rende mais
    byHour: group((r) => r.t.slice(11, 13) + 'h'),   // moedas por hora (UTC) -> quais horários rendem
  };
}

// ---------- Eventos normalizados -> jogo ----------
function onEvent(ev) {
  status.lastEventAt = new Date().toISOString();
  if (ev.type === 'gift') logGift(ev);
  const fx = game.handle(ev);
  if (fx) io.emit('fx', fx);
}
function onStatus(s) {
  Object.assign(status, s);
  io.emit('status', status);
}

const source = SIMULATE
  ? createSimulatorSource({ onEvent, onStatus })
  : createTikTokSource({ username: config.tiktokUsername, apiKey: config.eulerApiKey, onEvent, onStatus });
source.start();

// ---------- Loop do jogo ----------
setInterval(() => {
  game.tick(0.1);
  maybeRotate();
  io.emit('state', game.snapshot());
}, 100);

io.on('connection', (sock) => {
  sock.emit('game', gameId);
  sock.emit('config', game.publicConfig());
  sock.emit('status', status);
  sock.emit('state', game.snapshot());
});

// ---------- Endpoints ----------
app.get('/health', (_req, res) => {
  const silentFor = status.lastEventAt ? (Date.now() - Date.parse(status.lastEventAt)) / 1000 : null;
  res.json({ ok: true, game: gameId, ...status, silentForSeconds: silentFor, uptimeSeconds: Math.round(process.uptime()) });
});
app.get('/api/stats', (req, res) => res.json(dayStats(req.query.day || today())));
// Troca manual de jogo (só aceita chamadas do próprio PC): http://localhost:3000/api/game/melancia
app.get('/api/game/:id', (req, res) => {
  const ip = req.socket.remoteAddress || '';
  if (!['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(ip)) return res.status(403).json({ error: 'somente local' });
  try { loadGame(req.params.id); res.json({ ok: true, game: gameId }); } catch (e) { res.status(400).json({ error: e.message }); }
});

http.listen(config.port, () => {
  console.log(`[live] modo=${status.mode} overlay=http://localhost:${config.port}/  saude=http://localhost:${config.port}/health`);
});

for (const sig of ['SIGINT', 'SIGTERM']) process.on(sig, () => { source.stop?.(); process.exit(0); });
process.on('uncaughtException', (e) => { console.error('[fatal]', e); process.exit(1); }); // o supervisor reinicia
