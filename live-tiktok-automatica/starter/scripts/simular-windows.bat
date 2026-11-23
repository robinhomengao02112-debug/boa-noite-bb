@echo off
REM Roda o jogo com eventos FALSOS (simulador) para testar sem estar ao vivo.
cd /d "%~dp0.."
if not exist node_modules call npm install
start "" http://localhost:3000/
node src\server.js --simulate
pause
