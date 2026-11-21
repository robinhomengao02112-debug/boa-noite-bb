#Requires -Version 5.1
<#
    PROJETO DE OTIMIZAÇÃO
    Otimizador seguro e reversível para notebooks com Intel HD/UHD Graphics 620.

    - Toda alteração guarda o valor original em Dados\Backups\estado.json antes de mudar.
    - Um ponto de restauração do Windows é criado antes da primeira aplicação.
    - Nada de overclock, undervolt, BIOS, desativar segurança ou serviços.

    Execute pelo arquivo INICIAR.bat (pede permissão de administrador automaticamente).
#>
param([switch]$Relaunched)

Set-StrictMode -Off
$ErrorActionPreference = 'Continue'

# ============================================================================
#  ELEVAÇÃO (administrador) + STA (necessário para a interface WPF)
# ============================================================================
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
$isAdmin   = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
$isSta     = ([System.Threading.Thread]::CurrentThread.ApartmentState -eq [System.Threading.ApartmentState]::STA)

if (-not $isAdmin -or -not $isSta) {
    $argList = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-STA', '-File', ('"{0}"' -f $PSCommandPath), '-Relaunched')
    try {
        if ($isAdmin) {
            Start-Process -FilePath 'powershell.exe' -ArgumentList $argList -ErrorAction Stop | Out-Null
        } else {
            Start-Process -FilePath 'powershell.exe' -ArgumentList $argList -Verb RunAs -ErrorAction Stop | Out-Null
        }
    } catch {
        Add-Type -AssemblyName PresentationFramework
        [System.Windows.MessageBox]::Show(
            "O Projeto de Otimização precisa de permissão de administrador para ajustar o Windows.`n`nAbra o INICIAR.bat novamente e clique em 'Sim' na janela do Windows.",
            'Projeto de Otimização', 'OK', 'Warning') | Out-Null
    }
    exit
}

Add-Type -AssemblyName PresentationFramework, PresentationCore, WindowsBase, System.Windows.Forms

# ============================================================================
#  PASTAS E ARQUIVOS
# ============================================================================
$script:Root         = Split-Path -Parent $PSCommandPath
$script:DataDir      = Join-Path $script:Root 'Dados'
$script:BackupDir    = Join-Path $script:DataDir 'Backups'
$script:ReportDir    = Join-Path $script:DataDir 'Relatorios'
$script:BenchDir     = Join-Path $script:DataDir 'Benchmarks'
$script:LogDir       = Join-Path $script:DataDir 'Logs'
foreach ($d in @($script:DataDir, $script:BackupDir, $script:ReportDir, $script:BenchDir, $script:LogDir)) {
    [void][System.IO.Directory]::CreateDirectory($d)
}
$script:BackupFile   = Join-Path $script:BackupDir 'estado.json'
$script:BenchFile    = Join-Path $script:BenchDir 'historico.json'
$script:FpsFile      = Join-Path $script:BenchDir 'fps.json'
$script:LogFile      = Join-Path $script:LogDir ('log-{0}.txt' -f (Get-Date -Format 'yyyy-MM-dd'))
$script:Utf8NoBom    = New-Object System.Text.UTF8Encoding($false)
$script:UI           = @{}
$script:Window       = $null
$script:RestorePointDone = $false
$script:PendingApply = $null
$script:AfterRestore = $null
$script:LastDiagText = ''
$script:LastRecText  = ''
$script:ProcList     = @()
$script:TweakChecks  = @{}
$script:TweakStatus  = @{}
$script:OtzPlanName  = 'Otimizador - Desempenho Maximo'
$script:IfeoBase     = 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Image File Execution Options'

try { Get-ChildItem -LiteralPath $script:Root -Recurse -File -ErrorAction SilentlyContinue | Unblock-File -ErrorAction SilentlyContinue } catch {}

$osBuild = 0
try { $osBuild = [int](Get-CimInstance Win32_OperatingSystem -ErrorAction Stop).BuildNumber } catch {}
$script:IsWin11 = ($osBuild -ge 22000)

# ============================================================================
#  CÓDIGO NATIVO (benchmark, liberar memória em espera, console)
# ============================================================================
$nativeCode = @'
using System;
using System.Diagnostics;
using System.Threading;
using System.Runtime.InteropServices;

public static class OtzNative
{
    public static long Sink;

    private static long Work(long iterations)
    {
        long acc = 0;
        double f = 1.0;
        for (long i = 1; i <= iterations; i++)
        {
            acc += (i * 2654435761L) ^ (acc >> 3);
            f = f * 1.0000001 + 0.5 / i;
        }
        return acc + (long)f;
    }

    public static double CpuScore(int threads, int milliseconds)
    {
        if (threads < 1) threads = 1;
        long total = 0;
        object gate = new object();
        Stopwatch sw = new Stopwatch();
        ManualResetEvent go = new ManualResetEvent(false);
        Thread[] pool = new Thread[threads];
        for (int t = 0; t < threads; t++)
        {
            pool[t] = new Thread(new ThreadStart(() =>
            {
                go.WaitOne();
                long local = 0;
                long sink = 0;
                while (sw.ElapsedMilliseconds < milliseconds)
                {
                    sink += Work(100000);
                    local++;
                }
                lock (gate) { total += local; Sink += sink; }
            }));
            pool[t].IsBackground = true;
            pool[t].Start();
        }
        sw.Start();
        go.Set();
        foreach (Thread th in pool) th.Join();
        sw.Stop();
        return total / sw.Elapsed.TotalSeconds;
    }

    public static double MemoryBandwidth(int threads, int mbPerThread, int milliseconds)
    {
        if (threads < 1) threads = 1;
        int size = mbPerThread * 1024 * 1024;
        byte[][] src = new byte[threads][];
        byte[][] dst = new byte[threads][];
        for (int i = 0; i < threads; i++)
        {
            src[i] = new byte[size];
            dst[i] = new byte[size];
            for (int j = 0; j < size; j += 4096) { src[i][j] = (byte)(j >> 12); dst[i][j] = 1; }
        }
        long total = 0;
        object gate = new object();
        Stopwatch sw = new Stopwatch();
        ManualResetEvent go = new ManualResetEvent(false);
        Thread[] pool = new Thread[threads];
        for (int t = 0; t < threads; t++)
        {
            int id = t;
            pool[t] = new Thread(new ThreadStart(() =>
            {
                go.WaitOne();
                byte[] s = src[id];
                byte[] d = dst[id];
                long local = 0;
                while (sw.ElapsedMilliseconds < milliseconds)
                {
                    Buffer.BlockCopy(s, 0, d, 0, s.Length);
                    local += 2L * s.Length;
                }
                lock (gate) { total += local; }
            }));
            pool[t].IsBackground = true;
            pool[t].Start();
        }
        sw.Start();
        go.Set();
        foreach (Thread th in pool) th.Join();
        sw.Stop();
        return total / sw.Elapsed.TotalSeconds / 1e9;
    }

    public static double MemoryBandwidthSafe(int threads, int milliseconds)
    {
        int[] sizes = new int[] { 32, 16, 8 };
        foreach (int mb in sizes)
        {
            try { return MemoryBandwidth(threads, mb, milliseconds); }
            catch (OutOfMemoryException) { GC.Collect(); }
        }
        return 0;
    }

    public static double[] RunBenchmark(int threads)
    {
        CpuScore(1, 1000);
        double single = CpuScore(1, 6000);
        double multi = CpuScore(threads, 6000);
        double mem = MemoryBandwidthSafe(Math.Min(threads, 4), 5000);
        GC.Collect();
        return new double[] { single, multi, mem };
    }

    // 0 = parado, 1 = rodando, 2 = concluído, 3 = erro
    public static volatile int BenchState;
    public static double[] BenchResult;
    public static string BenchError;

