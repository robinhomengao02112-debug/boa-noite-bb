// Configuração inicial (rodada pelo instalador). Pode ser rodada de novo sem problema.
// Uso: node scripts/setup.js [--user=seu_usuario]
// 1) grava o usuário do TikTok no config.json
// 2) liga o servidor WebSocket do OBS com senha (para o controlador comandar o OBS)
// 3) desliga janelas do OBS que travariam o modo automático
//    (assistente de 1ª execução, confirmação ao sair, assistente ao criar perfil)
//    e desliga a aceleração de GPU da fonte de navegador (evita tela preta)
// IMPORTANTE: o OBS precisa estar FECHADO ao rodar este script.
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { obsConfigDir } from '../src/obs.js';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const CONFIG = path.join(ROOT, 'config.json');
const arg = (n) => process.argv.find((a) => a.startsWith(`--${n}=`))?.slice(n.length + 3);

function obsRunning() {
  try {
    if (process.platform === 'win32') return /obs64\.exe/i.test(execFileSync('tasklist', ['/FI', 'IMAGENAME eq obs64.exe', '/NH'], { encoding: 'utf8', windowsHide: true }));
    execFileSync('pgrep', ['-x', 'obs']);
    return true;
  } catch { return false; }
}

// Lê/escreve INI simples preservando o resto do arquivo.
function setIni(file, section, values) {
  let text = existsSync(file) ? readFileSync(file, 'utf8').replace(/^﻿/, '') : '';
  const lines = text.split(/\r?\n/);
  let secStart = lines.findIndex((l) => l.trim() === `[${section}]`);
  if (secStart === -1) {
    if (lines.length && lines[lines.length - 1] !== '') lines.push('');
    lines.push(`[${section}]`);
    secStart = lines.length - 1;
  }
  let secEnd = lines.findIndex((l, i) => i > secStart && /^\[.*\]$/.test(l.trim()));
  if (secEnd === -1) secEnd = lines.length;
  for (const [k, v] of Object.entries(values)) {
    const idx = lines.findIndex((l, i) => i > secStart && i < secEnd && l.split('=')[0].trim() === k);
    if (idx >= 0) lines[idx] = `${k}=${v}`;
    else { lines.splice(secEnd, 0, `${k}=${v}`); secEnd++; }
  }
  writeFileSync(file, lines.join('\n').replace(/\n*$/, '\n'));
}

// ---------- 1) config.json ----------
const cfg = JSON.parse(readFileSync(CONFIG, 'utf8'));
const user = (arg('user') || '').trim().replace(/^@/, '');
if (user) cfg.tiktokUsername = user;
cfg.obs ||= {};

// ---------- 2) WebSocket do OBS ----------
if (obsRunning()) {
  console.error('ERRO: feche o OBS antes de rodar a configuração (ele sobrescreve os arquivos ao fechar).');
  process.exit(2);
}
const dir = obsConfigDir();
const wsDir = path.join(dir, 'plugin_config', 'obs-websocket');
mkdirSync(wsDir, { recursive: true });
const wsFile = path.join(wsDir, 'config.json');
let ws = {};
if (existsSync(wsFile)) { try { ws = JSON.parse(readFileSync(wsFile, 'utf8')); } catch { ws = {}; } }
const password = cfg.obs.password || ws.server_password || randomBytes(12).toString('hex');
ws = { ...ws, alerts_enabled: false, auth_required: true, first_load: false, server_enabled: true, server_password: password, server_port: cfg.obs.port || 4455 };
writeFileSync(wsFile, JSON.stringify(ws, null, 2));
cfg.obs.password = password;
cfg.obs.port = ws.server_port;

// ---------- 3) Desliga diálogos que travariam o modo automático ----------
// BrowserHWAccel=false: a fonte de navegador desenha por software. Gasta um pouco mais de CPU,
// mas evita a "tela preta" que alguns drivers de vídeo causam — mais seguro para rodar 24h.
const hw = cfg.obs.browserHwAccel === true ? 'true' : 'false';
// Atenção: no OBS, FirstRun=true significa "a primeira execução JÁ aconteceu" -> não abre o assistente.
// (Testado: com FirstRun=false o OBS abre o Assistente de Configuração Automática, que travaria tudo.)
const general = { FirstRun: 'true', ConfirmOnExit: 'false', BrowserHWAccel: hw };
const basic = { ConfigOnNewProfile: 'false' };
mkdirSync(dir, { recursive: true });
for (const f of ['user.ini', 'global.ini']) { // OBS 31+ divide as opções entre user.ini e global.ini
  const p = path.join(dir, f);
  setIni(p, 'General', general);
  setIni(p, 'Basic', basic);
}

writeFileSync(CONFIG, JSON.stringify(cfg, null, 2) + '\n');
console.log('OK: configuração concluída.');
console.log(`   usuário TikTok: ${cfg.tiktokUsername}`);
console.log(`   OBS WebSocket:  porta ${cfg.obs.port}, senha gravada em config.json`);
console.log(`   pasta do OBS:   ${dir}`);
