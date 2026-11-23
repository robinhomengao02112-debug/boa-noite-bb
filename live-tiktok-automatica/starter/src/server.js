// Servidor principal: recebe eventos da live (TikTok ou simulador),
// roda o jogo ativo de forma autoritativa, faz rodízio entre jogos e envia o estado ao overlay.
import express from 'express';
import { createServer } from 'node:http';
import { Server } from 'socket.io';
import { readFileSync, writeFileSync, mkdirSync, appendFileSync, existsSync } from 'node:fs';
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

// ---------- Aprendizado automático ----------
// Mede, durante a live real, quantas moedas cada jogo rende por minuto no ar.
// No rodízio "inteligente", jogos que rendem mais ganham mais tempo de tela,
// mas todos continuam sendo testados de vez em quando (o público muda).
const LEARN_FILE = path.join(DATA_DIR, SIMULATE ? 'aprendizado-simulador.json' : 'aprendizado.json'); // simulador não contamina o real
let learn = { games: {} };
try { if (existsSync(LEARN_FILE)) learn = JSON.parse(readFileSync(LEARN_FILE, 'utf8')); } catch { learn = { games: {} }; }
const lg = (id) => (learn.games[id] ||= { seconds: 0, coins: 0, gifts: 0 });
function saveLearn() { try { writeFileSync(LEARN_FILE, JSON.stringify(learn, null, 2)); } catch { /* ignore */ } }
setInterval(saveLearn, 60000);
function learnTable() {
  const ids = config.rotation?.games?.length ? config.rotation.games : Object.keys(GAMES);
  return ids.map((id) => {
    const g = lg(id);
    const minutes = g.seconds / 60;
    return { id, minutes: +minutes.toFixed(1), coins: g.coins, gifts: g.gifts, coinsPerMin: minutes > 0 ? +(g.coins / minutes).toFixed(2) : 0 };
  });
}
function pickNextGame(r) {
  const options = r.games.filter((id) => GAMES[id] && id !== gameId);
  if (!options.length) return gameId;
  if (!r.smart) return r.games[(r.games.indexOf(gameId) + 1) % r.games.length]; // rodízio simples, em ordem
  const table = learnTable().filter((t) => options.includes(t.id));
  const minMinutes = r.learnMinMinutes ?? 30;
  // 1) Explorar: jogo com pouco tempo de teste vai primeiro
  const untested = table.filter((t) => t.minutes < minMinutes).sort((a, b) => a.minutes - b.minutes);
  if (untested.length) return untested[0].id;
  // 2) De vez em quando, testa qualquer um (o gosto do público muda)
  if (Math.random() < (r.exploreChance ?? 0.2)) return options[Math.floor(Math.random() * options.length)];
  // 3) Aproveitar: sorteio com peso proporcional às moedas por minuto
  const avg = table.reduce((s, t) => s + t.coinsPerMin, 0) / table.length || 1;
  const weights = table.map((t) => t.coinsPerMin + 0.1 * avg);
  let x = Math.random() * weights.reduce((a, b) => a + b, 0);
  for (let i = 0; i < table.length; i++) { if ((x -= weights[i]) <= 0) return table[i].id; }
  return table[0].id;
}

function maybeRotate() {
  const r = config.rotation;
  if (!r?.enabled || !r.games?.length) return;
  if (Date.now() - gameStartedAt < r.minutesPerGame * 60000) return;
  // troca só entre rodadas, para não cortar uma partida no meio
  const between = ['intermission', 'defeated', 'exploded'].includes(game.snapshot().phase);
  if (!between) return;
  const next = pickNextGame(r);
  if (next !== gameId) loadGame(next); else gameStartedAt = Date.now();
}

// "/" sempre mostra o jogo ativo (a URL do OBS nunca muda)
app.get('/', (_req, res) => res.redirect(`/${gameId}/`));
app.use(express.static(path.join(ROOT, 'public')));

const status = { mode: SIMULATE ? 'simulator' : 'tiktok', connected: false, lastEventAt: null, startedAt: new Date().toISOString(), reconnects: 0 };

// ---------- Registro de receita (estimada) ----------
function today() { return new Date().toISOString().slice(0, 10); }
function ledgerFile(day = today()) { return path.join(DATA_DIR, `ledger-${SIMULATE ? 'simulador-' : ''}${day}.jsonl`); }
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
  if (ev.type === 'gift') {
    logGift(ev);
    const g = lg(gameId); g.coins += ev.totalDiamonds; g.gifts++;
  }
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
  if (status.connected) lg(gameId).seconds += 0.1; // só conta tempo com a live conectada
  maybeRotate();
  io.emit('state', game.snapshot());
}, 100);

io.on('connection', (sock) => {
  sock.emit('game', gameId);
  sock.emit('music', { enabled: true, volume: 0.5, sfx: true, ...(config.music || {}) });
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
app.get('/api/aprendizado', (_req, res) => res.json({ smart: !!config.rotation?.smart, current: gameId, table: learnTable() }));
// Troca manual de jogo (só aceita chamadas do próprio PC): http://localhost:3000/api/game/melancia
app.get('/api/game/:id', (req, res) => {
  const ip = req.socket.remoteAddress || '';
  if (!['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(ip)) return res.status(403).json({ error: 'somente local' });
  try { loadGame(req.params.id); res.json({ ok: true, game: gameId }); } catch (e) { res.status(400).json({ error: e.message }); }
});

http.listen(config.port, '127.0.0.1', () => { // só o próprio PC acessa (sem alerta de firewall)
  console.log(`[live] modo=${status.mode} overlay=http://127.0.0.1:${config.port}/  saude=http://127.0.0.1:${config.port}/health`);
});

for (const sig of ['SIGINT', 'SIGTERM']) process.on(sig, () => { saveLearn(); source.stop?.(); process.exit(0); });
process.on('uncaughtException', (e) => { console.error('[fatal]', e); process.exit(1); }); // o supervisor reinicia
