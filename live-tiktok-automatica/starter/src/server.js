// Servidor principal: recebe eventos da live (TikTok ou simulador),
// roda o jogo de forma autoritativa e envia o estado para o overlay.
import express from 'express';
import { createServer } from 'node:http';
import { Server } from 'socket.io';
import { readFileSync, mkdirSync, appendFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { Game } from './game.js';
import { createTikTokSource } from './sources/tiktok.js';
import { createSimulatorSource } from './sources/simulator.js';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const config = JSON.parse(readFileSync(path.join(ROOT, 'config.json'), 'utf8'));
const SIMULATE = process.argv.includes('--simulate') || process.env.SIMULATE === '1';
const DATA_DIR = path.join(ROOT, 'data');
mkdirSync(DATA_DIR, { recursive: true });

const app = express();
const http = createServer(app);
const io = new Server(http);
app.use(express.static(path.join(ROOT, 'public')));

const game = new Game(config);
const status = { mode: SIMULATE ? 'simulator' : 'tiktok', connected: false, lastEventAt: null, startedAt: new Date().toISOString(), reconnects: 0 };

// ---------- Registro de receita (estimada) ----------
function today() { return new Date().toISOString().slice(0, 10); }
function ledgerFile(day = today()) { return path.join(DATA_DIR, `ledger-${day}.jsonl`); }
function logGift(ev) {
  const line = { t: new Date().toISOString(), user: ev.user.id, gift: ev.giftName, diamonds: ev.totalDiamonds, sim: SIMULATE };
  appendFileSync(ledgerFile(), JSON.stringify(line) + '\n');
}
function dayStats(day = today()) {
  const f = ledgerFile(day);
  if (!existsSync(f)) return { day, gifts: 0, diamonds: 0, estUsd: 0, uniqueGifters: 0 };
  const rows = readFileSync(f, 'utf8').trim().split('\n').filter(Boolean).map((l) => JSON.parse(l));
  const diamonds = rows.reduce((s, r) => s + r.diamonds, 0);
  return {
    day,
    gifts: rows.length,
    diamonds,
    estUsd: +(diamonds * config.estimatedUsdPerDiamond).toFixed(2),
    uniqueGifters: new Set(rows.map((r) => r.user)).size,
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
  io.emit('state', game.snapshot());
}, 100);

io.on('connection', (sock) => {
  sock.emit('config', { teams: config.teams, round: config.round, specialGifts: config.specialGifts });
  sock.emit('status', status);
  sock.emit('state', game.snapshot());
});

// ---------- Endpoints de monitoramento ----------
app.get('/health', (_req, res) => {
  const silentFor = status.lastEventAt ? (Date.now() - Date.parse(status.lastEventAt)) / 1000 : null;
  res.json({ ok: true, ...status, silentForSeconds: silentFor, uptimeSeconds: Math.round(process.uptime()) });
});
app.get('/api/stats', (req, res) => res.json(dayStats(req.query.day || today())));

http.listen(config.port, () => {
  console.log(`[live] modo=${status.mode} overlay=http://localhost:${config.port}/  saude=http://localhost:${config.port}/health`);
});

for (const sig of ['SIGINT', 'SIGTERM']) process.on(sig, () => { source.stop?.(); process.exit(0); });
process.on('uncaughtException', (e) => { console.error('[fatal]', e); process.exit(1); }); // o supervisor reinicia
