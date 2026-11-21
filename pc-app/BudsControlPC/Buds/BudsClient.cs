using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.Rfcomm;
using Windows.Devices.Enumeration;
using Windows.Networking.Sockets;

namespace BudsControlPC.Buds;

public enum Link { Disconnected, Connecting, Authenticating, Connected, Error }

public sealed record Battery(int Level, bool Charging);

public sealed class BudsState
{
    public Link Link = Link.Disconnected;
    public string Status = "Desconectado";
    public string? DeviceName;
    public string? DeviceId;
    public string? Firmware;
    public Battery? Left, Right, Case;
    public int? AmbientMode;           // 0 desligado, 1 ANC, 2 transparência
    public int? AncStrength;           // 0 equilibrado, 1 leve, 2 profundo
    public int? TransparencyStrength;  // 0 normal, 1 voz, 2 ambiente
    public bool? AdaptiveAnc, AdaptiveSound, EarDetection, DualConnection, AutoAnswer;
    public int? EqPreset;
    public int[] EqBands = new int[10];
    public int?[] Gestures = new int?[8]; // single L/R, double L/R, triple L/R, long L/R
    public (int L, int R)? LongCycle;
    public string? InEar;
}

public sealed record PairedDevice(string Id, string Name)
{
    public override string ToString() => Name;
}

public sealed class BudsClient : IDisposable
{
    public static readonly Guid CtrlUuid = Guid.Parse("0000fd2d-0000-1000-8000-00805f9b34fb");

    public const int AncBalanced = 0, AncLight = 1, AncDeep = 2, EqCustom = 10;

    public BudsState State { get; } = new();
    public event Action? Changed;
    public event Action<string>? Logged;

    private StreamSocket? _socket;
    private Stream? _out;
    private CancellationTokenSource? _cts;
    private Commands _cmd = new();
    private readonly Lazy<SaferAuth> _auth = new(() => new SaferAuth());
    private readonly object _writeLock = new();

    public bool IsConnected => State.Link == Link.Connected;

    private void Notify() => Changed?.Invoke();
    public void Log(string s) => Logged?.Invoke($"{DateTime.Now:HH:mm:ss}  {s}");

    public static async Task<List<PairedDevice>> ListPairedAsync()
    {
        var sel = BluetoothDevice.GetDeviceSelectorFromPairingState(true);
        var infos = await DeviceInformation.FindAllAsync(sel);
        return infos.Select(i => new PairedDevice(i.Id, string.IsNullOrWhiteSpace(i.Name) ? i.Id : i.Name))
            .OrderByDescending(d => d.Name.Contains("buds", StringComparison.OrdinalIgnoreCase) || d.Name.Contains("redmi", StringComparison.OrdinalIgnoreCase))
            .ThenBy(d => d.Name).ToList();
    }

    public async Task ConnectAsync(string deviceId)
    {
        if (State.Link is Link.Connecting or Link.Authenticating) return;
        Disconnect(silent: true);
        State.Link = Link.Connecting;
        State.DeviceId = deviceId;
        State.Status = "Conectando…";
        Notify();

        for (int attempt = 1; attempt <= 3; attempt++)
        {
            try
            {
                await OpenAsync(deviceId);
                return;
            }
            catch (Exception e)
            {
                Log($"Tentativa {attempt} falhou: {e.Message}");
                CloseSocket();
                await Task.Delay(1500 * attempt);
            }
        }
        State.Link = Link.Error;
        State.Status = "Não foi possível abrir o canal de controle. Confira se o fone está conectado a este PC e feche o app Xiaomi Earbuds no celular.";
        Notify();
    }

