@echo off
setlocal
title Projeto de Otimizacao
cd /d "%~dp0"
echo.
echo  Iniciando o Projeto de Otimizacao...
echo  Clique em "Sim" na janela de permissao do Windows.
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -File "%~dp0Otimizador.ps1"
if errorlevel 1 (
    echo.
    echo  Ocorreu um erro ao iniciar. Veja os detalhes na pasta Dados\Logs.
    pause
)
endlocal
