@echo off
REM Desliga a live e fecha o controlador e o jogo (o OBS fica aberto).
curl -s -X POST http://127.0.0.1:3001/api/desligar -H "Content-Type: application/json" -d "{}" >nul 2>&1
ping -n 9 127.0.0.1 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0parar.ps1"
echo Controlador encerrado.
ping -n 4 127.0.0.1 >nul