    private async Task OpenAsync(string deviceId)
    {
        var dev = await BluetoothDevice.FromIdAsync(deviceId) ?? throw new InvalidOperationException("Aparelho não encontrado");
        State.DeviceName = dev.Name;
        Notify();
        var sid = RfcommServiceId.FromUuid(CtrlUuid);
        var res = await dev.GetRfcommServicesForIdAsync(sid, BluetoothCacheMode.Uncached);
        if (res.Services.Count == 0) res = await dev.GetRfcommServicesForIdAsync(sid, BluetoothCacheMode.Cached);
        if (res.Services.Count == 0)
            throw new InvalidOperationException($"O fone não oferece o serviço de controle Xiaomi (erro {res.Error})");
        var svc = res.Services[0];
        Log($"Abrindo RFCOMM {svc.ConnectionServiceName} em {dev.Name}");
        var sock = new StreamSocket();
        await sock.ConnectAsync(svc.ConnectionHostName, svc.ConnectionServiceName,
            SocketProtectionLevel.BluetoothEncryptionAllowNullAuthentication);
        _socket = sock;
        _out = sock.OutputStream.AsStreamForWrite(0);
        _cmd = new Commands();
        State.Link = Link.Authenticating;
        State.Status = "Autenticando…";
        Notify();
        _cts = new CancellationTokenSource();
        _ = Task.Run(() => ReadLoop(sock, _cts.Token));
        Send(_cmd.StartAuth(), "AUTH início");
    }

    private async Task ReadLoop(StreamSocket sock, CancellationToken ct)
    {
        var input = sock.InputStream.AsStreamForRead(0);
        var buf = new byte[4096];
        var asm = new FrameAssembler();
        try
        {
            while (!ct.IsCancellationRequested)
            {
                int n = await input.ReadAsync(buf.AsMemory(0, buf.Length), ct);
                if (n <= 0) break;
                foreach (var m in asm.Feed(buf, n))
                {
                    Log($"⇐ {m}");
                    try { Handle(m); } catch (Exception e) { Log($"Erro ao tratar mensagem: {e.Message}"); }
                }
            }
        }
        catch (Exception e) when (!ct.IsCancellationRequested)
        {
            Log($"Leitura encerrada: {e.Message}");
        }
        catch { }
        if (ReferenceEquals(_socket, sock))
        {
            CloseSocket();
            State.Link = Link.Disconnected;
            State.Status = "Conexão perdida";
            Notify();
        }
    }

    public void Disconnect(bool silent = false)
    {
        CloseSocket();
        if (!silent)
        {
            State.Link = Link.Disconnected;
            State.Status = "Desconectado";
            Notify();
        }
    }

    private void CloseSocket()
    {
        var s = _socket;
        _socket = null;
        _out = null;
        try { _cts?.Cancel(); } catch { }
        try { s?.Dispose(); } catch { }
    }

    private void Send(byte[] bytes, string label = "")
    {
        var o = _out;
        if (o == null) { Log($"Sem conexão; comando ignorado ({label})"); return; }
        try
        {
            lock (_writeLock)
            {
                o.Write(bytes, 0, bytes.Length);
                o.Flush();
            }
            Log($"⇒ {(label.Length > 0 ? label + ": " : "")}{Hex.Of(bytes)}");
        }
        catch (Exception e)
        {
            Log($"Falha ao enviar: {e.Message}");
            CloseSocket();
            State.Link = Link.Disconnected;
            State.Status = "Conexão perdida";
            Notify();
        }
    }

    // ------------------------------------------------------------------ recebimento

