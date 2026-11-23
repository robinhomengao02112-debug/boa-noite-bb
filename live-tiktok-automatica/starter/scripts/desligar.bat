@echo off
REM Desliga a live (o jogo e o OBS continuam abertos, prontos para a proxima).
curl -s -X POST http://127.0.0.1:3001/api/desligar -H "Content-Type: application/json" -d "{}"
echo.
echo Live desligada.
timeout /t 3 >nul
