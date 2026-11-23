@echo off
REM ==============================================
REM  INSTALADOR - Live Interativa Automatica
REM  De dois cliques neste arquivo. So isso.
REM ==============================================
chcp 65001 >nul
cd /d "%~dp0"
if not exist "%~dp0starter\scripts\instalar.ps1" (
  echo ERRO: extraia o ZIP inteiro antes de rodar o instalador.
  pause
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0starter\scripts\instalar.ps1"