    private void Handle(Message m)
    {
        switch (m.Opcode)
        {
            case Op.AuthChallenge:
                if (m.Type == MsgType.Response)
                    Send(_cmd.AuthConfirm(), "AUTH confirma");
                else
                {
                    var challenge = m.Payload[1..17];
                    var resp = _auth.Value.Respond(challenge);
                    var p = new byte[17];
                    p[0] = 1;
                    Array.Copy(resp, 0, p, 1, 16);
                    Send(new Message(MsgType.Response, Op.AuthChallenge, m.Seq, p).Encode(), "AUTH resposta");
                }
                break;
            case Op.AuthConfirm:
                if (m.Type != MsgType.Response)
                {
                    Send(new Message(MsgType.Response, Op.AuthConfirm, m.Seq, new byte[] { 1 }).Encode(), "AUTH final");
                    State.Link = Link.Connected;
                    State.Status = "Conectado";
                    Notify();
                    RefreshAll();
                }
                break;
            case Op.GetDeviceInfo: DecodeDeviceInfo(m.Payload); break;
            case Op.GetDeviceRunInfo: DecodeRunInfo(m.Payload); break;
            case Op.ReportStatus:
                DecodeReport(m.Payload);
                if (m.Type != MsgType.Response)
                    Send(new Message(MsgType.Response, Op.ReportStatus, m.Seq, Array.Empty<byte>()).Encode(), "ack status");
                break;
            case Op.GetConfig:
                if (m.Type == MsgType.Response) DecodeConfig(m.Payload);
                break;
            case Op.NotifyConfig:
                DecodeNotify(m.Payload);
                if (m.Type != MsgType.Response)
                    Send(new Message(MsgType.Response, Op.NotifyConfig, m.Seq, Array.Empty<byte>()).Encode(), "ack notify");
                break;
        }
        Notify();
    }

    private static Battery? Bat(int b) => b == 0xFF ? null : new Battery(b & 0x7F, (b & 0x80) != 0);
    private static int? At(byte[] p, int i) => i >= 0 && i < p.Length ? p[i] : null;

    private static void ForEachTlv(byte[] p, Action<int, int> block)
    {
        int i = 0;
        while (i + 1 < p.Length)
        {
            int len = p[i];
            if (len == 0) break;
            block(p[i + 1], i);
            i += len + 1;
        }
    }

    private void DecodeDeviceInfo(byte[] p) => ForEachTlv(p, (idx, i) =>
    {
        if (idx == 0x01 && i + 5 < p.Length)
            State.Firmware = $"{p[i + 2] >> 4}.{p[i + 2] & 0xF}.{p[i + 3] >> 4}.{p[i + 3] & 0xF}";
        else if (idx == 0x07 && i + 4 < p.Length)
        {
            State.Left = Bat(p[i + 2]); State.Right = Bat(p[i + 3]); State.Case = Bat(p[i + 4]);
        }
    });

    private void DecodeRunInfo(byte[] p) => ForEachTlv(p, (idx, i) =>
    {
        if (idx == 0x09 && At(p, i + 2) is int mode) State.AmbientMode = mode;
        else if (idx == 0x0A && At(p, i + 2) is int ed) State.EarDetection = ed == 0;
    });

    private void DecodeReport(byte[] p) => ForEachTlv(p, (idx, i) =>
    {
        if (idx == 0x00 && i + 4 < p.Length)
        {
            State.Left = Bat(p[i + 2]); State.Right = Bat(p[i + 3]); State.Case = Bat(p[i + 4]);
        }
        else if (idx == 0x04 && At(p, i + 2) is int mode) State.AmbientMode = mode;
    });

    private void DecodeNotify(byte[] p)
    {
        int i = 0;
        while (i + 2 < p.Length)
        {
            int len = p[i];
            if (len == 0) break;
            switch (p[i + 2])
            {
                case 0x0C when At(p, i + 3) is int v:
                    bool wl = (v & 8) != 0, wr = (v & 4) != 0, cl = (v & 2) != 0, cr = (v & 1) != 0;
                    State.InEar = $"E: {(wl ? "no ouvido" : cl ? "no estojo" : "fora")} · D: {(wr ? "no ouvido" : cr ? "no estojo" : "fora")}";
                    break;
                case 0x0B when At(p, i + 3) is int mode && At(p, i + 4) is int lvl:
                    if (mode == StrengthTarget.Anc) State.AncStrength = lvl; else State.TransparencyStrength = lvl;
                    break;
            }
            i += len + 1;
        }
    }

