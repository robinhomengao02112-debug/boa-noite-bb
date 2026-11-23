# ============================================================
#  INSTALADOR — Live Interativa Automática (TikTok)
#  Faz tudo sozinho:
#   1. Instala Node.js e OBS Studio (via winget), se faltarem
#   2. Copia o projeto para  %USERPROFILE%\LiveTikTok
#   3. Instala as dependências
#   4. Configura o OBS (controle remoto + desliga janelas que travariam)
#   5. Ajusta o Windows para não dormir
#   6. Cria atalhos (Área de Trabalho + inicialização automática)
#   7. Inicia o controlador e abre o painel
#  Pode ser rodado de novo para ATUALIZAR (mantém suas configurações).
# ============================================================
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

function Info($m) { Write-Host "`n==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "    OK: $m" -ForegroundColor Green }
function Warn($m) { Write-Host "    AVISO: $m" -ForegroundColor Yellow }
function Fail($m) { Write-Host "`nERRO: $m" -ForegroundColor Red; Read-Host "`nPressione ENTER para sair"; exit 1 }
function Refresh-Path {
  $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
}
function Winget-Install($id, $name) {
  Info "Instalando $name (pode demorar alguns minutos)..."
  & winget install --id $id -e --silent --accept-source-agreements --accept-package-agreements | Out-Host
  Refresh-Path
}

$Src  = Split-Path -Parent $PSScriptRoot          # pasta "starter"
$Dest = Join-Path $env:USERPROFILE 'LiveTikTok'

Write-Host ''
Write-Host '  ======================================' -ForegroundColor Magenta
Write-Host '   LIVE INTERATIVA AUTOMATICA - INSTALADOR' -ForegroundColor Magenta
Write-Host '  ======================================' -ForegroundColor Magenta
Write-Host "  Origem:  $Src"
Write-Host "  Destino: $Dest"

if (-not (Test-Path (Join-Path $Src 'src\controller.js'))) { Fail "Não encontrei os arquivos do projeto em $Src. Extraia o ZIP inteiro antes de rodar." }

# ---------- 0. winget ----------
Info 'Verificando o winget (instalador de aplicativos do Windows)'
if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
  Fail 'O "winget" não foi encontrado. Abra a Microsoft Store, atualize o "Instalador de Aplicativos" (App Installer) e rode este instalador de novo.'
}
Ok 'winget disponível'

# ---------- 1. Node.js ----------
Info 'Verificando o Node.js'
Refresh-Path
$needNode = $true
if (Get-Command node -ErrorAction SilentlyContinue) {
  $v = (& node -v).Trim().TrimStart('v')
  if ([version]$v -ge [version]'20.0.0') { $needNode = $false; Ok "Node.js $v já instalado" }
  else { Warn "Node.js $v é antigo; instalando a versão LTS" }
}
if ($needNode) {
  Winget-Install 'OpenJS.NodeJS.LTS' 'Node.js LTS'
  if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    $nodeDir = Join-Path $env:ProgramFiles 'nodejs'
    if (Test-Path (Join-Path $nodeDir 'node.exe')) { $env:Path = "$nodeDir;$env:Path" }
  }
  if (-not (Get-Command node -ErrorAction SilentlyContinue)) { Fail 'Não consegui instalar o Node.js. Instale manualmente em https://nodejs.org (versão LTS) e rode de novo.' }
  Ok ("Node.js " + (& node -v))
}

# ---------- 2. OBS Studio ----------
Info 'Verificando o OBS Studio'
$obsExe = Join-Path $env:ProgramFiles 'obs-studio\bin\64bit\obs64.exe'
if (-not (Test-Path $obsExe)) {
  Winget-Install 'OBSProject.OBSStudio' 'OBS Studio'
  if (-not (Test-Path $obsExe)) { Fail 'Não consegui instalar o OBS. Instale manualmente em https://obsproject.com e rode de novo.' }
}
Ok 'OBS Studio instalado'

# ---------- 3. Fecha o que estiver rodando ----------
Info 'Fechando OBS e controlador antigos (se estiverem abertos)'
Get-CimInstance Win32_Process -Filter "Name='node.exe'" -ErrorAction SilentlyContinue |
  Where-Object { $_.CommandLine -and ($_.CommandLine -match 'controller\.js|server\.js') } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
$obsProcs = Get-Process obs64 -ErrorAction SilentlyContinue
if ($obsProcs) {
  Warn 'O OBS está aberto. Ele será fechado para ser configurado.'
  foreach ($p in $obsProcs) { $null = $p.CloseMainWindow() }
  Start-Sleep -Seconds 6
  Get-Process obs64 -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
  Start-Sleep -Seconds 2
}
Ok 'pronto'

# ---------- 4. Copia os arquivos ----------
Info "Copiando o projeto para $Dest"
$isUpdate = Test-Path (Join-Path $Dest 'config.json')
$roboArgs = @($Src, $Dest, '/E', '/XD', 'node_modules', 'data', '/NFL', '/NDL', '/NJH', '/NJS', '/NP', '/R:2', '/W:2')
if ($isUpdate) { $roboArgs += @('/XF', 'config.json') }   # atualização: mantém suas configurações
& robocopy @roboArgs | Out-Null
if ($LASTEXITCODE -ge 8) { Fail "Falha ao copiar arquivos (robocopy código $LASTEXITCODE)." }
New-Item -ItemType Directory -Force -Path (Join-Path $Dest 'data') | Out-Null
Get-ChildItem -Path $Dest -Recurse -File -ErrorAction SilentlyContinue | Unblock-File -ErrorAction SilentlyContinue
if ($isUpdate) { Ok 'arquivos atualizados (config.json mantido)' } else { Ok 'arquivos copiados' }

