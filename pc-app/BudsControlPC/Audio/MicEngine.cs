using System.Runtime.InteropServices;
using NAudio.CoreAudioApi;
using NAudio.Wave;

namespace BudsControlPC.Audio;

public sealed record AudioDevice(string Id, string Name)
{
    public override string ToString() => Name;
}

/// <summary>
/// Captura o microfone escolhido (WASAPI), aplica a cadeia de efeitos e envia o áudio tratado
/// para uma saída (ex.: "CABLE Input" do VB-CABLE, que os outros apps enxergam como microfone)
/// e, opcionalmente, para um monitor (ouvir a própria voz).
/// </summary>
public sealed class MicEngine : IDisposable
{
    private readonly MMDeviceEnumerator _enum = new();
    private WasapiCapture? _capture;
    private WasapiOut? _out, _mon;
    private BufferedWaveProvider? _outBuf, _monBuf;
    private DspChain? _dsp;
    private float[] _mono = new float[0];
    private MicSettings _settings = new();
    private readonly object _lock = new();

    public bool Running { get; private set; }
    public event Action<float, float>? Levels;   // pico de entrada e saída em dBFS
    public event Action<string>? Logged;
    public event Action? Stopped;

    public List<AudioDevice> Inputs() => List(DataFlow.Capture);
    public List<AudioDevice> Outputs() => List(DataFlow.Render);

    private List<AudioDevice> List(DataFlow flow)
    {
        var list = new List<AudioDevice>();
        foreach (var d in _enum.EnumerateAudioEndPoints(flow, DeviceState.Active))
        {
            try { list.Add(new AudioDevice(d.ID, d.FriendlyName)); } catch { }
        }
        return list;
    }

    public string? DefaultId(DataFlow flow)
    {
        try { return _enum.GetDefaultAudioEndpoint(flow, Role.Communications).ID; } catch { return null; }
    }

    public MMDevice? Find(string? id)
    {
        if (string.IsNullOrEmpty(id)) return null;
        try { return _enum.GetDevice(id); } catch { return null; }
    }

    public void Apply(MicSettings s)
    {
        lock (_lock)
        {
            _settings = s.Clone();
            _dsp?.Configure(_settings);
            if (_mon != null) _mon.Volume = Math.Clamp(_settings.MonitorVolume / 100f, 0, 1);
        }
    }

    public void Start(MicSettings s)
    {
        Stop();
        _settings = s.Clone();
        var inDev = Find(s.InputDeviceId) ?? throw new InvalidOperationException("Escolha um microfone.");
        var outDev = Find(s.OutputDeviceId);

        var cap = new WasapiCapture(inDev, true, 10);
        var fmt = cap.WaveFormat;
        Log($"Microfone: {inDev.FriendlyName} · {fmt.SampleRate} Hz · {fmt.Channels} canal(is) · {fmt.Encoding} {fmt.BitsPerSample} bits");
        _dsp = new DspChain(fmt.SampleRate, _settings);

        var outFmt = WaveFormat.CreateIeeeFloatWaveFormat(fmt.SampleRate, 2);
        if (outDev != null)
        {
            _outBuf = new BufferedWaveProvider(outFmt) { DiscardOnBufferOverflow = true, BufferDuration = TimeSpan.FromMilliseconds(500) };
            _out = new WasapiOut(outDev, AudioClientShareMode.Shared, true, 30);
            _out.Init(_outBuf);
            _out.Play();
            Log($"Saída: {outDev.FriendlyName}");
        }
        else Log("Sem saída escolhida: só medindo e monitorando.");

        if (s.MonitorOn) StartMonitor(outFmt);

        cap.DataAvailable += OnData;
        cap.RecordingStopped += (_, e) =>
        {
            if (e.Exception != null) Log($"Captura parou: {e.Exception.Message}");
            if (Running) { Running = false; Stopped?.Invoke(); }
        };
        _capture = cap;
        cap.StartRecording();
        Running = true;
    }