    private void DecodeConfig(byte[] p)
    {
        if (At(p, 2) is not int id) return;
        switch (id)
        {
            case Cfg.Gestures:
                int[] idx = { 4, 5, 7, 8, 10, 11, 13, 14 };
                for (int k = 0; k < 8; k++) State.Gestures[k] = At(p, idx[k]);
                break;
            case Cfg.LongGestures when At(p, 3) is int l && At(p, 4) is int r: State.LongCycle = (l, r); break;
            case Cfg.AutoAnswer when At(p, 3) is int v: State.AutoAnswer = v == 1; break;
            case Cfg.DoubleConnection when At(p, 3) is int v: State.DualConnection = v == 1; break;
            case Cfg.EqPreset when At(p, 3) is int v: State.EqPreset = v; break;
            case Cfg.AdaptiveAnc when At(p, 3) is int v: State.AdaptiveAnc = v == 1; break;
            case Cfg.AdaptiveSound when At(p, 3) is int v: State.AdaptiveSound = v == 1; break;
            case Cfg.EffectStrength when At(p, 3) is int t && At(p, 4) is int lvl:
                if (t == StrengthTarget.Anc) State.AncStrength = lvl; else State.TransparencyStrength = lvl;
                break;
            case Cfg.EqCurve:
                for (int k = 0; k < 10; k++) State.EqBands[k] = At(p, 12 + 3 * k) is int b ? Commands.EqByteToDb(b) : 0;
                break;
            default:
                Log($"Config 0x{id:X2} não tratada: {Hex.Of(p)}");
                break;
        }
    }

    // ------------------------------------------------------------------ comandos

    public void RefreshAll()
    {
        Send(_cmd.GetDeviceInfo(), "info");
        Send(_cmd.GetRunInfo(), "estado");
        Send(_cmd.GetAllConfig(), "configs");
    }

    public void SetAmbientMode(int mode) { Send(_cmd.SetAmbientMode(mode), $"modo {mode}"); State.AmbientMode = mode; Notify(); }
    public void SetAncStrength(int lvl) { Send(_cmd.SetStrength(StrengthTarget.Anc, lvl), $"ANC {lvl}"); State.AncStrength = lvl; Notify(); }
    public void SetTransparencyStrength(int lvl) { Send(_cmd.SetStrength(StrengthTarget.Transparency, lvl), $"transparência {lvl}"); State.TransparencyStrength = lvl; Notify(); }
    public void SetAdaptiveAnc(bool on) { Send(_cmd.SetBool(Cfg.AdaptiveAnc, on), $"ANC adaptativo {on}"); State.AdaptiveAnc = on; Notify(); }
    public void SetAdaptiveSound(bool on) { Send(_cmd.SetBool(Cfg.AdaptiveSound, on), $"som adaptativo {on}"); State.AdaptiveSound = on; Notify(); }
    public void SetDualConnection(bool on) { Send(_cmd.SetBool(Cfg.DoubleConnection, on), $"conexão dupla {on}"); State.DualConnection = on; Notify(); }
    public void SetAutoAnswer(bool on) { Send(_cmd.SetBool(Cfg.AutoAnswer, on), $"atender auto {on}"); State.AutoAnswer = on; Notify(); }
    public void SetEarDetection(bool on) { Send(_cmd.SetEarDetection(on), $"detecção de uso {on}"); State.EarDetection = on; Notify(); }
    public void SetEqPreset(int p) { Send(_cmd.SetInt(Cfg.EqPreset, p), $"EQ preset {p}"); State.EqPreset = p; Notify(); }

    public void SetCustomEq(int[] bands)
    {
        Send(_cmd.SetInt(Cfg.EqPreset, EqCustom), "EQ personalizado");
        Send(_cmd.SetCustomEq(bands), "EQ curva");
        State.EqPreset = EqCustom;
        State.EqBands = (int[])bands.Clone();
        Notify();
    }

