@echo off
REM Liga a live usando a ultima chave guardada no painel.
cd /d "%~dp0.."
curl -s -m 3 http://127.0.0.1:3001/api/status >nul 2>&1
if errorlevel 1 (
  echo Iniciando o controlador...
  wscript "%~dp0iniciar-oculto.vbs"
  timeout /t 10 /nobreak >nul
)
curl -s -X POST http://127.0.0.1:3001/api/ligar -H "Content-Type: application/json" -d "{}"
echo.
echo Pedido enviado. Abrindo o painel...
start "" http://127.0.0.1:3001/
timeout /t 3 >nul