    private void StartMonitor(WaveFormat outFmt)
    {
        var dev = Find(_settings.MonitorDeviceId);
        if (dev == null) { try { dev = _enum.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia); } catch { } }
        if (dev == null) return;
        _monBuf = new BufferedWaveProvider(outFmt) { DiscardOnBufferOverflow = true, BufferDuration = TimeSpan.FromMilliseconds(500) };
        _mon = new WasapiOut(dev, AudioClientShareMode.Shared, true, 30);
        _mon.Init(_monBuf);
        _mon.Volume = Math.Clamp(_settings.MonitorVolume / 100f, 0, 1);
        _mon.Play();
        Log($"Monitor: {dev.FriendlyName}");
    }

    public void SetMonitor(bool on)
    {
        lock (_lock)
        {
            _settings.MonitorOn = on;
            if (!Running || _capture == null) return;
            if (on && _mon == null) StartMonitor(WaveFormat.CreateIeeeFloatWaveFormat(_capture.WaveFormat.SampleRate, 2));
            else if (!on) StopMonitor();
        }
    }

    private void StopMonitor()
    {
        try { _mon?.Stop(); } catch { }
        _mon?.Dispose();
        _mon = null;
        _monBuf = null;
    }

    private void OnData(object? sender, WaveInEventArgs e)
    {
        var cap = _capture;
        if (cap == null || e.BytesRecorded == 0) return;
        var fmt = cap.WaveFormat;
        int ch = fmt.Channels;
        int bytesPerSample = fmt.BitsPerSample / 8;
        int frames = e.BytesRecorded / (bytesPerSample * ch);
        if (_mono.Length < frames) _mono = new float[frames];

        bool isFloat = fmt.Encoding == WaveFormatEncoding.IeeeFloat ||
                       (fmt is WaveFormatExtensible ext && ext.SubFormat == NAudio.Dmo.AudioMediaSubtypes.MEDIASUBTYPE_IEEE_FLOAT);

        float inPeak = 0;
        var src = e.Buffer;
        for (int f = 0; f < frames; f++)
        {
            float sum = 0;
            for (int c = 0; c < ch; c++)
            {
                int o = (f * ch + c) * bytesPerSample;
                float v = isFloat && bytesPerSample == 4 ? BitConverter.ToSingle(src, o)
                    : bytesPerSample == 2 ? BitConverter.ToInt16(src, o) / 32768f
                    : bytesPerSample == 3 ? ((src[o] << 8 | src[o + 1] << 16 | src[o + 2] << 24) >> 8) / 8388608f
                    : bytesPerSample == 4 ? BitConverter.ToInt32(src, o) / 2147483648f
                    : 0;
                sum += v;
            }
            float m = sum / ch;
            _mono[f] = m;
            float a = Math.Abs(m); if (a > inPeak) inPeak = a;
        }

        lock (_lock) { _dsp?.Process(_mono, frames); }

        float outPeak = 0;
        var bytes = new byte[frames * 8];
        var span = MemoryMarshal.Cast<byte, float>(bytes.AsSpan());
        for (int f = 0; f < frames; f++)
        {
            float v = _mono[f];
            span[f * 2] = v; span[f * 2 + 1] = v;
            float a = Math.Abs(v); if (a > outPeak) outPeak = a;
        }

        Push(_outBuf, bytes);
        Push(_monBuf, bytes);
        Levels?.Invoke(DspChain.LinToDb(inPeak), DspChain.LinToDb(outPeak));
    }

    private static void Push(BufferedWaveProvider? b, byte[] bytes)
    {
        if (b == null) return;
        // evita atraso crescente: se o buffer acumular demais, descarta
        if (b.BufferedDuration > TimeSpan.FromMilliseconds(160)) b.ClearBuffer();
        b.AddSamples(bytes, 0, bytes.Length);
    }

    public void Stop()
    {
        Running = false;
        var cap = _capture;
        _capture = null;
        if (cap != null)
        {
            cap.DataAvailable -= OnData;
            try { cap.StopRecording(); } catch { }
            cap.Dispose();
        }
        try { _out?.Stop(); } catch { }
        _out?.Dispose();
        _out = null;
        _outBuf = null;
        StopMonitor();
        _dsp = null;
    }

    // ---------------------------------------------------------- nível do Windows

    public float? GetWindowsLevel(string? id)
    {
        try { return Find(id)?.AudioEndpointVolume.MasterVolumeLevelScalar * 100f; } catch { return null; }
    }

    public void SetWindowsLevel(string? id, float percent)
    {
        try { var d = Find(id); if (d != null) d.AudioEndpointVolume.MasterVolumeLevelScalar = Math.Clamp(percent / 100f, 0, 1); }
        catch (Exception e) { Log($"Não consegui mudar o nível: {e.Message}"); }
    }

    public bool GetWindowsMute(string? id)
    {
        try { return Find(id)?.AudioEndpointVolume.Mute ?? false; } catch { return false; }
    }

    public void SetWindowsMute(string? id, bool mute)
    {
        try { var d = Find(id); if (d != null) d.AudioEndpointVolume.Mute = mute; } catch { }
    }

    /// <summary>Procura o lado "microfone" do VB-CABLE (CABLE Output).</summary>
    public AudioDevice? FindCableRecording() =>
        Inputs().FirstOrDefault(d => d.Name.Contains("CABLE Output", StringComparison.OrdinalIgnoreCase)
                                  || d.Name.Contains("VB-Audio", StringComparison.OrdinalIgnoreCase));

    public AudioDevice? FindCablePlayback() =>
        Outputs().FirstOrDefault(d => d.Name.Contains("CABLE Input", StringComparison.OrdinalIgnoreCase)
                                   || d.Name.Contains("VB-Audio", StringComparison.OrdinalIgnoreCase));

    private void Log(string s) => Logged?.Invoke($"{DateTime.Now:HH:mm:ss}  [mic] {s}");

    public void Dispose()
    {
        Stop();
        _enum.Dispose();
    }
}

