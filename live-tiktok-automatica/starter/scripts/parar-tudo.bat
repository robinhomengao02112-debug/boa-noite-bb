@echo off
REM Desliga a live e fecha o controlador e o jogo (o OBS fica aberto).
curl -s -X POST http://127.0.0.1:3001/api/desligar -H "Content-Type: application/json" -d "{}" >nul 2>&1
timeout /t 8 /nobreak >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0parar.ps1"
echo Controlador encerrado.
timeout /t 3 >nul
