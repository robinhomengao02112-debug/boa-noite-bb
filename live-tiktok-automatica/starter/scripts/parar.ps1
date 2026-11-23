# Fecha o controlador e o servidor do jogo (processos node desta pasta).
$root = Split-Path -Parent $PSScriptRoot
Get-CimInstance Win32_Process -Filter "Name='node.exe'" |
  Where-Object { $_.CommandLine -and ($_.CommandLine -match 'controller\.js|server\.js') } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