    public void SetGesture(int slot, int value)
    {
        int interaction = new[] { 0x04, 0x01, 0x02, 0x03 }[slot / 2];
        Send(_cmd.SetGesture(interaction, slot % 2 == 0, value), $"gesto {slot}={value}");
        State.Gestures[slot] = value;
        Notify();
    }

    public void SetLongCycle(bool left, int value)
    {
        Send(_cmd.SetLongGestureMode(left, value), $"toque longo {(left ? "E" : "D")}={value}");
        var cur = State.LongCycle ?? (7, 7);
        State.LongCycle = left ? (value, cur.R) : (cur.L, value);
        Notify();
    }

    // ------------------------------------------------------------------ conexão em um clique

    private BluetoothDevice? _watched;
    /// <summary>Disparado quando o Windows conecta (true) ou desconecta (false) o fone.</summary>
    public event Action<bool>? AclChanged;

    /// <summary>Passa a acompanhar o fone: quando ele ligar perto do PC, o app fica sabendo.</summary>
    public async Task WatchAsync(string deviceId)
    {
        try
        {
            if (_watched != null) { _watched.ConnectionStatusChanged -= OnAcl; _watched.Dispose(); }
            _watched = await BluetoothDevice.FromIdAsync(deviceId);
            if (_watched != null) _watched.ConnectionStatusChanged += OnAcl;
        }
        catch (Exception e) { Log($"Não consegui acompanhar o fone: {e.Message}"); }
    }

    private void OnAcl(BluetoothDevice d, object _)
    {
        bool on = d.ConnectionStatus == BluetoothConnectionStatus.Connected;
        Log(on ? "Windows conectou o fone" : "Windows desconectou o fone");
        AclChanged?.Invoke(on);
    }

    /// <summary>
    /// Um clique: liga o áudio Bluetooth do fone no Windows (se estiver desligado),
    /// espera ele conectar e abre o canal de controle.
    /// </summary>
    public async Task ConnectEverythingAsync(string deviceId)
    {
        if (State.Link is Link.Connecting or Link.Authenticating) return;
        State.Status = "Ligando o áudio Bluetooth…";
        State.Link = Link.Connecting;
        Notify();

        var mac = await BtAudio.MacOf(deviceId);
        var dev = await BluetoothDevice.FromIdAsync(deviceId);
        State.DeviceName = dev?.Name;

        if (!await BtAudio.IsConnectedAsync(deviceId) && mac != null)
        {
            int n = await Task.Run(() => BtAudio.Send(mac, dev?.Name, connect: true, log: Log));
            if (n == 0) Log("Nenhum endpoint de áudio respondeu; tentando abrir o controle direto (isso também acorda a conexão).");
            for (int i = 0; i < 20 && !await BtAudio.IsConnectedAsync(deviceId); i++) await Task.Delay(400);
        }
        State.Link = Link.Disconnected;
        await ConnectAsync(deviceId);
    }

    /// <summary>Solta o fone do PC (controle + áudio) para o celular poder pegar.</summary>
    public async Task ReleaseAsync(string deviceId)
    {
        Disconnect();
        var mac = await BtAudio.MacOf(deviceId);
        var dev = await BluetoothDevice.FromIdAsync(deviceId);
        if (mac != null)
        {
            int n = await Task.Run(() => BtAudio.Send(mac, dev?.Name, connect: false, log: Log));
            State.Status = n > 0 ? "Áudio desligado do PC: o celular já pode conectar" : "Controle desconectado (o Windows não liberou o áudio)";
            Notify();
        }
    }

    public void SendRaw(int op, byte[] payload) => Send(_cmd.Raw(op, payload), "manual");

    public void Turbo()
    {
        SetAmbientMode(1);
        SetAdaptiveAnc(false);
        SetAncStrength(AncDeep);
        Log("Turbo: ANC ligado, adaptativo desligado, intensidade profunda");
    }

    public void Dispose()
    {
        CloseSocket();
        if (_watched != null) { _watched.ConnectionStatusChanged -= OnAcl; _watched.Dispose(); }
    }
}