    public static void StartBenchmark(int threads)
    {
        BenchResult = null;
        BenchError = null;
        BenchState = 1;
        Thread worker = new Thread(new ThreadStart(() =>
        {
            try { BenchResult = RunBenchmark(threads); BenchState = 2; }
            catch (Exception ex) { BenchError = ex.Message; BenchState = 3; }
        }));
        worker.IsBackground = true;
        worker.Start();
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct LUID { public uint LowPart; public int HighPart; }

    [StructLayout(LayoutKind.Sequential)]
    public struct TOKEN_PRIVILEGES { public int PrivilegeCount; public LUID Luid; public int Attributes; }

    [DllImport("advapi32.dll", SetLastError = true)]
    static extern bool OpenProcessToken(IntPtr ProcessHandle, int DesiredAccess, out IntPtr TokenHandle);

    [DllImport("advapi32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    static extern bool LookupPrivilegeValue(string lpSystemName, string lpName, out LUID lpLuid);

    [DllImport("advapi32.dll", SetLastError = true)]
    static extern bool AdjustTokenPrivileges(IntPtr TokenHandle, bool DisableAllPrivileges, ref TOKEN_PRIVILEGES NewState, int BufferLength, IntPtr PreviousState, IntPtr ReturnLength);

    [DllImport("kernel32.dll")]
    static extern bool CloseHandle(IntPtr hObject);

    [DllImport("kernel32.dll")]
    static extern IntPtr GetCurrentProcess();

    [DllImport("ntdll.dll")]
    static extern int NtSetSystemInformation(int SystemInformationClass, ref int SystemInformation, int SystemInformationLength);

    [DllImport("kernel32.dll")]
    public static extern IntPtr GetConsoleWindow();

    [DllImport("user32.dll")]
    public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

    static bool EnablePrivilege(string name)
    {
        IntPtr token;
        if (!OpenProcessToken(GetCurrentProcess(), 0x0020 | 0x0008, out token)) return false;
        try
        {
            TOKEN_PRIVILEGES tp = new TOKEN_PRIVILEGES();
            tp.PrivilegeCount = 1;
            tp.Attributes = 2;
            LUID luid;
            if (!LookupPrivilegeValue(null, name, out luid)) return false;
            tp.Luid = luid;
            if (!AdjustTokenPrivileges(token, false, ref tp, 0, IntPtr.Zero, IntPtr.Zero)) return false;
            return Marshal.GetLastWin32Error() == 0;
        }
        finally { CloseHandle(token); }
    }

    // 0 = sucesso; -1 = sem privilégio; outro = código NTSTATUS
    public static int PurgeStandbyList()
    {
        if (!EnablePrivilege("SeProfileSingleProcessPrivilege")) return -1;
        int command = 4; // MemoryPurgeStandbyList
        return NtSetSystemInformation(80, ref command, sizeof(int)); // SystemMemoryListInformation
    }
}
'@
if (-not ('OtzNative' -as [type])) {
    Add-Type -TypeDefinition $nativeCode -Language CSharp
}

# ============================================================================
#  UTILITÁRIOS
# ============================================================================
function Get-Brush([string]$hex) {
    return (New-Object System.Windows.Media.BrushConverter).ConvertFromString($hex)
}

function Write-Log([string]$Message) {
    $line = '[{0}] {1}' -f (Get-Date -Format 'HH:mm:ss'), $Message
    try { [System.IO.File]::AppendAllText($script:LogFile, $line + [Environment]::NewLine, $script:Utf8NoBom) } catch {}
    if ($script:UI.ContainsKey('txtLog') -and $script:UI['txtLog']) {
        $script:UI['txtLog'].AppendText($line + [Environment]::NewLine)
        $script:UI['txtLog'].ScrollToEnd()
    }
}

function Update-UI {
    if ($script:Window) {
        $script:Window.Dispatcher.Invoke([Action]{}, [System.Windows.Threading.DispatcherPriority]::Background)
    }
}

function Set-Status([string]$Text) {
    if ($script:UI.ContainsKey('txtStatus')) { $script:UI['txtStatus'].Text = $Text }
    Update-UI
}

function Set-Busy([bool]$Busy, [string]$Text = '') {
    if ($script:UI.ContainsKey('tabs')) { $script:UI['tabs'].IsEnabled = -not $Busy }
    if ($script:Window) {
        if ($Busy) { $script:Window.Cursor = [System.Windows.Input.Cursors]::Wait } else { $script:Window.Cursor = $null }
    }
    if ($Busy) { Set-Status $Text } else { Set-Status 'Pronto.' }
}

function Show-Msg([string]$Text, [string]$Icon = 'Information') {
    if ($script:Window) {
        [void][System.Windows.MessageBox]::Show($script:Window, $Text, 'Projeto de Otimização', 'OK', $Icon)
    } else {
        [void][System.Windows.MessageBox]::Show($Text, 'Projeto de Otimização', 'OK', $Icon)
    }
}

function Show-Confirm([string]$Text) {
    $r = [System.Windows.MessageBox]::Show($script:Window, $Text, 'Projeto de Otimização', 'YesNo', 'Question')
    return ($r -eq [System.Windows.MessageBoxResult]::Yes)
}

function Open-Url([string]$Url) {
    try { Start-Process $Url | Out-Null; Write-Log "Abrindo: $Url" } catch { Write-Log "Não foi possível abrir $Url : $($_.Exception.Message)" }
}

# Executa código PowerShell em segundo plano (a janela continua respondendo).
function Start-OtzJob([string]$Code, [object[]]$Arguments, [scriptblock]$OnDone) {
    $ps = [PowerShell]::Create()
    [void]$ps.AddScript($Code)
    if ($Arguments) { foreach ($a in $Arguments) { [void]$ps.AddArgument($a) } }
    $handle = $ps.BeginInvoke()
    $timer = New-Object System.Windows.Threading.DispatcherTimer
    $timer.Interval = [TimeSpan]::FromMilliseconds(300)
    $timer.Tag = @{ PS = $ps; Handle = $handle; OnDone = $OnDone }
    $timer.Add_Tick({
        param($sender, $e)
        $st = $sender.Tag
        if (-not $st.Handle.IsCompleted) { return }
        $sender.Stop()
        $out = $null
        $err = $null
        try { $out = $st.PS.EndInvoke($st.Handle) } catch { $err = $_.Exception.Message }
        if (-not $err -and $st.PS.Streams.Error.Count -gt 0 -and (-not $out -or $out.Count -eq 0)) {
            $err = $st.PS.Streams.Error[0].ToString()
        }
        try { $st.PS.Dispose() } catch {}
        try { & $st.OnDone $out $err } catch { Write-Log ('ERRO: ' + $_.Exception.Message); Set-Busy $false }
    })
    $timer.Start()
}

# ============================================================================
#  REGISTRO — leitura, escrita e restauração com backup
# ============================================================================
function Get-RegValueInfo([string]$Path, [string]$Name) {
    $info = [ordered]@{ Path = $Path; Name = $Name; Existed = $false; Type = $null; Value = $null }
    try {
        if (Test-Path -LiteralPath $Path) {
            $key = Get-Item -LiteralPath $Path -ErrorAction Stop
            if (@($key.GetValueNames()) -contains $Name) {
                $info.Existed = $true
                $info.Type    = $key.GetValueKind($Name).ToString()
                $info.Value   = $key.GetValue($Name, $null, [Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames)
            }
        }
    } catch {}
    return [pscustomobject]$info
}

function Set-RegValue([string]$Path, [string]$Name, [string]$Type, $Value) {
    if (-not (Test-Path -LiteralPath $Path)) { New-Item -Path $Path -Force -ErrorAction Stop | Out-Null }
    New-ItemProperty -LiteralPath $Path -Name $Name -PropertyType $Type -Value $Value -Force -ErrorAction Stop | Out-Null
}

function Convert-RegValue([string]$Type, $Value) {
    switch ($Type) {
        'DWord'       { return [int]$Value }
        'QWord'       { return [long]$Value }
        'Binary'      { return ,([byte[]]@($Value)) }
        'MultiString' { return ,([string[]]@($Value)) }
        default       { return [string]$Value }
    }
}

function Restore-RegEntry($Saved) {
    if ($Saved.Existed) {
        if (@('String', 'ExpandString', 'DWord', 'QWord', 'Binary', 'MultiString') -notcontains $Saved.Type) {
            Write-Log "  Aviso: tipo de valor '$($Saved.Type)' em $($Saved.Path)\$($Saved.Name) não pôde ser restaurado automaticamente."
            return
        }
        Set-RegValue $Saved.Path $Saved.Name $Saved.Type (Convert-RegValue $Saved.Type $Saved.Value)
    } else {
        if (Test-Path -LiteralPath $Saved.Path) {
            Remove-ItemProperty -LiteralPath $Saved.Path -Name $Saved.Name -ErrorAction SilentlyContinue
        }
    }
}

function Import-OtzBackup {
    $script:Backup = @{ tweaks = @{}; powerplan = $null; ifeo = @{} }
    if (-not [System.IO.File]::Exists($script:BackupFile)) { return }
    try {
        $obj = [System.IO.File]::ReadAllText($script:BackupFile) | ConvertFrom-Json
        if ($obj.tweaks) { foreach ($p in $obj.tweaks.PSObject.Properties) { $script:Backup.tweaks[$p.Name] = @($p.Value) } }
        if ($obj.powerplan -and $obj.powerplan.Created) { $script:Backup.powerplan = $obj.powerplan }
        if ($obj.ifeo) { foreach ($p in $obj.ifeo.PSObject.Properties) { $script:Backup.ifeo[$p.Name] = $p.Value } }
    } catch {
        $bad = $script:BackupFile + '.ilegivel-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
        try { Copy-Item -LiteralPath $script:BackupFile -Destination $bad -Force } catch {}
        Write-Log "Aviso: o arquivo de backup estava ilegível e foi preservado em $bad"
    }
}

function Save-OtzBackup {
    $json = ConvertTo-Json -InputObject $script:Backup -Depth 8
    [System.IO.File]::WriteAllText($script:BackupFile, $json, $script:Utf8NoBom)
}

# ============================================================================
#  PLANO DE ENERGIA
# ============================================================================
$script:GuidHighPerf = '8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c'
$script:GuidBalanced = '381b4222-f694-41f0-9685-ff5bb260df2e'
$script:GuidRegex    = '([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})'

function Get-ActiveSchemeGuid {
    $o = (& powercfg.exe /getactivescheme 2>$null) | Out-String
    if ($o -match $script:GuidRegex) { return $Matches[1].ToLower() }
    return $null
}

function Test-SchemeExists([string]$Guid) {
    if (-not $Guid) { return $false }
    $o = (& powercfg.exe /list 2>$null) | Out-String
    return ($o -match [regex]::Escape($Guid))
}

function Set-PowerValue([string]$Scheme, [string]$Sub, [string]$Setting, $Ac, $Dc) {
    if ($null -ne $Ac) { $null = & powercfg.exe /setacvalueindex $Scheme $Sub $Setting $Ac 2>&1 }
    if ($null -ne $Dc) { $null = & powercfg.exe /setdcvalueindex $Scheme $Sub $Setting $Dc 2>&1 }
}

function Enable-OtzPowerPlan {
    $prev = Get-ActiveSchemeGuid
    $existing = $script:Backup.powerplan
    if ($existing -and (Test-SchemeExists $existing.Created)) {
        $new = $existing.Created
    } else {
        $new = [guid]::NewGuid().ToString()
        $ok = $false
        foreach ($src in @($script:GuidHighPerf, $script:GuidBalanced)) {
            $null = & powercfg.exe /duplicatescheme $src $new 2>&1
            if (Test-SchemeExists $new) { $ok = $true; Write-Log "  Plano base: $src"; break }
        }
        if (-not $ok) { throw 'Não foi possível criar o plano de energia (powercfg recusou a cópia).' }
        $script:Backup.powerplan = [pscustomobject]@{ Previous = $prev; Created = $new }
        Save-OtzBackup
        $null = & powercfg.exe /changename $new $script:OtzPlanName 'Criado pelo Projeto de Otimizacao. Reversivel pelo aplicativo.' 2>&1
    }
    # Processador: 100% máximo, turbo agressivo na tomada, preferência energética = desempenho
    Set-PowerValue $new 'SUB_PROCESSOR' 'PROCTHROTTLEMAX' 100 100
    Set-PowerValue $new 'SUB_PROCESSOR' 'PERFBOOSTMODE' 2 1
    Set-PowerValue $new 'SUB_PROCESSOR' 'PERFEPP' 0 25
    # Plano gráfico da Intel (quando o driver expõe): 2 = desempenho máximo
    Set-PowerValue $new '44f3beca-a7c0-460e-9df2-bb8b99e0cba6' '3619c3f2-afb2-4afc-b0e9-e7fef372de36' 2 1
    # Sem economia de energia em PCIe, USB, Wi-Fi e disco (somente na tomada)
    Set-PowerValue $new 'SUB_PCIEXPRESS' 'ASPM' 0 $null
    Set-PowerValue $new '2a737441-1930-4402-8d77-b2bebba308a3' '48e6b7a6-50f5-4782-a5d4-53bb8f07e226' 0 $null
    Set-PowerValue $new '19cbb8fa-5279-450e-9fac-8a3d5fedd0c1' '12bbebe6-58d6-4636-95bb-3217ef867c1a' 0 $null
    Set-PowerValue $new 'SUB_DISK' 'DISKIDLE' 0 $null
    $null = & powercfg.exe /setactive $new 2>&1
    if ((Get-ActiveSchemeGuid) -ne $new.ToLower()) { throw 'O plano foi criado, mas o Windows não o ativou.' }
}

function Disable-OtzPowerPlan {
    $pp = $script:Backup.powerplan
    if (-not $pp) { Write-Log '  Plano de energia: nada para desfazer.'; return }
    $target = $pp.Previous
    if (-not $target -or $target -eq $pp.Created -or -not (Test-SchemeExists $target)) { $target = $script:GuidBalanced }
    $null = & powercfg.exe /setactive $target 2>&1
    if (Test-SchemeExists $pp.Created) { $null = & powercfg.exe /delete $pp.Created 2>&1 }
    $script:Backup.powerplan = $null
    Save-OtzBackup
}

# ============================================================================
#  CATÁLOGO DE OTIMIZAÇÕES
# ============================================================================
$swapEffectCompute = {
    param($old)
    if ($null -eq $old) { $old = '' }
    $parts = @(("$old" -split ';') | Where-Object { $_ -and ($_ -notmatch '^\s*SwapEffectUpgradeEnable=') })
    $parts += 'SwapEffectUpgradeEnable=1'
    return (($parts -join ';') + ';')
}

$script:Tweaks = @(
    [pscustomobject]@{
        Id = 'powerplan'; Kind = 'PowerPlan'; Recommended = $true; Win11Only = $false
        Name = 'Plano de energia "Otimizador - Desempenho Máximo"'
        Desc = 'Cria um plano NOVO (cópia do Alto Desempenho) com turbo agressivo, preferência de energia em desempenho, plano gráfico da Intel no máximo e sem economia em PCIe/USB/Wi-Fi/disco na tomada. Seus planos atuais não são alterados.'
        Entries = @()
    }
    [pscustomobject]@{
        Id = 'gamemode'; Kind = 'Registry'; Recommended = $true; Win11Only = $false
        Name = 'Ativar o Modo de Jogo do Windows'
        Desc = 'Prioriza o jogo aberto e segura atualizações e tarefas em segundo plano durante a partida.'
        Entries = @(
            @{ Path = 'HKCU:\Software\Microsoft\GameBar'; Name = 'AllowAutoGameMode';   Type = 'DWord'; Value = 1 }
            @{ Path = 'HKCU:\Software\Microsoft\GameBar'; Name = 'AutoGameModeEnabled'; Type = 'DWord'; Value = 1 }
        )
    }
    [pscustomobject]@{
        Id = 'gamedvr'; Kind = 'Registry'; Recommended = $true; Win11Only = $false
        Name = 'Desativar gravação em segundo plano (Game DVR)'
        Desc = 'A gravação contínua de vídeo consome GPU e disco o tempo todo. A Xbox Game Bar continua funcionando.'
        Entries = @(
            @{ Path = 'HKCU:\System\GameConfigStore'; Name = 'GameDVR_Enabled'; Type = 'DWord'; Value = 0 }
            @{ Path = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\GameDVR'; Name = 'AppCaptureEnabled'; Type = 'DWord'; Value = 0 }
            @{ Path = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\GameDVR'; Name = 'HistoricalCaptureEnabled'; Type = 'DWord'; Value = 0 }
        )
    }
    [pscustomobject]@{
        Id = 'mmcss'; Kind = 'Registry'; Recommended = $true; Win11Only = $false
        Name = 'Prioridade para jogos no agendador multimídia (MMCSS)'
        Desc = 'Reserva menos CPU para tarefas de fundo (20% -> 10%) e dá prioridade alta de CPU e disco às threads de jogos.'
        Entries = @(
            @{ Path = 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Multimedia\SystemProfile'; Name = 'SystemResponsiveness'; Type = 'DWord'; Value = 10 }
            @{ Path = 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Multimedia\SystemProfile\Tasks\Games'; Name = 'GPU Priority'; Type = 'DWord'; Value = 8 }
            @{ Path = 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Multimedia\SystemProfile\Tasks\Games'; Name = 'Priority'; Type = 'DWord'; Value = 6 }
            @{ Path = 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Multimedia\SystemProfile\Tasks\Games'; Name = 'Scheduling Category'; Type = 'String'; Value = 'High' }
            @{ Path = 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Multimedia\SystemProfile\Tasks\Games'; Name = 'SFIO Priority'; Type = 'String'; Value = 'High' }
        )
    }
    [pscustomobject]@{
        Id = 'transparency'; Kind = 'Registry'; Recommended = $true; Win11Only = $false
        Name = 'Desativar efeitos de transparência'
        Desc = 'O desfoque/transparência do Windows é desenhado pela própria HD 620. Desligar libera a GPU (mudança só visual).'
        Entries = @(
            @{ Path = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize'; Name = 'EnableTransparency'; Type = 'DWord'; Value = 0 }
        )
    }
    [pscustomobject]@{
        Id = 'visualfx'; Kind = 'Registry'; Recommended = $true; Win11Only = $false
        Name = 'Reduzir animações (mantendo fontes suaves)'
        Desc = 'Desliga animações de janelas e da barra de tarefas e o Aero Peek; menus abrem mais rápido. Vale após sair e entrar na conta.'
        Entries = @(
            @{ Path = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\VisualEffects'; Name = 'VisualFXSetting'; Type = 'DWord'; Value = 3 }
            @{ Path = 'HKCU:\Control Panel\Desktop\WindowMetrics'; Name = 'MinAnimate'; Type = 'String'; Value = '0' }
            @{ Path = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\Advanced'; Name = 'TaskbarAnimations'; Type = 'DWord'; Value = 0 }
            @{ Path = 'HKCU:\Software\Microsoft\Windows\DWM'; Name = 'EnableAeroPeek'; Type = 'DWord'; Value = 0 }
            @{ Path = 'HKCU:\Control Panel\Desktop'; Name = 'MenuShowDelay'; Type = 'String'; Value = '100' }
        )
    }
    [pscustomobject]@{
        Id = 'startupdelay'; Kind = 'Registry'; Recommended = $true; Win11Only = $false
        Name = 'Remover atraso dos programas de inicialização'
        Desc = 'O Windows espera alguns segundos antes de abrir os programas de inicialização. Isso remove a espera artificial.'
        Entries = @(
            @{ Path = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\Serialize'; Name = 'StartupDelayInMSec'; Type = 'DWord'; Value = 0 }
        )
    }
    [pscustomobject]@{
        Id = 'tips'; Kind = 'Registry'; Recommended = $true; Win11Only = $false
        Name = 'Desativar dicas, sugestões e apps promovidos'
        Desc = 'Evita instalações silenciosas de apps patrocinados e processos de sugestões rodando em segundo plano.'
        Entries = @(
            @{ Path = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\ContentDeliveryManager'; Name = 'SystemPaneSuggestionsEnabled'; Type = 'DWord'; Value = 0 }
            @{ Path = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\ContentDeliveryManager'; Name = 'SoftLandingEnabled'; Type = 'DWord'; Value = 0 }
            @{ Path = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\ContentDeliveryManager'; Name = 'SilentInstalledAppsEnabled'; Type = 'DWord'; Value = 0 }
            @{ Path = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\ContentDeliveryManager'; Name = 'SubscribedContent-338388Enabled'; Type = 'DWord'; Value = 0 }
            @{ Path = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\ContentDeliveryManager'; Name = 'SubscribedContent-338389Enabled'; Type = 'DWord'; Value = 0 }
            @{ Path = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\ContentDeliveryManager'; Name = 'SubscribedContent-353694Enabled'; Type = 'DWord'; Value = 0 }
            @{ Path = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\ContentDeliveryManager'; Name = 'SubscribedContent-353696Enabled'; Type = 'DWord'; Value = 0 }
        )
    }
    [pscustomobject]@{
        Id = 'deliveryopt'; Kind = 'Registry'; Recommended = $true; Win11Only = $false
        Name = 'Não enviar atualizações para outros PCs (P2P)'
        Desc = 'O Windows Update continua funcionando normalmente; ele só para de usar seu disco, CPU e internet para distribuir atualizações a outros computadores. (O Windows pode mostrar "Algumas configurações são gerenciadas pela sua organização" — é normal e some ao desfazer.)'
        Entries = @(
            @{ Path = 'HKLM:\SOFTWARE\Policies\Microsoft\Windows\DeliveryOptimization'; Name = 'DODownloadMode'; Type = 'DWord'; Value = 0 }
        )
    }
    [pscustomobject]@{
        Id = 'windowedgames'; Kind = 'Registry'; Recommended = $true; Win11Only = $true
        Name = 'Otimizações para jogos em janela (Windows 11)'
        Desc = 'Opção oficial do Windows 11: usa o modelo de apresentação moderno em jogos DirectX 10/11 em janela ou sem borda (menos latência e mais fluidez).'
        Entries = @(
            @{ Path = 'HKCU:\Software\Microsoft\DirectX\UserGpuPreferences'; Name = 'DirectXUserGlobalSettings'; Type = 'String'; Value = $null; Compute = $swapEffectCompute }
        )
    }
    [pscustomobject]@{
        Id = 'backgroundapps'; Kind = 'Registry'; Recommended = $false; Win11Only = $false
        Name = '(Opcional) Impedir apps da Microsoft Store em segundo plano'
        Desc = 'Libera RAM e CPU. Efeito colateral: apps da Store (ex.: Mail, WhatsApp da Store) podem não mostrar notificações com eles fechados.'
        Entries = @(
            @{ Path = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\BackgroundAccessApplications'; Name = 'GlobalUserDisabled'; Type = 'DWord'; Value = 1 }
            @{ Path = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Search'; Name = 'BackgroundAppGlobalToggle'; Type = 'DWord'; Value = 0 }
        )
    }
)

function Get-EntryTarget($Entry, $CurrentValue) {
    if ($Entry.Compute) { return (& $Entry.Compute $CurrentValue) }
    return $Entry.Value
}

function Test-TweakApplied($Tweak) {
    if ($Tweak.Kind -eq 'PowerPlan') {
        $pp = $script:Backup.powerplan
        if (-not $pp) { return $false }
        return ((Get-ActiveSchemeGuid) -eq ("$($pp.Created)".ToLower()))
    }
    foreach ($e in $Tweak.Entries) {
        $cur = Get-RegValueInfo $e.Path $e.Name
        if (-not $cur.Existed) { return $false }
        $target = Get-EntryTarget $e $cur.Value
        if ("$($cur.Value)" -ne "$target") { return $false }
    }
    return $true
}

function Invoke-ApplyTweak($Tweak) {
    if ($Tweak.Kind -eq 'PowerPlan') { Enable-OtzPowerPlan; return }
    if (-not $script:Backup.tweaks.ContainsKey($Tweak.Id)) {
        $saved = @()
        foreach ($e in $Tweak.Entries) { $saved += (Get-RegValueInfo $e.Path $e.Name) }
        $script:Backup.tweaks[$Tweak.Id] = $saved
        Save-OtzBackup
    }
    foreach ($e in $Tweak.Entries) {
        $cur = Get-RegValueInfo $e.Path $e.Name
        $val = Get-EntryTarget $e $cur.Value
        Set-RegValue $e.Path $e.Name $e.Type $val
    }
}

function Invoke-RevertTweak([string]$Id) {
    if ($Id -eq 'powerplan') { Disable-OtzPowerPlan; return $true }
    if (-not $script:Backup.tweaks.ContainsKey($Id)) { return $false }
    foreach ($s in @($script:Backup.tweaks[$Id])) { Restore-RegEntry $s }
    $script:Backup.tweaks.Remove($Id)
    Save-OtzBackup
    return $true
}

function Get-TweakName([string]$Id) {
    foreach ($t in $script:Tweaks) { if ($t.Id -eq $Id) { return $t.Name } }
    return $Id
}

# ============================================================================
#  PRIORIDADE PERMANENTE (Image File Execution Options\PerfOptions)
# ============================================================================
$script:BlockedExe = @('explorer.exe', 'svchost.exe', 'csrss.exe', 'wininit.exe', 'winlogon.exe', 'lsass.exe', 'services.exe',
                       'smss.exe', 'dwm.exe', 'system', 'audiodg.exe', 'powershell.exe', 'cmd.exe', 'conhost.exe', 'taskmgr.exe',
                       'msmpeng.exe', 'searchindexer.exe', 'runtimebroker.exe', 'sihost.exe', 'fontdrvhost.exe')

function Set-PermanentPriority([string]$ExeName) {
    $key = $script:IfeoBase + '\' + $ExeName + '\PerfOptions'
    if (-not $script:Backup.ifeo.ContainsKey($ExeName)) {
        $info = Get-RegValueInfo $key 'CpuPriorityClass'
        $script:Backup.ifeo[$ExeName] = [pscustomobject]@{
            Path = $key; Name = 'CpuPriorityClass'; Existed = $info.Existed; Type = $info.Type; Value = $info.Value
            KeyCreated = (-not (Test-Path -LiteralPath $key))
        }
        Save-OtzBackup
    }
    Set-RegValue $key 'CpuPriorityClass' 'DWord' 3
}

function Remove-PermanentPriority([string]$ExeName) {
    $b = $script:Backup.ifeo[$ExeName]
    if (-not $b) { return }
    Restore-RegEntry $b
    if ($b.KeyCreated) {
        try {
            $k = Get-Item -LiteralPath $b.Path -ErrorAction Stop
            if ($k.ValueCount -eq 0 -and $k.SubKeyCount -eq 0) { Remove-Item -LiteralPath $b.Path -Force -ErrorAction SilentlyContinue }
        } catch {}
    }
    $script:Backup.ifeo.Remove($ExeName)
    Save-OtzBackup
}

# ============================================================================
#  DIAGNÓSTICO (roda em segundo plano — por isso é autocontido)
# ============================================================================
function Get-OtzDiagnostics {
    $ErrorActionPreference = 'SilentlyContinue'
    $sb   = New-Object System.Text.StringBuilder
    $recs = New-Object System.Collections.ArrayList
    function Add-Line([string]$s = '') { [void]$sb.AppendLine($s) }
    function Add-Rec([int]$p, [string]$s) { [void]$recs.Add([pscustomobject]@{ P = $p; T = $s }) }
    function Get-CimSafe([string]$cls, [string]$ns = 'root\cimv2') {
        try { return @(Get-CimInstance -ClassName $cls -Namespace $ns -ErrorAction Stop) } catch { return @() }
    }
    function Format-GB($b) { return ('{0:N1} GB' -f ([double]$b / 1GB)) }
    function Format-Date($d) { if ($d) { return ([datetime]$d).ToString('dd/MM/yyyy') } else { return '?' } }

    $cs   = Get-CimSafe 'Win32_ComputerSystem' | Select-Object -First 1
    $os   = Get-CimSafe 'Win32_OperatingSystem' | Select-Object -First 1
    $bios = Get-CimSafe 'Win32_BIOS' | Select-Object -First 1
    $prod = Get-CimSafe 'Win32_ComputerSystemProduct' | Select-Object -First 1

    # ---------- SISTEMA ----------
    Add-Line '=============== SISTEMA ==============='
    Add-Line ('Fabricante/Modelo : {0} {1}' -f $cs.Manufacturer, $cs.Model)
    if ($prod -and $prod.Version -and $prod.Version -ne $cs.Model) { Add-Line ('Nome comercial    : {0}' -f $prod.Version) }
    Add-Line ('BIOS              : {0} ({1})' -f $bios.SMBIOSBIOSVersion, (Format-Date $bios.ReleaseDate))
    Add-Line ('Windows           : {0} — build {1} ({2})' -f $os.Caption, $os.BuildNumber, $os.OSArchitecture)
    if ($os.LastBootUpTime) {
        $up = (Get-Date) - [datetime]$os.LastBootUpTime
        Add-Line ('Ligado há         : {0:N1} dias' -f $up.TotalDays)
        if ($up.TotalDays -gt 3) { Add-Rec 3 ('O Windows está ligado há {0:N0} dias sem reiniciar. Reinicie antes de jogar (o "Desligar" com inicialização rápida não zera a memória).' -f $up.TotalDays) }
    }
    Add-Line ''

    # ---------- PROCESSADOR ----------
    $cpu = Get-CimSafe 'Win32_Processor' | Select-Object -First 1
    $cpuName = ("$($cpu.Name)" -replace '\s+', ' ').Trim()
    Add-Line '=============== PROCESSADOR ==============='
    Add-Line ('Modelo            : {0}' -f $cpuName)
    Add-Line ('Núcleos/Threads   : {0} / {1}' -f $cpu.NumberOfCores, $cpu.NumberOfLogicalProcessors)
    Add-Line ('Clock base        : {0} MHz' -f $cpu.MaxClockSpeed)
    if ($cpu.L3CacheSize) { Add-Line ('Cache L3          : {0} MB' -f ([math]::Round($cpu.L3CacheSize / 1024, 1))) }
    if ($cpuName -match 'i[3579]-(\d)\d{3}U') {
        $gen = [int]$Matches[1]
        Add-Line ('Família           : Core de {0}ª geração série U (15 W, CPU e GPU dividem o limite de energia)' -f $gen)
    }
    $tz = @(Get-CimSafe 'MSAcpi_ThermalZoneTemperature' 'root\wmi')
    $temps = @()
    foreach ($z in $tz) {
        $c = [math]::Round(($z.CurrentTemperature / 10) - 273.15, 0)
        if ($c -gt 0 -and $c -lt 120) { $temps += $c }
    }
    if ($temps.Count -gt 0) {
        $maxT = ($temps | Measure-Object -Maximum).Maximum
        Add-Line ('Temperatura (ACPI): {0} °C (sensor aproximado do Windows)' -f ($temps -join ' / '))
        if ($maxT -ge 80) { Add-Rec 1 ('Temperatura alta mesmo sem jogo ({0} °C). Provável poeira e pasta térmica seca: uma limpeza interna com troca de pasta térmica evita queda de desempenho por calor (thermal throttling).' -f $maxT) }
    } else {
        Add-Line 'Temperatura       : não informada pelo Windows (use o HWiNFO para ver em tempo real)'
    }
    Add-Line ''

    # ---------- GPU ----------
    $gpus = @(Get-CimSafe 'Win32_VideoController')
    Add-Line '=============== PLACA DE VÍDEO ==============='
    $igpu = $null; $dgpu = $null
    foreach ($g in $gpus) {
        Add-Line ('• {0}' -f $g.Name)
        Add-Line ('    Driver {0} de {1}' -f $g.DriverVersion, (Format-Date $g.DriverDate))
        if ($g.CurrentHorizontalResolution) {
            Add-Line ('    Tela: {0} x {1} @ {2} Hz' -f $g.CurrentHorizontalResolution, $g.CurrentVerticalResolution, $g.CurrentRefreshRate)
        }
        if (-not $igpu -and "$($g.Name)" -match 'Intel') { $igpu = $g }
        if (-not $dgpu -and "$($g.Name)" -match 'NVIDIA|GeForce|Radeon|AMD') { $dgpu = $g }
    }
    if ($igpu) {
        $build = $null
        $p = "$($igpu.DriverVersion)".Split('.')
        if ($p.Count -eq 4) {
            $a = 0; $b = 0
            if ([int]::TryParse($p[2], [ref]$a) -and [int]::TryParse($p[3], [ref]$b)) { $build = $a * 10000 + $b }
        }
        if ("$($igpu.Name)" -match '620') {
            Add-Line '    Arquitetura Gen9.5 GT2 — 24 unidades de execução, sem memória própria (usa a RAM).'
        }
        if ($null -ne $build -and $build -lt 1012115) {
            Add-Rec 1 ('Driver da Intel desatualizado ({0}). Instale o pacote oficial "Intel 7th-10th Gen Processor Graphics" (versão 31.0.101.21xx). Botão "Driver Intel" na aba Jogos. O app cria um ponto de restauração antes, se você quiser.' -f $igpu.DriverVersion)
        } elseif ($null -ne $build) {
            Add-Line '    Driver: série legada atual (31.0.101.21xx ou mais novo) — OK.'
        }
        if ($igpu.CurrentHorizontalResolution -ge 1920) {
            Add-Rec 4 'Sua tela é Full HD. Nos jogos, use 1280x720 ou 1366x768 em tela cheia: é a mudança que mais aumenta o FPS na HD 620 (veja o guia na aba Jogos).'
        }
    }
    if ($dgpu) {
        Add-Rec 1 ('Seu notebook também tem GPU dedicada ({0}). Configure cada jogo para usá-la: Configurações > Sistema > Tela > Gráficos > escolha o jogo > Alto desempenho.' -f $dgpu.Name)
    }
    Add-Line ''

    # ---------- RAM ----------
    $mods = @(Get-CimSafe 'Win32_PhysicalMemory')
    $arr  = @(Get-CimSafe 'Win32_PhysicalMemoryArray')
    $typeMap = @{ 20 = 'DDR'; 21 = 'DDR2'; 24 = 'DDR3'; 26 = 'DDR4'; 27 = 'LPDDR'; 28 = 'LPDDR2'; 29 = 'LPDDR3'; 30 = 'LPDDR4'; 34 = 'DDR5'; 35 = 'LPDDR5' }
    $total = ($mods | Measure-Object -Property Capacity -Sum).Sum
    if (-not $total) { $total = $cs.TotalPhysicalMemory }
    $slots = ($arr | Measure-Object -Property MemoryDevices -Sum).Sum
    Add-Line '=============== MEMÓRIA RAM ==============='
    Add-Line ('Total instalado   : {0}' -f (Format-GB $total))
    if ($slots) { Add-Line ('Pentes / slots    : {0} em uso de {1} informados pela placa' -f $mods.Count, $slots) }
    $types = @()
    $caps  = @()
    foreach ($m in $mods) {
        $t = $typeMap[[int]$m.SMBIOSMemoryType]
        if (-not $t) { $t = 'tipo ?' }
        $types += $t
        $caps  += [double]$m.Capacity
        $ff = switch ([int]$m.FormFactor) { 8 { 'DIMM' } 12 { 'SO-DIMM (removível)' } default { 'soldada/onboard?' } }
        Add-Line ('• {0}: {1} {2} {3} — nominal {4} MHz, rodando a {5} MHz — {6} {7}' -f $m.DeviceLocator, (Format-GB $m.Capacity), $t, $ff, $m.Speed, $m.ConfiguredClockSpeed, ("$($m.Manufacturer)").Trim(), ("$($m.PartNumber)").Trim())
    }
    $isLp = (@($types | Where-Object { $_ -like 'LP*' }).Count -gt 0)
    $channel = 'desconhecido'
    if ($mods.Count -ge 2) {
        $equal = (@($caps | Select-Object -Unique).Count -eq 1)
        if ($equal) {
            $channel = 'DUAL-CHANNEL (provável)'
        } else {
            $channel = 'DUAL parcial (Flex Mode — pentes de tamanhos diferentes)'
            Add-Rec 2 'Os pentes têm tamanhos diferentes: só parte da memória trabalha em dual-channel. Para o máximo, use dois pentes do mesmo tamanho (ex.: 2x 8 GB).'
        }
    } elseif ($mods.Count -eq 1) {
        if ($isLp) {
            $channel = 'LPDDR soldada (normalmente já em dual-channel)'
        } else {
            $channel = 'SINGLE-CHANNEL (provável)'
            Add-Rec 1 'RAM em SINGLE-CHANNEL (só 1 pente). Esta é a maior limitação da HD 620: instale um 2º pente DDR4 SO-DIMM do MESMO tamanho. A largura de banda dobra (17 -> 34 GB/s) e o FPS sobe bastante. Confirme no CPU-Z (aba Memory > Channel).'
        }
    }
    Add-Line ('Modo dos canais   : {0}' -f $channel)
    $totalGB = [double]$total / 1GB
    if ($totalGB -lt 7.5) {
        Add-Rec 1 ('Apenas {0:N0} GB de RAM: a GPU integrada usa parte dela. 8 GB é o mínimo para jogos; 16 GB (2x 8 GB) é o ideal.' -f $totalGB)
    } elseif ($totalGB -lt 15) {
        Add-Rec 3 '8 GB atende jogos leves. Para jogos atuais e navegador aberto ao mesmo tempo, 16 GB (2x 8 GB) é o ideal.'
    }
    if ($os.FreePhysicalMemory -and $os.TotalVisibleMemorySize) {
        $usedPct = 100 - ([double]$os.FreePhysicalMemory / [double]$os.TotalVisibleMemorySize * 100)
        Add-Line ('Em uso agora      : {0:N0}% (memória livre {1})' -f $usedPct, (Format-GB ([double]$os.FreePhysicalMemory * 1KB)))
    }
    $pf = @(Get-CimSafe 'Win32_PageFileUsage')
    if ($pf.Count -eq 0) {
        Add-Line 'Arquivo de paginação: DESATIVADO'
        Add-Rec 1 'O arquivo de paginação (memória virtual) está desativado. Com pouca RAM isso fecha jogos sem aviso. Reative: Configurações avançadas do sistema > Desempenho > Avançado > Memória virtual > "Gerenciar automaticamente".'
    } else {
        $auto = if ($cs.AutomaticManagedPagefile) { 'gerenciado pelo Windows' } else { 'tamanho manual' }
        Add-Line ('Arquivo de paginação: {0} MB ({1})' -f (($pf | Measure-Object -Property AllocatedBaseSize -Sum).Sum), $auto)
    }
    Add-Line ''

    # ---------- ARMAZENAMENTO ----------
    Add-Line '=============== ARMAZENAMENTO ==============='
    $sysNum = $null
    try { $sysNum = (Get-Partition -DriveLetter ($env:SystemDrive.Substring(0, 1)) -ErrorAction Stop | Get-Disk -ErrorAction Stop).Number } catch {}
    $pds = @()
    try { $pds = @(Get-PhysicalDisk -ErrorAction Stop) } catch {}
    $sysMedia = ''
    foreach ($pd in $pds) {
        $isSys = ($null -ne $sysNum -and "$($pd.DeviceId)" -eq "$sysNum")
        $mark = ''
        if ($isSys) { $mark = '  [disco do Windows]'; $sysMedia = "$($pd.MediaType)" }
        Add-Line ('• {0} — {1} — {2} — {3} — saúde: {4}{5}' -f $pd.FriendlyName, $pd.MediaType, $pd.BusType, (Format-GB $pd.Size), $pd.HealthStatus, $mark)
        if ("$($pd.HealthStatus)" -ne '' -and "$($pd.HealthStatus)" -ne 'Healthy') {
            Add-Rec 1 ('O disco "{0}" está com alerta de saúde ({1}). Faça backup dos seus arquivos e verifique com o CrystalDiskInfo.' -f $pd.FriendlyName, $pd.HealthStatus)
        }
    }
    if ($sysMedia -eq 'HDD') {
        Add-Rec 1 'O Windows está em um HD mecânico (HDD). Trocar por um SSD é a melhoria de fluidez mais perceptível depois da RAM: o sistema e os jogos carregam muito mais rápido e as travadinhas de carregamento somem.'
    } elseif ($sysMedia -eq 'SSD') {
        Add-Line 'Windows em SSD: ótimo.'
    } else {
        Add-Line 'Tipo do disco do Windows não identificado (verifique no Gerenciador de Tarefas > Desempenho > Disco).'
    }
    $ld = Get-CimSafe 'Win32_LogicalDisk' | Where-Object { $_.DeviceID -eq $env:SystemDrive } | Select-Object -First 1
    if ($ld -and $ld.Size) {
        $freePct = [double]$ld.FreeSpace / [double]$ld.Size * 100
        Add-Line ('Espaço livre em {0} : {1} de {2} ({3:N0}%)' -f $env:SystemDrive, (Format-GB $ld.FreeSpace), (Format-GB $ld.Size), $freePct)
        if ($freePct -lt 15) { Add-Rec 2 ('Pouco espaço livre no disco do Windows ({0:N0}%). Deixe pelo menos 15-20% livre: use "Limpar arquivos temporários" e desinstale o que não usa.' -f $freePct) }
    }
    Add-Line ''

    # ---------- ENERGIA ----------
    Add-Line '=============== ENERGIA ==============='
    Add-Type -AssemblyName System.Windows.Forms
    $pw = [System.Windows.Forms.SystemInformation]::PowerStatus
    $bat = @(Get-CimSafe 'Win32_Battery')
    if ($bat.Count -gt 0) {
        $onAc = ("$($pw.PowerLineStatus)" -eq 'Online')
        $acText = if ($onAc) { 'carregador conectado' } else { 'NA BATERIA' }
        Add-Line ('Bateria           : {0:N0}% — {1}' -f ($pw.BatteryLifePercent * 100), $acText)
        if (-not $onAc) { Add-Rec 1 'Você está na bateria. Para jogar, ligue SEMPRE o carregador: na bateria o Windows e o fabricante limitam CPU e GPU.' }
    } else {
        Add-Line 'Bateria           : não detectada'
    }
    $plan = ((& powercfg.exe /getactivescheme 2>$null) | Out-String).Trim()
    Add-Line ('Plano ativo       : {0}' -f $plan)
    Add-Line ''

    # ---------- WINDOWS ----------
    Add-Line '=============== WINDOWS ==============='
    $gm = $null
    try { $gm = (Get-ItemProperty -LiteralPath 'HKCU:\Software\Microsoft\GameBar' -Name AutoGameModeEnabled -ErrorAction Stop).AutoGameModeEnabled } catch {}
    $gmText = if ($gm -eq 0) { 'desativado' } else { 'ativado/padrão' }
    Add-Line ('Modo de Jogo      : {0}' -f $gmText)
    $dg = @(Get-CimSafe 'Win32_DeviceGuard' 'root\Microsoft\Windows\DeviceGuard')
    if ($dg.Count -gt 0) {
        $vbsOn  = ($dg[0].VirtualizationBasedSecurityStatus -eq 2)
        $hvciOn = (@($dg[0].SecurityServicesRunning) -contains 2)
        $vbsText = if ($vbsOn) { 'ativa' } else { 'inativa' }
        $hvciText = if ($hvciOn) { 'ativa' } else { 'inativa' }
        Add-Line ('Segurança VBS     : {0} | Integridade de memória (HVCI): {1}' -f $vbsText, $hvciText)
        if ($hvciOn) {
            Add-Rec 4 'A "Integridade de memória" (Isolamento de núcleo) está ativa. Ela protege o sistema, mas pode custar alguns % de FPS em CPUs antigas. O app NÃO altera isso por segurança; se quiser, a decisão é sua em Segurança do Windows > Segurança do dispositivo > Isolamento de núcleo.'
        }
    }
    $st = @(Get-CimSafe 'Win32_StartupCommand')
    Add-Line ('Inicialização     : {0} programas abrem com o Windows' -f $st.Count)
    foreach ($s in ($st | Select-Object -First 20)) { Add-Line ('    - {0}' -f $s.Caption) }
    if ($st.Count -gt 6) {
        Add-Rec 2 ('{0} programas abrem junto com o Windows. Desative os que não usa sempre (botão "Gerenciar programas da inicialização" na aba Otimizações): libera RAM e CPU para os jogos.' -f $st.Count)
    }

    Add-Rec 4 'Manutenção física: em notebooks de 2017-2019, uma limpeza das ventoinhas e troca da pasta térmica costuma recuperar muito desempenho perdido por calor. Uma base com cooler também ajuda.'
    Add-Rec 4 'Para medir: rode o teste da aba "Testes" com o rótulo "Antes", aplique as otimizações, reinicie e rode de novo com o rótulo "Depois".'

    $labels = @{ 1 = '[PRIORIDADE ALTA]'; 2 = '[PRIORIDADE MÉDIA]'; 3 = '[PRIORIDADE BAIXA]'; 4 = '[DICA]' }
    $recText = (@($recs | Sort-Object -Property P | ForEach-Object { '{0}{1}{2}' -f $labels[$_.P], [Environment]::NewLine, $_.T })) -join ([Environment]::NewLine + [Environment]::NewLine)

    $short = ($cpuName -replace '\(R\)|\(TM\)|Intel|CPU|@.*$', '' -replace '\s+', ' ').Trim()
    $chip = '{0} • {1} • {2} RAM • {3}' -f ("$($cs.Model)").Trim(), $short, (Format-GB $total), ($channel -replace ' \(.*\)', '')

    [pscustomobject]@{ Text = $sb.ToString(); Recs = $recText; Chip = $chip }
}

# ============================================================================
#  CÓDIGO DOS TRABALHOS EM SEGUNDO PLANO
# ============================================================================
$script:RestorePointCode = @'
$key = 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\SystemRestore'
$had = $false
$old = $null
try {
    $p = Get-ItemProperty -LiteralPath $key -Name SystemRestorePointCreationFrequency -ErrorAction Stop
    $had = $true
    $old = $p.SystemRestorePointCreationFrequency
} catch {}
try {
    try { Enable-ComputerRestore -Drive ($env:SystemDrive + '\') -ErrorAction Stop } catch {}
    New-ItemProperty -LiteralPath $key -Name SystemRestorePointCreationFrequency -PropertyType DWord -Value 0 -Force -ErrorAction Stop | Out-Null
    Checkpoint-Computer -Description 'Projeto de Otimizacao - antes das mudancas' -RestorePointType 'MODIFY_SETTINGS' -ErrorAction Stop
    'OK'
} catch {
    'ERRO: ' + $_.Exception.Message
} finally {
    try {
        if ($had) {
            New-ItemProperty -LiteralPath $key -Name SystemRestorePointCreationFrequency -PropertyType DWord -Value $old -Force | Out-Null
        } else {
            Remove-ItemProperty -LiteralPath $key -Name SystemRestorePointCreationFrequency -ErrorAction SilentlyContinue
        }
    } catch {}
}
'@

$script:CleanTempCode = @'
$dirs = @($env:TEMP, (Join-Path $env:windir 'Temp')) | Where-Object { $_ } | Select-Object -Unique
$limit = (Get-Date).AddHours(-24)
$freed = [long]0
$count = 0
$skipped = 0
foreach ($root in $dirs) {
    if (-not [System.IO.Directory]::Exists($root)) { continue }
    $stack = New-Object System.Collections.Generic.Stack[System.IO.DirectoryInfo]
    $stack.Push((New-Object System.IO.DirectoryInfo($root)))
    while ($stack.Count -gt 0) {
        $cur = $stack.Pop()
        $files = @()
        try { $files = $cur.GetFiles() } catch {}
        foreach ($f in $files) {
            if ($f.LastWriteTime -ge $limit) { continue }
            try {
                $len = $f.Length
                if ($f.IsReadOnly) { $f.IsReadOnly = $false }
                $f.Delete()
                $freed += $len
                $count++
            } catch { $skipped++ }
        }
        $subs = @()
        try { $subs = $cur.GetDirectories() } catch {}
        foreach ($sd in $subs) {
            if (($sd.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -eq 0) { $stack.Push($sd) }
        }
    }
}
'{0}|{1}|{2}' -f $count, $freed, $skipped
'@

# ============================================================================
#  BENCHMARK — histórico
# ============================================================================
# Sempre devolve um ArrayList (evita o comportamento de arrays do ConvertFrom-Json no PowerShell 5.1).
function Read-JsonList([string]$Path) {
    $list = New-Object System.Collections.ArrayList
    if ([System.IO.File]::Exists($Path)) {
        try {
            $data = ConvertFrom-Json -InputObject ([System.IO.File]::ReadAllText($Path))
            foreach ($item in $data) { if ($null -ne $item) { [void]$list.Add($item) } }
        } catch {}
    }
    return ,$list
}

function Write-JsonList([string]$Path, $Items) {
    $json = ConvertTo-Json -InputObject @($Items) -Depth 5
    [System.IO.File]::WriteAllText($Path, $json, $script:Utf8NoBom)
}

function Format-Pct($Old, $New) {
    if (-not $Old -or [double]$Old -eq 0) { return '   —  ' }
    $p = ([double]$New - [double]$Old) / [double]$Old * 100
    return ('{0:+0.0;-0.0;0.0}%' -f $p)
}

function Show-BenchHistory {
    $hist = Read-JsonList $script:BenchFile
    $fps  = Read-JsonList $script:FpsFile
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine('=========== TESTES DE CPU E MEMÓRIA ===========')
    if ($hist.Count -eq 0) {
        [void]$sb.AppendLine('Nenhum teste ainda. Rode o primeiro com o rótulo "Antes".')
    } else {
        [void]$sb.AppendLine(('{0,-17} {1,-16} {2,14} {3,16} {4,14}' -f 'Data', 'Rótulo', 'CPU 1 núcleo', 'CPU todos', 'RAM (GB/s)'))
        foreach ($h in $hist) {
            [void]$sb.AppendLine(('{0,-17} {1,-16} {2,14:N0} {3,16:N0} {4,14:N2}' -f $h.Data, $h.Rotulo, [double]$h.CpuSingle, [double]$h.CpuMulti, [double]$h.MemGBs))
        }
        if ($hist.Count -ge 2) {
            $a = $hist[0]; $b = $hist[$hist.Count - 1]
            [void]$sb.AppendLine('')
            [void]$sb.AppendLine(('Comparação: "{0}" ({1})  ->  "{2}" ({3})' -f $a.Rotulo, $a.Data, $b.Rotulo, $b.Data))
            [void]$sb.AppendLine(('  CPU 1 núcleo : {0}' -f (Format-Pct $a.CpuSingle $b.CpuSingle)))
            [void]$sb.AppendLine(('  CPU todos    : {0}' -f (Format-Pct $a.CpuMulti $b.CpuMulti)))
            [void]$sb.AppendLine(('  Memória RAM  : {0}' -f (Format-Pct $a.MemGBs $b.MemGBs)))
            [void]$sb.AppendLine('  (Variações de até ~3% são normais entre testes.)')
        }
    }
    [void]$sb.AppendLine('')
    [void]$sb.AppendLine('=========== FPS REGISTRADOS NOS JOGOS ===========')
    if ($fps.Count -eq 0) {
        [void]$sb.AppendLine('Nenhum FPS registrado. Meça com o RTSS ou CapFrameX sempre no mesmo trecho do jogo e registre acima.')
    } else {
        [void]$sb.AppendLine(('{0,-17} {1,-16} {2,-24} {3,10} {4,10}' -f 'Data', 'Rótulo', 'Jogo', 'FPS médio', '1% low'))
        foreach ($f in $fps) {
            [void]$sb.AppendLine(('{0,-17} {1,-16} {2,-24} {3,10:N1} {4,10:N1}' -f $f.Data, $f.Rotulo, $f.Jogo, [double]$f.Media, [double]$f.Low))
        }
        $games = @($fps | Select-Object -ExpandProperty Jogo -Unique)
        foreach ($g in $games) {
            $rows = @($fps | Where-Object { $_.Jogo -eq $g })
            if ($rows.Count -ge 2) {
                $a = $rows[0]; $b = $rows[$rows.Count - 1]
                [void]$sb.AppendLine(('  {0}: "{1}" -> "{2}"  FPS médio {3} | 1% low {4}' -f $g, $a.Rotulo, $b.Rotulo, (Format-Pct $a.Media $b.Media), (Format-Pct $a.Low $b.Low)))
            }
        }
    }
    $script:UI['txtBench'].Text = $sb.ToString()
}

function ConvertTo-NumberSafe([string]$Text) {
    $v = 0.0
    $t = "$Text".Trim()
    if ([double]::TryParse($t, [System.Globalization.NumberStyles]::Float, [System.Globalization.CultureInfo]::CurrentCulture, [ref]$v)) { return $v }
    if ([double]::TryParse($t.Replace(',', '.'), [System.Globalization.NumberStyles]::Float, [System.Globalization.CultureInfo]::InvariantCulture, [ref]$v)) { return $v }
    return $null
}

# ============================================================================
#  GUIA DE JOGOS
# ============================================================================
$script:GuideText = @'
COMO EXTRAIR O MÁXIMO DA HD 620 NOS JOGOS
==========================================

ANTES DE JOGAR
 1. Carregador SEMPRE ligado (na bateria a CPU/GPU são limitadas).
 2. Feche navegador, Discord com vídeo, launchers que não está usando.
 3. Abra o jogo e use "Turbo no jogo aberto" (ao lado).

DENTRO DO JOGO (na ordem do que mais dá FPS)
 1. Resolução 1280x720 ou 1366x768, TELA CHEIA.
 2. Escala de renderização 70-85% + FSR (se o jogo tiver).
 3. Sombras: desligado/baixo.  Oclusão de ambiente (SSAO/HBAO): desligado.
 4. Anti-aliasing: desligado ou FXAA (nunca MSAA/TAA alto).
 5. Reflexos, volumétricos, desfoque de movimento, profundidade
    de campo, aberração cromática: desligado.
 6. Texturas: médio com 8 GB dual-channel; baixo com 4 GB/single.
 7. Distância de visão/vegetação: baixo a médio.
 8. API: teste DirectX 11 primeiro (costuma ser o melhor na Gen9).
 9. V-Sync desligado + limite de FPS no RTSS (30, 40 ou 60).
    FPS estável é MUITO mais fluido que FPS oscilando.

PAINEL DA INTEL (Graphics Command Center)
 - Energia: "Desempenho máximo" na tomada.
 - Economia de energia da tela: desligada.
 - 3D global: "usar configurações do aplicativo".
 - Escala: "Manter proporção" para jogar em 720p.

UPSCALING (imagem melhor em resolução baixa)
 - Magpie (grátis): jogue em janela 720p e amplie com FSR.
 - Lossless Scaling (pago, Steam): use só o upscaling (LS1/FSR);
   a geração de quadros é pesada demais para a HD 620.

JOGOS ESPECÍFICOS
 - Minecraft Java: Fabric + Sodium + Lithium (muitas vezes 2-3x FPS).
 - LoL / Valorant / Dota 2 / CS:GO legado / Roblox: 720p baixo
   costumam passar de 40-60 FPS em dual-channel.
 - GTA V: 1024x576 ou 720p, tudo normal/baixo, MSAA off,
   "Escala de resolução" 3/4 se precisar: mire 30 FPS travado.
 - Fortnite: modo "Desempenho", 720p, 3D 75%: 30-60 FPS.
 - Jogos DX9 antigos com problemas: experimente DXVK (experimental).

EXPECTATIVA HONESTA
 - Jogos de 2020+ pesados (Warzone, CS2, AAA) ficam no limite ou
   injogáveis mesmo otimizados. O maior salto real vem de:
   2º pente de RAM (dual-channel) > SSD > limpeza/pasta térmica.

COMO MEDIR
 - RTSS ou CapFrameX: FPS médio e 1% low, mesmo trecho, 60 s.
 - Registre na aba "Testes" com rótulo "Antes" e "Depois".
'@

# ============================================================================
#  INTERFACE (WPF)
# ============================================================================
$xaml = @'
<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        Title="Projeto de Otimização — Intel HD Graphics 620"
        Width="1140" Height="780" MinWidth="920" MinHeight="620"
        WindowStartupLocation="CenterScreen"
        Background="#111318" Foreground="#E6E8EC" FontFamily="Segoe UI" FontSize="13">
  <Window.Resources>
    <Style TargetType="Button">
      <Setter Property="Background" Value="#2563EB"/>
      <Setter Property="Foreground" Value="White"/>
      <Setter Property="Padding" Value="14,7"/>
      <Setter Property="Margin" Value="0,0,8,8"/>
      <Setter Property="Cursor" Value="Hand"/>
      <Setter Property="Template">
        <Setter.Value>
          <ControlTemplate TargetType="Button">
            <Border x:Name="B" Background="{TemplateBinding Background}" CornerRadius="5" Padding="{TemplateBinding Padding}">
              <ContentPresenter HorizontalAlignment="Center" VerticalAlignment="Center"/>
            </Border>
            <ControlTemplate.Triggers>
              <Trigger Property="IsMouseOver" Value="True">
                <Setter TargetName="B" Property="Opacity" Value="0.85"/>
              </Trigger>
              <Trigger Property="IsEnabled" Value="False">
                <Setter TargetName="B" Property="Opacity" Value="0.4"/>
              </Trigger>
            </ControlTemplate.Triggers>
          </ControlTemplate>
        </Setter.Value>
      </Setter>
    </Style>
    <Style TargetType="TabItem">
      <Setter Property="Foreground" Value="#C9D1D9"/>
      <Setter Property="FontSize" Value="14"/>
      <Setter Property="Template">
        <Setter.Value>
          <ControlTemplate TargetType="TabItem">
            <Border x:Name="Bd" Background="#1C1F26" BorderBrush="#2A2F3A" BorderThickness="1,1,1,0" CornerRadius="6,6,0,0" Padding="16,8" Margin="0,0,4,0">
              <ContentPresenter ContentSource="Header" VerticalAlignment="Center"/>
            </Border>
            <ControlTemplate.Triggers>
              <Trigger Property="IsSelected" Value="True">
                <Setter TargetName="Bd" Property="Background" Value="#2563EB"/>
                <Setter Property="Foreground" Value="White"/>
              </Trigger>
            </ControlTemplate.Triggers>
          </ControlTemplate>
        </Setter.Value>
      </Setter>
    </Style>
    <Style x:Key="Mono" TargetType="TextBox">
      <Setter Property="IsReadOnly" Value="True"/>
      <Setter Property="TextWrapping" Value="Wrap"/>
      <Setter Property="VerticalScrollBarVisibility" Value="Auto"/>
      <Setter Property="Background" Value="#0B0D11"/>
      <Setter Property="Foreground" Value="#D1D5DB"/>
      <Setter Property="BorderBrush" Value="#262B35"/>
      <Setter Property="FontFamily" Value="Consolas"/>
      <Setter Property="FontSize" Value="12.5"/>
      <Setter Property="Padding" Value="8"/>
    </Style>
    <Style x:Key="Input" TargetType="TextBox">
      <Setter Property="Background" Value="#0B0D11"/>
      <Setter Property="Foreground" Value="White"/>
      <Setter Property="CaretBrush" Value="White"/>
      <Setter Property="BorderBrush" Value="#374151"/>
      <Setter Property="Padding" Value="4"/>
      <Setter Property="Margin" Value="0,0,8,8"/>
      <Setter Property="VerticalContentAlignment" Value="Center"/>
    </Style>
    <Style x:Key="Card" TargetType="Border">
      <Setter Property="Background" Value="#1C1F26"/>
      <Setter Property="CornerRadius" Value="6"/>
      <Setter Property="Padding" Value="12"/>
      <Setter Property="Margin" Value="0,0,0,10"/>
    </Style>
    <Style x:Key="Hint" TargetType="TextBlock">
      <Setter Property="Foreground" Value="#9CA3AF"/>
      <Setter Property="TextWrapping" Value="Wrap"/>
      <Setter Property="Margin" Value="0,0,0,8"/>
    </Style>
    <Style x:Key="H" TargetType="TextBlock">
      <Setter Property="FontWeight" Value="SemiBold"/>
      <Setter Property="FontSize" Value="14"/>
      <Setter Property="Margin" Value="0,0,0,6"/>
    </Style>
  </Window.Resources>

  <Grid Margin="16">
    <Grid.RowDefinitions>
      <RowDefinition Height="Auto"/>
      <RowDefinition Height="*"/>
      <RowDefinition Height="120"/>
      <RowDefinition Height="Auto"/>
    </Grid.RowDefinitions>

    <DockPanel Grid.Row="0" Margin="0,0,0,12">
      <Border DockPanel.Dock="Right" Background="#1C1F26" CornerRadius="6" Padding="12,6" VerticalAlignment="Center" MaxWidth="560">
        <TextBlock x:Name="txtChip" Text="Analisando o computador..." Foreground="#9CA3AF" TextTrimming="CharacterEllipsis"/>
      </Border>
      <StackPanel>
        <TextBlock Text="Projeto de Otimização" FontSize="24" FontWeight="Bold"/>
        <TextBlock Text="Desempenho seguro e 100% reversível para notebooks com Intel HD/UHD Graphics 620" Foreground="#9CA3AF"/>
      </StackPanel>
    </DockPanel>

    <TabControl x:Name="tabs" Grid.Row="1" Background="#111318" BorderThickness="0" Padding="0">

      <TabItem Header="1. Diagnóstico">
        <Grid Margin="0,12,0,0">
          <Grid.RowDefinitions>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="*"/>
          </Grid.RowDefinitions>
          <WrapPanel>
            <Button x:Name="btnAnalyze" Content="Analisar computador"/>
            <Button x:Name="btnSaveReport" Content="Salvar relatório (.txt)" Background="#374151"/>
          </WrapPanel>
          <Grid Grid.Row="1">
            <Grid.ColumnDefinitions>
              <ColumnDefinition Width="3*"/>
              <ColumnDefinition Width="12"/>
              <ColumnDefinition Width="2*"/>
            </Grid.ColumnDefinitions>
            <TextBox x:Name="txtDiag" Style="{StaticResource Mono}" TextWrapping="NoWrap" HorizontalScrollBarVisibility="Auto"/>
            <DockPanel Grid.Column="2">
              <TextBlock DockPanel.Dock="Top" Text="Recomendações para o SEU computador" Style="{StaticResource H}"/>
              <TextBox x:Name="txtRec" Style="{StaticResource Mono}"/>
            </DockPanel>
          </Grid>
        </Grid>
      </TabItem>

      <TabItem Header="2. Otimizações">
        <Grid Margin="0,12,0,0">
          <Grid.RowDefinitions>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="*"/>
            <RowDefinition Height="Auto"/>
          </Grid.RowDefinitions>
          <TextBlock Style="{StaticResource Hint}" Text="Cada otimização salva o valor original antes de mudar qualquer coisa e pode ser desfeita a qualquer momento. Um ponto de restauração do Windows é criado automaticamente antes da primeira aplicação. Depois de aplicar, reinicie o computador."/>
          <WrapPanel Grid.Row="1">
            <Button x:Name="btnApply" Content="Aplicar selecionadas"/>
            <Button x:Name="btnSelectRec" Content="Marcar recomendadas" Background="#374151"/>
            <Button x:Name="btnUndoSel" Content="Desfazer selecionadas" Background="#B45309"/>
            <Button x:Name="btnRestorePoint" Content="Criar ponto de restauração" Background="#374151"/>
            <Button x:Name="btnRefresh" Content="Atualizar status" Background="#374151"/>
          </WrapPanel>
          <ScrollViewer Grid.Row="2" VerticalScrollBarVisibility="Auto">
            <StackPanel x:Name="pnlTweaks" Margin="0,0,8,0"/>
          </ScrollViewer>
          <Border Grid.Row="3" Style="{StaticResource Card}" Margin="0,8,0,0">
            <StackPanel>
              <TextBlock Text="Manutenção rápida" Style="{StaticResource H}"/>
              <WrapPanel>
                <Button x:Name="btnCleanTemp" Content="Limpar arquivos temporários" Background="#374151"/>
                <Button x:Name="btnPurgeRam" Content="Liberar memória em espera (RAM)" Background="#374151"/>
                <Button x:Name="btnStartup" Content="Gerenciar programas da inicialização" Background="#374151"/>
                <Button x:Name="btnGfxSettings" Content="Configurações de gráficos do Windows" Background="#374151"/>
                <Button x:Name="btnIntelPanel" Content="Abrir painel da Intel" Background="#374151"/>
              </WrapPanel>
            </StackPanel>
          </Border>
        </Grid>
      </TabItem>

      <TabItem Header="3. Testes">
        <Grid Margin="0,12,0,0">
          <Grid.RowDefinitions>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="*"/>
          </Grid.RowDefinitions>
          <TextBlock Style="{StaticResource Hint}" Text="Rode o teste ANTES de otimizar (rótulo &quot;Antes&quot;) e DEPOIS de aplicar e reiniciar (rótulo &quot;Depois&quot;). Sempre com o carregador ligado, notebook em repouso há alguns minutos e sem programas abertos. O teste de RAM mostra claramente a diferença entre single e dual-channel."/>
          <WrapPanel Grid.Row="1">
            <TextBlock Text="Rótulo do teste:" VerticalAlignment="Center" Margin="0,0,8,8"/>
            <TextBox x:Name="txtLabel" Width="170" Text="Antes" Style="{StaticResource Input}"/>
            <Button x:Name="btnBench" Content="Rodar teste de CPU + RAM (~20 s)"/>
            <Button x:Name="btnHistory" Content="Ver histórico e comparação" Background="#374151"/>
          </WrapPanel>
          <Border Grid.Row="2" Style="{StaticResource Card}">
            <WrapPanel>
              <TextBlock Text="Registrar FPS de um jogo   Jogo:" VerticalAlignment="Center" Margin="0,0,8,8"/>
              <TextBox x:Name="txtGame" Width="180" Style="{StaticResource Input}"/>
              <TextBlock Text="FPS médio:" VerticalAlignment="Center" Margin="0,0,8,8"/>
              <TextBox x:Name="txtFpsAvg" Width="70" Style="{StaticResource Input}"/>
              <TextBlock Text="1% low:" VerticalAlignment="Center" Margin="0,0,8,8"/>
              <TextBox x:Name="txtFpsLow" Width="70" Style="{StaticResource Input}"/>
              <Button x:Name="btnAddFps" Content="Registrar FPS"/>
            </WrapPanel>
          </Border>
          <TextBox x:Name="txtBench" Grid.Row="3" Style="{StaticResource Mono}" TextWrapping="NoWrap" HorizontalScrollBarVisibility="Auto"/>
        </Grid>
      </TabItem>

      <TabItem Header="4. Jogos">
        <Grid Margin="0,12,0,0">
          <Grid.ColumnDefinitions>
            <ColumnDefinition Width="*"/>
            <ColumnDefinition Width="12"/>
            <ColumnDefinition Width="*"/>
          </Grid.ColumnDefinitions>
          <ScrollViewer VerticalScrollBarVisibility="Auto">
            <StackPanel Margin="0,0,8,0">
              <Border Style="{StaticResource Card}">
                <StackPanel>
                  <TextBlock Text="Turbo no jogo aberto" Style="{StaticResource H}"/>
                  <TextBlock Style="{StaticResource Hint}" Text="Abra o jogo normalmente, volte aqui, escolha o jogo e clique em Turbo: ele passa para prioridade ALTA e a memória em espera é liberada. Vale até fechar o jogo."/>
                  <ComboBox x:Name="cmbProc" Foreground="Black" Margin="0,0,0,8"/>
                  <WrapPanel>
                    <Button x:Name="btnProcRefresh" Content="Atualizar lista" Background="#374151"/>
                    <Button x:Name="btnTurbo" Content="Turbo agora"/>
                  </WrapPanel>
                </StackPanel>
              </Border>
              <Border Style="{StaticResource Card}">
                <StackPanel>
                  <TextBlock Text="Prioridade alta permanente" Style="{StaticResource H}"/>
                  <TextBlock Style="{StaticResource Hint}" Text="Escolha o .exe do jogo e o Windows passará a abri-lo sempre em prioridade alta (recurso oficial do Windows). Se algum jogo online reclamar, remova aqui."/>
                  <WrapPanel>
                    <Button x:Name="btnPermAdd" Content="Escolher .exe do jogo..."/>
                  </WrapPanel>
                  <ComboBox x:Name="cmbPerm" Foreground="Black" Margin="0,0,0,8"/>
                  <WrapPanel>
                    <Button x:Name="btnPermRemove" Content="Remover prioridade do selecionado" Background="#B45309"/>
                  </WrapPanel>
                </StackPanel>
              </Border>
              <Border Style="{StaticResource Card}">
                <StackPanel>
                  <TextBlock Text="Ferramentas recomendadas (sites oficiais)" Style="{StaticResource H}"/>
                  <WrapPanel x:Name="pnlLinks">
                    <Button Content="Driver Intel (7ª-10ª ger.)" Background="#374151" Tag="https://www.intel.com/content/www/us/en/download/776137/intel-7th-10th-gen-processor-graphics-windows.html"/>
                    <Button Content="Intel Driver Assistant" Background="#374151" Tag="https://www.intel.com/content/www/us/en/support/intel-driver-support-assistant.html"/>
                    <Button Content="CPU-Z (ver dual-channel)" Background="#374151" Tag="https://www.cpuid.com/softwares/cpu-z.html"/>
                    <Button Content="HWiNFO (temperaturas)" Background="#374151" Tag="https://www.hwinfo.com/download/"/>
                    <Button Content="CrystalDiskInfo (saúde do disco)" Background="#374151" Tag="https://crystalmark.info/en/software/crystaldiskinfo/"/>
                    <Button Content="RTSS (limite/contador de FPS)" Background="#374151" Tag="https://www.guru3d.com/download/rtss-rivatuner-statistics-server-download/"/>
                    <Button Content="CapFrameX (medir FPS)" Background="#374151" Tag="https://www.capframex.com/"/>
                    <Button Content="Magpie (upscaling grátis)" Background="#374151" Tag="https://github.com/Blinue/Magpie/releases"/>
                    <Button Content="Lossless Scaling (Steam)" Background="#374151" Tag="https://store.steampowered.com/app/993090/Lossless_Scaling/"/>
                    <Button Content="Sodium (Minecraft)" Background="#374151" Tag="https://modrinth.com/mod/sodium"/>
                    <Button Content="Unigine Heaven (teste de GPU)" Background="#374151" Tag="https://benchmark.unigine.com/heaven"/>
                  </WrapPanel>
                </StackPanel>
              </Border>
            </StackPanel>
          </ScrollViewer>
          <DockPanel Grid.Column="2">
            <TextBlock DockPanel.Dock="Top" Text="Guia de configurações para jogos" Style="{StaticResource H}"/>
            <TextBox x:Name="txtGuide" Style="{StaticResource Mono}"/>
          </DockPanel>
        </Grid>
      </TabItem>

      <TabItem Header="5. Restaurar">
        <StackPanel Margin="0,12,0,0" MaxWidth="820" HorizontalAlignment="Left">
          <Border Style="{StaticResource Card}">
            <StackPanel>
              <TextBlock Text="Voltar tudo como era" Style="{StaticResource H}"/>
              <TextBlock Style="{StaticResource Hint}" Text="O app guarda o valor original de cada configuração em Dados\Backups\estado.json. &quot;Desfazer TODAS&quot; restaura exatamente esses valores, volta ao seu plano de energia anterior, apaga o plano criado pelo app e remove as prioridades permanentes. Como segunda camada de segurança existe o ponto de restauração do Windows."/>
              <WrapPanel>
                <Button x:Name="btnUndoAll" Content="Desfazer TODAS as otimizações" Background="#B91C1C"/>
                <Button x:Name="btnRstrui" Content="Abrir Restauração do Sistema" Background="#374151"/>
                <Button x:Name="btnOpenFolder" Content="Abrir pasta do projeto" Background="#374151"/>
              </WrapPanel>
            </StackPanel>
          </Border>
          <Border Style="{StaticResource Card}">
            <StackPanel>
              <TextBlock Text="O que este app NUNCA faz" Style="{StaticResource H}"/>
              <TextBlock Style="{StaticResource Hint}" Text="Não desativa antivírus, firewall, atualizações ou serviços do Windows; não mexe em BIOS, tensão (undervolt) ou overclock; não apaga cache de shaders; não desativa o arquivo de paginação; não instala nada sem você clicar. Detalhes no arquivo PESQUISA.md."/>
            </StackPanel>
          </Border>
        </StackPanel>
      </TabItem>
    </TabControl>

    <Border Grid.Row="2" Margin="0,12,0,0" Background="#0B0D11" CornerRadius="6" BorderBrush="#262B35" BorderThickness="1">
      <TextBox x:Name="txtLog" Style="{StaticResource Mono}" BorderThickness="0"/>
    </Border>
    <TextBlock x:Name="txtStatus" Grid.Row="3" Margin="0,8,0,0" Foreground="#9CA3AF" Text="Pronto."/>
  </Grid>
</Window>
'@

# ============================================================================
#  CONSTRUÇÃO DA INTERFACE
# ============================================================================
function Initialize-TweakPanel {
    $panel = $script:UI['pnlTweaks']
    $panel.Children.Clear()
    $script:TweakChecks = @{}
    $script:TweakStatus = @{}
    foreach ($t in $script:Tweaks) {
        if ($t.Win11Only -and -not $script:IsWin11) { continue }

        $border = New-Object System.Windows.Controls.Border
        $border.Background   = Get-Brush '#1C1F26'
        $border.CornerRadius = New-Object System.Windows.CornerRadius(6)
        $border.Padding      = New-Object System.Windows.Thickness(12, 10, 12, 10)
        $border.Margin       = New-Object System.Windows.Thickness(0, 0, 0, 8)

        $dock = New-Object System.Windows.Controls.DockPanel

        $status = New-Object System.Windows.Controls.TextBlock
        $status.Margin = New-Object System.Windows.Thickness(12, 2, 0, 0)
        $status.VerticalAlignment = [System.Windows.VerticalAlignment]::Top
        $status.FontWeight = [System.Windows.FontWeights]::SemiBold
        [System.Windows.Controls.DockPanel]::SetDock($status, [System.Windows.Controls.Dock]::Right)

        $cb = New-Object System.Windows.Controls.CheckBox
        $cb.IsChecked  = [bool]$t.Recommended
        $cb.Foreground = Get-Brush '#E6E8EC'
        $cb.VerticalContentAlignment = [System.Windows.VerticalAlignment]::Top

        $sp = New-Object System.Windows.Controls.StackPanel
        $sp.Margin = New-Object System.Windows.Thickness(6, 0, 0, 0)
        $title = New-Object System.Windows.Controls.TextBlock
        $title.Text = $t.Name
        $title.FontSize = 14
        $title.FontWeight = [System.Windows.FontWeights]::SemiBold
        $title.TextWrapping = [System.Windows.TextWrapping]::Wrap
        $desc = New-Object System.Windows.Controls.TextBlock
        $desc.Text = $t.Desc
        $desc.Foreground = Get-Brush '#9CA3AF'
        $desc.TextWrapping = [System.Windows.TextWrapping]::Wrap
        $desc.Margin = New-Object System.Windows.Thickness(0, 3, 0, 0)
        [void]$sp.Children.Add($title)
        [void]$sp.Children.Add($desc)
        $cb.Content = $sp

        [void]$dock.Children.Add($status)
        [void]$dock.Children.Add($cb)
        $border.Child = $dock
        [void]$panel.Children.Add($border)

        $script:TweakChecks[$t.Id] = $cb
        $script:TweakStatus[$t.Id] = $status
    }
}

function Update-TweakStatus {
    $active = 0
    $shown = 0
    foreach ($t in $script:Tweaks) {
        if (-not $script:TweakStatus.ContainsKey($t.Id)) { continue }
        $shown++
        $applied = $false
        try { $applied = [bool](Test-TweakApplied $t) } catch {}
        $lbl = $script:TweakStatus[$t.Id]
        if ($applied) {
            $active++
            $lbl.Text = '✔ Ativa'
            $lbl.Foreground = Get-Brush '#34D399'
        } else {
            $lbl.Text = '○ Não aplicada'
            $lbl.Foreground = Get-Brush '#9CA3AF'
        }
    }
    Write-Log ('Status: {0} de {1} otimizações ativas.' -f $active, $shown)
}

function Get-SelectedTweaks {
    $list = @()
    foreach ($t in $script:Tweaks) {
        if ($script:TweakChecks.ContainsKey($t.Id) -and $script:TweakChecks[$t.Id].IsChecked -eq $true) { $list += $t }
    }
    return ,$list
}

function Update-PermCombo {
    $cmb = $script:UI['cmbPerm']
    $cmb.Items.Clear()
    foreach ($k in ($script:Backup.ifeo.Keys | Sort-Object)) { [void]$cmb.Items.Add($k) }
    if ($cmb.Items.Count -gt 0) { $cmb.SelectedIndex = 0 }
}

function Update-ProcList {
    $cmb = $script:UI['cmbProc']
    $cmb.Items.Clear()
    $script:ProcList = @(Get-Process -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowHandle -ne [IntPtr]::Zero -and $_.Id -ne $PID -and $_.ProcessName -notin @('explorer', 'ApplicationFrameHost', 'TextInputHost', 'SystemSettings') } |
        Sort-Object -Property WorkingSet64 -Descending)
    foreach ($p in $script:ProcList) {
        $title = "$($p.MainWindowTitle)"
        if ($title.Length -gt 45) { $title = $title.Substring(0, 45) + '...' }
        [void]$cmb.Items.Add(('{0}   (PID {1}, {2:N0} MB)   {3}' -f $p.ProcessName, $p.Id, ($p.WorkingSet64 / 1MB), $title))
    }
    if ($cmb.Items.Count -gt 0) { $cmb.SelectedIndex = 0 }
}

function Invoke-PurgeStandby {
    $before = 0; $after = 0
    try { $before = [double](Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory } catch {}
    $r = [OtzNative]::PurgeStandbyList()
    try { $after = [double](Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory } catch {}
    if ($r -eq 0) {
        Write-Log ('Memória em espera liberada: livre {0:N0} MB -> {1:N0} MB.' -f ($before / 1024), ($after / 1024))
        return $true
    }
    Write-Log ('Não foi possível liberar a memória em espera (código {0}).' -f $r)
    return $false
}

function Invoke-ApplySelected($Selected) {
    Set-Busy $true 'Aplicando otimizações...'
    $ok = 0; $fail = 0
    foreach ($t in $Selected) {
        try {
            Invoke-ApplyTweak $t
            $ok++
            Write-Log ('OK: {0}' -f $t.Name)
        } catch {
            $fail++
            Write-Log ('FALHOU: {0} — {1}' -f $t.Name, $_.Exception.Message)
        }
        Update-UI
    }
    Update-TweakStatus
    Set-Busy $false
    $msg = '{0} otimização(ões) aplicada(s).' -f $ok
    if ($fail -gt 0) { $msg += "`n{0} não puderam ser aplicadas (veja o registro na parte de baixo da janela)." -f $fail }
    $msg += "`n`nReinicie o computador para que todas as mudanças tenham efeito."
    Show-Msg $msg
}

function Start-RestorePoint([scriptblock]$Then) {
    $script:AfterRestore = $Then
    Set-Busy $true 'Criando ponto de restauração do Windows (pode levar 1-2 minutos)...'
    Write-Log 'Criando ponto de restauração do Windows...'
    Start-OtzJob -Code $script:RestorePointCode -OnDone {
        param($out, $err)
        Set-Busy $false
        $msg = $err
        if (-not $msg -and $out -and $out.Count -gt 0) { $msg = "$($out[$out.Count - 1])" }
        $ok = ("$msg" -like 'OK*')
        if ($ok) {
            $script:RestorePointDone = $true
            Write-Log 'Ponto de restauração criado com sucesso.'
        } else {
            Write-Log ('Não foi possível criar o ponto de restauração: {0}' -f $msg)
        }
        $next = $script:AfterRestore
        $script:AfterRestore = $null
        if ($next) { & $next $ok }
    }
}

function Start-Diagnostics {
    Set-Busy $true 'Analisando o hardware e o Windows...'
    Write-Log 'Analisando o computador...'
    $code = 'function Get-OtzDiagnostics {' + [Environment]::NewLine + ${function:Get-OtzDiagnostics}.ToString() + [Environment]::NewLine + '}' + [Environment]::NewLine + 'Get-OtzDiagnostics'
    Start-OtzJob -Code $code -OnDone {
        param($out, $err)
        Set-Busy $false
        if ($err -or -not $out -or $out.Count -eq 0) {
            Write-Log ('Falha na análise: {0}' -f $err)
            return
        }
        $res = $out[$out.Count - 1]
        $applied = 0; $shown = 0
        foreach ($t in $script:Tweaks) {
            if ($t.Win11Only -and -not $script:IsWin11) { continue }
            $shown++
            try { if (Test-TweakApplied $t) { $applied++ } } catch {}
        }
        $rec = $res.Recs
        if ($applied -lt $shown) {
            $rec = ('[PRIORIDADE ALTA]{0}{1} de {2} otimizações seguras ainda não estão ativas. Vá na aba "2. Otimizações", clique em "Aplicar selecionadas" e reinicie.{0}{0}' -f [Environment]::NewLine, ($shown - $applied), $shown) + $rec
        }
        $script:LastDiagText = $res.Text
        $script:LastRecText  = $rec
        $script:UI['txtDiag'].Text = $res.Text
        $script:UI['txtRec'].Text  = $rec
        $script:UI['txtChip'].Text = $res.Chip
        $script:UI['txtChip'].ToolTip = $res.Chip
        Write-Log 'Análise concluída.'
    }
}

# ============================================================================
#  EVENTOS
# ============================================================================
function Register-Events {
    $ui = $script:UI

    $ui['btnAnalyze'].Add_Click({ Start-Diagnostics })

    $ui['btnSaveReport'].Add_Click({
        if (-not $script:LastDiagText) { Show-Msg 'Rode a análise primeiro.'; return }
        $file = Join-Path $script:ReportDir ('diagnostico-{0}.txt' -f (Get-Date -Format 'yyyy-MM-dd_HH-mm'))
        $content = 'PROJETO DE OTIMIZAÇÃO — RELATÓRIO ' + (Get-Date -Format 'dd/MM/yyyy HH:mm') + [Environment]::NewLine + [Environment]::NewLine +
                   $script:LastDiagText + [Environment]::NewLine + '=============== RECOMENDAÇÕES ===============' + [Environment]::NewLine + $script:LastRecText
        [System.IO.File]::WriteAllText($file, $content, (New-Object System.Text.UTF8Encoding($true)))
        Write-Log "Relatório salvo em $file"
        try { Start-Process notepad.exe -ArgumentList ('"{0}"' -f $file) } catch {}
    })

    $ui['btnSelectRec'].Add_Click({
        foreach ($t in $script:Tweaks) {
            if ($script:TweakChecks.ContainsKey($t.Id)) { $script:TweakChecks[$t.Id].IsChecked = [bool]$t.Recommended }
        }
    })

    $ui['btnRefresh'].Add_Click({ Update-TweakStatus })

    $ui['btnRestorePoint'].Add_Click({
        Start-RestorePoint -Then {
            param($ok)
            if ($ok) { Show-Msg 'Ponto de restauração criado com sucesso.' }
            else { Show-Msg "Não foi possível criar o ponto de restauração.`nVerifique se a Proteção do Sistema está disponível no disco C: (Painel de Controle > Sistema > Proteção do sistema)." 'Warning' }
        }
    })

    $ui['btnApply'].Add_Click({
        $sel = Get-SelectedTweaks
        if ($sel.Count -eq 0) { Show-Msg 'Marque pelo menos uma otimização.'; return }
        $names = ($sel | ForEach-Object { '• ' + $_.Name }) -join "`n"
        if (-not (Show-Confirm ("Aplicar as otimizações abaixo?`n`n" + $names + "`n`nTudo é salvo antes e pode ser desfeito depois."))) { return }
        $script:PendingApply = $sel
        if ($script:RestorePointDone) {
            Invoke-ApplySelected $script:PendingApply
        } else {
            Start-RestorePoint -Then {
                param($ok)
                if (-not $ok) {
                    if (-not (Show-Confirm "Não foi possível criar o ponto de restauração do Windows.`n`nOs backups do próprio app continuam funcionando (todas as mudanças podem ser desfeitas pela aba Restaurar).`n`nContinuar mesmo assim?")) { return }
                }
                Invoke-ApplySelected $script:PendingApply
            }
        }
    })

    $ui['btnUndoSel'].Add_Click({
        $sel = Get-SelectedTweaks
        if ($sel.Count -eq 0) { Show-Msg 'Marque as otimizações que deseja desfazer.'; return }
        if (-not (Show-Confirm 'Desfazer as otimizações marcadas e voltar aos valores originais?')) { return }
        Set-Busy $true 'Desfazendo...'
        foreach ($t in $sel) {
            try {
                if (Invoke-RevertTweak $t.Id) { Write-Log ('Desfeito: {0}' -f $t.Name) }
                else { Write-Log ('Nada a desfazer (não foi aplicado por este app): {0}' -f $t.Name) }
            } catch { Write-Log ('FALHOU ao desfazer {0}: {1}' -f $t.Name, $_.Exception.Message) }
        }
        Update-TweakStatus
        Set-Busy $false
        Show-Msg 'Pronto. Reinicie o computador para concluir.'
    })

    $ui['btnUndoAll'].Add_Click({
        if (-not (Show-Confirm 'Desfazer TODAS as mudanças feitas por este app e voltar ao estado original?')) { return }
        Set-Busy $true 'Desfazendo todas as otimizações...'
        foreach ($id in @($script:Backup.tweaks.Keys)) {
            try { [void](Invoke-RevertTweak $id); Write-Log ('Desfeito: {0}' -f (Get-TweakName $id)) }
            catch { Write-Log ('FALHOU ao desfazer {0}: {1}' -f $id, $_.Exception.Message) }
        }
        if ($script:Backup.powerplan) {
            try { Disable-OtzPowerPlan; Write-Log 'Plano de energia original restaurado.' }
            catch { Write-Log ('FALHOU ao restaurar o plano de energia: {0}' -f $_.Exception.Message) }
        }
        foreach ($exe in @($script:Backup.ifeo.Keys)) {
            try { Remove-PermanentPriority $exe; Write-Log ('Prioridade permanente removida: {0}' -f $exe) }
            catch { Write-Log ('FALHOU ao remover prioridade de {0}: {1}' -f $exe, $_.Exception.Message) }
        }
        Update-PermCombo
        Update-TweakStatus
        Set-Busy $false
        Show-Msg 'Tudo foi restaurado. Reinicie o computador para concluir.'
    })

    $ui['btnCleanTemp'].Add_Click({
        if (-not (Show-Confirm "Apagar arquivos temporários com mais de 24 horas (pastas TEMP do usuário e do Windows)?`n`nArquivos em uso são ignorados automaticamente.")) { return }
        Set-Busy $true 'Limpando arquivos temporários...'
        Start-OtzJob -Code $script:CleanTempCode -OnDone {
            param($out, $err)
            Set-Busy $false
            if ($err -or -not $out -or $out.Count -eq 0) { Write-Log ('Falha na limpeza: {0}' -f $err); return }
            $parts = "$($out[$out.Count - 1])".Split('|')
            $msg = 'Limpeza concluída: {0} arquivos removidos, {1:N0} MB liberados ({2} em uso foram ignorados).' -f $parts[0], ([double]$parts[1] / 1MB), $parts[2]
            Write-Log $msg
            Show-Msg $msg
        }
    })

    $ui['btnPurgeRam'].Add_Click({
        if (Invoke-PurgeStandby) { Show-Msg 'Memória em espera liberada. Ideal para fazer logo antes de abrir um jogo.' }
        else { Show-Msg 'Não foi possível liberar a memória em espera neste computador.' 'Warning' }
    })

    $ui['btnStartup'].Add_Click({
        try { Start-Process 'ms-settings:startupapps' -ErrorAction Stop }
        catch { try { Start-Process taskmgr.exe } catch {} }
        Write-Log 'Abrindo programas de inicialização. Desative o que não precisa abrir com o Windows (pode reativar depois no mesmo lugar).'
    })

    $ui['btnGfxSettings'].Add_Click({
        try { Start-Process 'ms-settings:display-advancedgraphics' -ErrorAction Stop } catch { Write-Log 'Não foi possível abrir as configurações de gráficos.' }
    })

    $ui['btnIntelPanel'].Add_Click({
        try {
            Start-Process 'shell:AppsFolder\AppUp.IntelGraphicsExperience_8j3eq9eme6ctt!App' -ErrorAction Stop
            Write-Log 'Abrindo Intel Graphics Command Center.'
        } catch {
            Write-Log 'Intel Graphics Command Center não encontrado. Abrindo a página dele na Microsoft Store.'
            Open-Url 'ms-windows-store://pdp/?ProductId=9PLFNLNT3G5G'
        }
    })

    $ui['btnBench'].Add_Click({
        $label = "$($script:UI['txtLabel'].Text)".Trim()
        if (-not $label) { $label = 'Teste' }
        $script:BenchLabel = $label
        Set-Busy $true 'Teste em andamento (cerca de 20 segundos). Não mexa no computador...'
        Write-Log ('Iniciando teste "{0}"...' -f $label)
        [OtzNative]::StartBenchmark([Environment]::ProcessorCount)
        $script:BenchTimer.Start()
    })

    $script:BenchTimer = New-Object System.Windows.Threading.DispatcherTimer
    $script:BenchTimer.Interval = [TimeSpan]::FromMilliseconds(500)
    $script:BenchTimer.Add_Tick({
        $state = [OtzNative]::BenchState
        if ($state -eq 1) { return }
        $script:BenchTimer.Stop()
        Set-Busy $false
        $r = [OtzNative]::BenchResult
        if ($state -ne 2 -or $null -eq $r -or $r.Length -lt 3) {
            Write-Log ('Falha no teste: {0}' -f [OtzNative]::BenchError)
            return
        }
        $entry = [pscustomobject]@{
            Data      = (Get-Date -Format 'dd/MM/yyyy HH:mm')
            Rotulo    = $script:BenchLabel
            CpuSingle = [math]::Round($r[0], 0)
            CpuMulti  = [math]::Round($r[1], 0)
            MemGBs    = [math]::Round($r[2], 2)
        }
        $hist = Read-JsonList $script:BenchFile
        [void]$hist.Add($entry)
        Write-JsonList $script:BenchFile $hist
        Write-Log ('Teste "{0}": CPU 1 núcleo {1:N0} | CPU todos {2:N0} | RAM {3:N2} GB/s' -f $entry.Rotulo, $entry.CpuSingle, $entry.CpuMulti, $entry.MemGBs)
        Show-BenchHistory
    })

    $ui['btnHistory'].Add_Click({ Show-BenchHistory })

    $ui['btnAddFps'].Add_Click({
        $game = "$($script:UI['txtGame'].Text)".Trim()
        $avg  = ConvertTo-NumberSafe $script:UI['txtFpsAvg'].Text
        $low  = ConvertTo-NumberSafe $script:UI['txtFpsLow'].Text
        if (-not $game -or $null -eq $avg) { Show-Msg 'Preencha pelo menos o nome do jogo e o FPS médio.'; return }
        if ($null -eq $low) { $low = 0 }
        $label = "$($script:UI['txtLabel'].Text)".Trim()
        if (-not $label) { $label = 'Teste' }
        $list = Read-JsonList $script:FpsFile
        [void]$list.Add([pscustomobject]@{ Data = (Get-Date -Format 'dd/MM/yyyy HH:mm'); Rotulo = $label; Jogo = $game; Media = $avg; Low = $low })
        Write-JsonList $script:FpsFile $list
        Write-Log ('FPS registrado: {0} [{1}] média {2} / 1% low {3}' -f $game, $label, $avg, $low)
        $script:UI['txtFpsAvg'].Text = ''
        $script:UI['txtFpsLow'].Text = ''
        Show-BenchHistory
    })

    $ui['btnProcRefresh'].Add_Click({ Update-ProcList })

    $ui['btnTurbo'].Add_Click({
        $i = $script:UI['cmbProc'].SelectedIndex
        if ($i -lt 0 -or $i -ge $script:ProcList.Count) { Show-Msg 'Abra o jogo, clique em "Atualizar lista" e escolha o jogo.'; return }
        $p = $script:ProcList[$i]
        try {
            $p.Refresh()
            if ($p.HasExited) { Show-Msg 'Esse programa já foi fechado. Atualize a lista.'; return }
            $p.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::High
            Write-Log ('Turbo: {0} (PID {1}) agora em prioridade ALTA.' -f $p.ProcessName, $p.Id)
        } catch {
            Write-Log ('Não foi possível mudar a prioridade de {0}: {1}' -f $p.ProcessName, $_.Exception.Message)
        }
        [void](Invoke-PurgeStandby)
        Show-Msg ('Turbo aplicado em {0}. Bom jogo!' -f $p.ProcessName)
    })

    $ui['btnPermAdd'].Add_Click({
        $dlg = New-Object Microsoft.Win32.OpenFileDialog
        $dlg.Filter = 'Programas (*.exe)|*.exe'
        $dlg.Title  = 'Escolha o executável do jogo'
        if ($dlg.ShowDialog($script:Window) -ne $true) { return }
        $exe = [System.IO.Path]::GetFileName($dlg.FileName)
        if ($script:BlockedExe -contains $exe.ToLower()) { Show-Msg ('{0} é um programa do sistema e não deve ter a prioridade alterada.' -f $exe) 'Warning'; return }
        try {
            Set-PermanentPriority $exe
            Write-Log ('Prioridade alta permanente ativada para {0}.' -f $exe)
            Update-PermCombo
            Show-Msg ('{0} abrirá sempre em prioridade alta a partir de agora.' -f $exe)
        } catch { Write-Log ('FALHOU: {0}' -f $_.Exception.Message) }
    })

    $ui['btnPermRemove'].Add_Click({
        $exe = $script:UI['cmbPerm'].SelectedItem
        if (-not $exe) { Show-Msg 'Nenhum jogo com prioridade permanente.'; return }
        try {
            Remove-PermanentPriority ([string]$exe)
            Write-Log ('Prioridade permanente removida: {0}' -f $exe)
        } catch { Write-Log ('FALHOU: {0}' -f $_.Exception.Message) }
        Update-PermCombo
    })

    foreach ($child in $ui['pnlLinks'].Children) {
        if ($child -is [System.Windows.Controls.Button]) {
            $child.Add_Click({ param($s, $e) Open-Url ([string]$s.Tag) })
        }
    }

    $ui['btnRstrui'].Add_Click({ try { Start-Process rstrui.exe } catch { Write-Log 'Não foi possível abrir a Restauração do Sistema.' } })
    $ui['btnOpenFolder'].Add_Click({ try { Start-Process explorer.exe -ArgumentList ('"{0}"' -f $script:Root) } catch {} })
}

# ============================================================================
#  INÍCIO
# ============================================================================
try {
    [xml]$xamlXml = $xaml
    $reader = New-Object System.Xml.XmlNodeReader($xamlXml)
    $script:Window = [Windows.Markup.XamlReader]::Load($reader)

    $names = @('txtChip', 'tabs', 'btnAnalyze', 'btnSaveReport', 'txtDiag', 'txtRec',
               'btnApply', 'btnSelectRec', 'btnUndoSel', 'btnRestorePoint', 'btnRefresh', 'pnlTweaks',
               'btnCleanTemp', 'btnPurgeRam', 'btnStartup', 'btnGfxSettings', 'btnIntelPanel',
               'txtLabel', 'btnBench', 'btnHistory', 'txtGame', 'txtFpsAvg', 'txtFpsLow', 'btnAddFps', 'txtBench',
               'cmbProc', 'btnProcRefresh', 'btnTurbo', 'btnPermAdd', 'cmbPerm', 'btnPermRemove', 'pnlLinks', 'txtGuide',
               'btnUndoAll', 'btnRstrui', 'btnOpenFolder', 'txtLog', 'txtStatus')
    foreach ($n in $names) {
        $el = $script:Window.FindName($n)
        if (-not $el) { throw "Elemento de interface não encontrado: $n" }
        $script:UI[$n] = $el
    }

    Import-OtzBackup
    Initialize-TweakPanel
    $script:UI['txtGuide'].Text = $script:GuideText
    Register-Events

    $script:Window.Add_ContentRendered({
        Write-Log ('Projeto de Otimização iniciado. Windows build {0}. Pasta: {1}' -f $osBuild, $script:Root)
        if ($Relaunched) { try { [void][OtzNative]::ShowWindow([OtzNative]::GetConsoleWindow(), 0) } catch {} }
        Update-TweakStatus
        Update-PermCombo
        Update-ProcList
        Show-BenchHistory
        Start-Diagnostics
    })

    [void]$script:Window.ShowDialog()
} catch {
    $msg = $_.Exception.Message
    try { [System.IO.File]::AppendAllText($script:LogFile, ('[ERRO FATAL] ' + $msg + [Environment]::NewLine + $_.ScriptStackTrace + [Environment]::NewLine), $script:Utf8NoBom) } catch {}
    [void][System.Windows.MessageBox]::Show("Ocorreu um erro ao abrir o aplicativo:`n`n$msg`n`nDetalhes em Dados\Logs.", 'Projeto de Otimização', 'OK', 'Error')
}