# ---------- 5. Dependências ----------
Info 'Instalando dependências (npm)'
Push-Location $Dest
& npm.cmd ci --omit=dev --no-audit --no-fund
$npmCode = $LASTEXITCODE
Pop-Location
if ($npmCode -ne 0) { Fail 'Falha no "npm ci". Verifique a internet e rode de novo.' }
Ok 'dependências instaladas'

# ---------- 6. Usuário + configuração do OBS ----------
Info 'Configuração'
$cfg = Get-Content (Join-Path $Dest 'config.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$current = $cfg.tiktokUsername
$prompt = 'Digite seu usuario do TikTok (sem @)'
if ($current -and $current -ne 'SEU_USUARIO_AQUI') { $prompt += " [ENTER mantem: $current]" }
$user = Read-Host $prompt
Push-Location $Dest
if ($user) { & node scripts\setup.js "--user=$user" } else { & node scripts\setup.js }
$setupCode = $LASTEXITCODE
Pop-Location
if ($setupCode -ne 0) { Fail 'Falha ao configurar o OBS. Feche o OBS e rode o instalador de novo.' }
Ok 'OBS configurado (controle remoto ligado, janelas automáticas desligadas)'

# ---------- 7. Energia ----------
Info 'Ajustando energia (o PC não vai dormir enquanto estiver na tomada)'
try {
  & powercfg /change standby-timeout-ac 0 | Out-Null
  & powercfg /change hibernate-timeout-ac 0 | Out-Null
  Ok 'suspensão e hibernação desligadas (na tomada)'
} catch { Warn 'Não consegui mudar a energia. Faça manualmente: Configurações > Sistema > Energia > Suspender: Nunca.' }

# ---------- 8. Atalhos ----------
Info 'Criando atalhos'
$wsh = New-Object -ComObject WScript.Shell
$desktop = [Environment]::GetFolderPath('Desktop')
$startup = [Environment]::GetFolderPath('Startup')
$vbs = Join-Path $Dest 'scripts\iniciar-oculto.vbs'
$icon = Join-Path $env:ProgramFiles 'obs-studio\bin\64bit\obs64.exe'

function New-Shortcut($path, $target, $arguments, $desc) {
  $s = $wsh.CreateShortcut($path)
  $s.TargetPath = $target
  if ($arguments) { $s.Arguments = $arguments }
  $s.WorkingDirectory = $Dest
  $s.Description = $desc
  if (Test-Path $icon) { $s.IconLocation = "$icon,0" }
  $s.Save()
}
New-Shortcut (Join-Path $startup 'Live TikTok - controlador.lnk') 'wscript.exe' "`"$vbs`"" 'Inicia o controlador da live junto com o Windows'
New-Shortcut (Join-Path $desktop 'LIGAR LIVE.lnk') (Join-Path $Dest 'scripts\ligar.bat') $null 'Liga a live'
New-Shortcut (Join-Path $desktop 'DESLIGAR LIVE.lnk') (Join-Path $Dest 'scripts\desligar.bat') $null 'Desliga a live'
New-Shortcut (Join-Path $desktop 'Iniciar controlador da Live.lnk') 'wscript.exe' "`"$vbs`"" 'Inicia o controlador (se tiver sido fechado)'
Set-Content -Path (Join-Path $desktop 'Painel da Live.url') -Value "[InternetShortcut]`r`nURL=http://127.0.0.1:3001/`r`n" -Encoding ASCII
Ok 'atalhos criados na Área de Trabalho e na inicialização do Windows'

# ---------- 9. Inicia ----------
Info 'Iniciando o controlador'
Start-Process -FilePath 'wscript.exe' -ArgumentList "`"$vbs`""
$ok = $false
for ($i = 0; $i -lt 30; $i++) {
  Start-Sleep -Seconds 2
  try { $null = Invoke-RestMethod -Uri 'http://127.0.0.1:3001/api/status' -TimeoutSec 2; $ok = $true; break } catch { }
}
if (-not $ok) { Fail "O controlador não respondeu. Veja o arquivo $Dest\data\console.log" }
Ok 'controlador rodando (o OBS vai abrir e se configurar sozinho em instantes)'
Start-Process 'http://127.0.0.1:3001/'

Write-Host ''
Write-Host '  ================== PRONTO ==================' -ForegroundColor Green
Write-Host '  Painel:   http://127.0.0.1:3001  (atalho "Painel da Live")'
Write-Host '  Para ir ao vivo: cole o Servidor e a Chave do TikTok LIVE Center no painel e clique LIGAR.'
Write-Host '  Tudo volta a rodar sozinho quando o Windows iniciar.'
Write-Host '  ============================================' -ForegroundColor Green
Read-Host "`nPressione ENTER para fechar"
