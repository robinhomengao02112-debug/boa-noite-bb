@echo off
REM Inicia o jogo com supervisor (reinicia sozinho se cair).
REM Coloque um atalho deste arquivo em shell:startup para iniciar junto com o Windows.
cd /d "%~dp0.."
if not exist node_modules (
  echo Instalando dependencias...
  call npm install
)
node scripts\supervisor.js
pause
