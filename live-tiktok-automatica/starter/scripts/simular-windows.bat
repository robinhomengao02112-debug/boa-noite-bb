@echo off
REM Testa o jogo com eventos FALSOS (sem live). Abre o jogo no navegador.
cd /d "%~dp0.."
if not exist node_modules call npm ci --omit=dev
start "" http://127.0.0.1:3000/
node src\server.js --simulate
pause
