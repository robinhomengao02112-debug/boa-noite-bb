using System.Text.Json;
using BudsControlPC.Audio;
using Microsoft.Win32;

namespace BudsControlPC;

public sealed class AppSettings
{
    public MicSettings Mic { get; set; } = new();
    public Dictionary<string, MicSettings> UserPresets { get; set; } = new();
    public string? BudsDeviceId { get; set; }
    public bool BudsAutoConnect { get; set; } = true;
    public bool MinimizeToTray { get; set; } = true;

    private static string Dir => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "BudsControlPC");
    private static string FilePath => Path.Combine(Dir, "settings.json");
    private static readonly JsonSerializerOptions Opts = new() { WriteIndented = true };

    public static AppSettings Load()
    {
        try
        {
            if (File.Exists(FilePath))
                return JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(FilePath)) ?? new AppSettings();
        }
        catch { }
        return new AppSettings();
    }

    public void Save()
    {
        try
        {
            Directory.CreateDirectory(Dir);
            File.WriteAllText(FilePath, JsonSerializer.Serialize(this, Opts));
        }
        catch { }
    }

    private const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";

    public static bool StartWithWindows
    {
        get
        {
            using var k = Registry.CurrentUser.OpenSubKey(RunKey);
            return k?.GetValue("BudsControlPC") != null;
        }
        set
        {
            using var k = Registry.CurrentUser.CreateSubKey(RunKey);
            if (value) k.SetValue("BudsControlPC", $"\"{Environment.ProcessPath}\" --minimized");
            else k.DeleteValue("BudsControlPC", false);
        }
    }
}

public static class Presets
{
    /// <summary>Presets prontos. Só mexem nos efeitos, nunca nos dispositivos escolhidos.</summary>
    public static readonly Dictionary<string, Action<MicSettings>> BuiltIn = new()
    {
        ["Natural (padrão)"] = s =>
        {
            Reset(s);
        },
        ["Voz de rádio"] = s =>
        {
            Reset(s);
            s.HighPassHz = 90; s.GateOn = true; s.GateThresholdDb = -48;
            s.EqBands = new float[] { 0, 2, 4, 1, -2, 0, 2, 4, 3, 1 };
            s.CompressorOn = true; s.CompThresholdDb = -24; s.CompRatio = 4; s.GainDb = 6;
        },
        ["Streaming / Discord"] = s =>
        {
            Reset(s);
            s.HighPassHz = 100; s.NoiseReductionOn = true; s.NoiseReductionAmount = 55;
            s.GateOn = true; s.GateThresholdDb = -50;
            s.EqBands = new float[] { -2, 0, 1, 0, -2, 0, 2, 3, 2, 0 };
            s.CompressorOn = true; s.CompThresholdDb = -22; s.CompRatio = 3; s.GainDb = 4;
        },
        ["Reunião (clareza)"] = s =>
        {
            Reset(s);
            s.HighPassHz = 120; s.NoiseReductionOn = true; s.NoiseReductionAmount = 70;
            s.EqBands = new float[] { -4, -3, -1, 0, 0, 2, 4, 3, 0, -2 };
            s.CompressorOn = true; s.CompThresholdDb = -26; s.CompRatio = 3; s.GainDb = 5;
        },
        ["Voz baixa (bem mais alto)"] = s =>
        {
            Reset(s);
            s.HighPassHz = 90; s.NoiseReductionOn = true; s.NoiseReductionAmount = 50;
            s.CompressorOn = true; s.CompThresholdDb = -32; s.CompRatio = 5; s.GainDb = 14;
            s.EqBands = new float[] { 0, 0, 1, 0, 0, 1, 3, 2, 1, 0 };
        },
        ["Ambiente barulhento"] = s =>
        {
            Reset(s);
            s.HighPassHz = 150; s.NoiseReductionOn = true; s.NoiseReductionAmount = 90;
            s.GateOn = true; s.GateThresholdDb = -40;
            s.EqBands = new float[] { -6, -4, -2, 0, 0, 2, 3, 2, -1, -3 };
            s.CompressorOn = true; s.CompThresholdDb = -24; s.CompRatio = 3; s.GainDb = 4;
        },
        ["Gravação limpa"] = s =>
        {
            Reset(s);
            s.HighPassHz = 70; s.NoiseReductionOn = true; s.NoiseReductionAmount = 35;
            s.EqBands = new float[] { 0, 1, 1, -1, -1, 0, 1, 2, 2, 1 };
            s.CompressorOn = true; s.CompThresholdDb = -18; s.CompRatio = 2; s.GainDb = 2;
        },
    };

    private static void Reset(MicSettings s)
    {
        s.GainDb = 0; s.HighPassOn = true; s.HighPassHz = 80;
        s.NoiseReductionOn = false; s.NoiseReductionAmount = 60;
        s.GateOn = false; s.GateThresholdDb = -50;
        s.EqOn = true; s.EqBands = new float[10];
        s.CompressorOn = false; s.CompThresholdDb = -20; s.CompRatio = 3;
        s.LimiterOn = true; s.LimiterCeilingDb = -1;
    }
}
