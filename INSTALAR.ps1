# Instalador do Projeto de Otimizacao
# Uso (PowerShell):  irm https://raw.githubusercontent.com/robinhomengao02112-debug/boa-noite-bb/ccr-10652a12-hlun9n/INSTALAR.ps1 | iex
# Baixa o projeto, cria a pasta "Projeto de Otimizacao" na Area de Trabalho e abre o aplicativo.
# (Arquivo somente em ASCII de proposito; os acentos do nome da pasta sao montados por codigo.)

& {
    $ErrorActionPreference = 'Stop'
    $ProgressPreference = 'SilentlyContinue'
    try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12 } catch {}

    $name    = 'Projeto de Otimiza' + [char]0x00E7 + [char]0x00E3 + 'o'
    $zipUrl  = 'https://github.com/robinhomengao02112-debug/boa-noite-bb/archive/refs/heads/ccr-10652a12-hlun9n.zip'
    $desktop = [Environment]::GetFolderPath('Desktop')
    if ($env:OTZ_DESTINO) { $desktop = $env:OTZ_DESTINO }
    $dest    = Join-Path $desktop $name
    $tmp     = Join-Path ([IO.Path]::GetTempPath()) ('otz-' + [guid]::NewGuid().ToString('N'))
    [void][IO.Directory]::CreateDirectory($tmp)

    try {
        Write-Host ''
        Write-Host ' [1/4] Baixando o Projeto de Otimizacao...' -ForegroundColor Cyan
        $zip = Join-Path $tmp 'projeto.zip'
        Invoke-WebRequest -Uri $zipUrl -OutFile $zip -UseBasicParsing

        Write-Host ' [2/4] Extraindo...' -ForegroundColor Cyan
        $extract = Join-Path $tmp 'x'
        Expand-Archive -LiteralPath $zip -DestinationPath $extract -Force
        $bat = Get-ChildItem -LiteralPath $extract -Recurse -Filter 'INICIAR.bat' | Select-Object -First 1
        if (-not $bat) { throw 'Os arquivos do projeto nao foram encontrados no download.' }
        $src = $bat.Directory.FullName

        Write-Host ' [3/4] Instalando na Area de Trabalho...' -ForegroundColor Cyan
        if (Test-Path -LiteralPath $dest) {
            # Atualizacao: substitui os arquivos do app e preserva a pasta Dados (backups e testes).
            Get-ChildItem -LiteralPath $src | Where-Object { $_.Name -ne 'Dados' } | ForEach-Object {
                Copy-Item -LiteralPath $_.FullName -Destination $dest -Recurse -Force
            }
        } else {
            Copy-Item -LiteralPath $src -Destination $dest -Recurse
        }
        Get-ChildItem -LiteralPath $dest -Recurse -File | ForEach-Object {
            try { Unblock-File -LiteralPath $_.FullName } catch {}
        }
        foreach ($f in @('INICIAR.bat', 'Otimizador.ps1', 'PESQUISA.md', 'README.md')) {
            if (-not (Test-Path -LiteralPath (Join-Path $dest $f))) { throw "Arquivo ausente apos a instalacao: $f" }
        }

        if (-not $env:OTZ_DESTINO) {
            try {
                $ws  = New-Object -ComObject WScript.Shell
                $lnk = $ws.CreateShortcut((Join-Path $desktop 'Iniciar Otimizador.lnk'))
                $lnk.TargetPath       = Join-Path $dest 'INICIAR.bat'
                $lnk.WorkingDirectory = $dest
                $lnk.IconLocation     = "$env:SystemRoot\System32\imageres.dll,109"
                $lnk.Description      = 'Abre o Projeto de Otimizacao'
                $lnk.Save()
            } catch {}
        }

        Write-Host ' [4/4] Pronto!' -ForegroundColor Green
        Write-Host (' Pasta instalada: ' + $dest)
        Write-Host ' Atalho criado na Area de Trabalho: "Iniciar Otimizador"'
        Write-Host ' Abrindo o aplicativo (clique em "Sim" na janela do Windows)...'
        Write-Host ''
        if (-not $env:OTZ_DESTINO) {
            Start-Process -FilePath (Join-Path $dest 'INICIAR.bat') -WorkingDirectory $dest
        }
    } catch {
        Write-Host ''
        Write-Host (' ERRO na instalacao: ' + $_.Exception.Message) -ForegroundColor Red
        Write-Host ' Verifique a internet e tente de novo, ou baixe manualmente:'
        Write-Host (' ' + $zipUrl)
    } finally {
        Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
    }
}