/// <summary>Define o dispositivo padrão do Windows (interface IPolicyConfig, a mesma usada pelo painel de Som).</summary>
public static class DefaultDevice
{
    [ComImport, Guid("870af99c-171d-4f9e-af0d-e63df40c2bc9")]
    private class PolicyConfigClient { }

    [ComImport, Guid("f8679f50-850a-41cf-9c72-430f290290c8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IPolicyConfig
    {
        [PreserveSig] int GetMixFormat(string id, IntPtr fmt);
        [PreserveSig] int GetDeviceFormat(string id, int def, IntPtr fmt);
        [PreserveSig] int ResetDeviceFormat(string id);
        [PreserveSig] int SetDeviceFormat(string id, IntPtr ep, IntPtr mix);
        [PreserveSig] int GetProcessingPeriod(string id, int def, IntPtr pDef, IntPtr pMin);
        [PreserveSig] int SetProcessingPeriod(string id, IntPtr period);
        [PreserveSig] int GetShareMode(string id, IntPtr mode);
        [PreserveSig] int SetShareMode(string id, IntPtr mode);
        [PreserveSig] int GetPropertyValue(string id, IntPtr key, IntPtr value);
        [PreserveSig] int SetPropertyValue(string id, IntPtr key, IntPtr value);
        [PreserveSig] int SetDefaultEndpoint([MarshalAs(UnmanagedType.LPWStr)] string id, int role);
        [PreserveSig] int SetEndpointVisibility(string id, int visible);
    }

    /// <summary>Define como padrão para console, multimídia e comunicações.</summary>
    public static bool Set(string deviceId)
    {
        try
        {
            var pc = (IPolicyConfig)new PolicyConfigClient();
            for (int role = 0; role < 3; role++) pc.SetDefaultEndpoint(deviceId, role);
            return true;
        }
        catch { return false; }
    }
}
