// Supervisor (watchdog): mantém o servidor do jogo rodando 24h.
// - Reinicia o processo se ele cair.
// - Checa /health a cada 30s; se travar (sem resposta) por 3 checagens, reinicia.
// Uso: node scripts/supervisor.js [--simulate]
import { spawn } from 'node:child_process';
import { readFileSync, appendFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const { port } = JSON.parse(readFileSync(path.join(ROOT, 'config.json'), 'utf8'));
const extra = process.argv.slice(2);
mkdirSync(path.join(ROOT, 'data'), { recursive: true });
const LOG = path.join(ROOT, 'data', 'supervisor.log');
const log = (m) => { const l = `${new Date().toISOString()} ${m}`; console.log(l); appendFileSync(LOG, l + '\n'); };

let child = null, fails = 0, restarts = 0;

function start() {
  child = spawn(process.execPath, [path.join(ROOT, 'src', 'server.js'), ...extra], { stdio: 'inherit', cwd: ROOT });
  log(`servidor iniciado (pid ${child.pid}, reinícios: ${restarts})`);
  child.on('exit', (code) => {
    log(`servidor saiu (código ${code}); reiniciando em 3s`);
    restarts++; child = null;
    setTimeout(start, 3000);
  });
}

setInterval(async () => {
  if (!child) return;
  try {
    const r = await fetch(`http://localhost:${port}/health`, { signal: AbortSignal.timeout(5000) });
    const h = await r.json();
    fails = 0;
    if (h.silentForSeconds && h.silentForSeconds > 900) log(`aviso: nenhum evento da live há ${Math.round(h.silentForSeconds)}s (live caiu? sem público?)`);
  } catch {
    fails++;
    log(`health falhou (${fails}/3)`);
    if (fails >= 3) { fails = 0; log('travado: matando processo'); child.kill(); }
  }
}, 30000);

process.on('SIGINT', () => { child?.kill(); process.exit(0); });
start();
