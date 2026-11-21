namespace BudsControlPC.Audio;

/// <summary>Configurações do microfone (salvas em JSON).</summary>
public sealed class MicSettings
{
    public string? InputDeviceId { get; set; }
    public string? OutputDeviceId { get; set; }
    public string? MonitorDeviceId { get; set; }
    public bool AutoStart { get; set; }

    public float GainDb { get; set; } = 0;

    public bool HighPassOn { get; set; } = true;
    public float HighPassHz { get; set; } = 80;

    public bool NoiseReductionOn { get; set; }
    public float NoiseReductionAmount { get; set; } = 60;   // 0..100 %

    public bool GateOn { get; set; }
    public float GateThresholdDb { get; set; } = -50;

    public bool EqOn { get; set; } = true;
    public float[] EqBands { get; set; } = new float[10];

    public bool CompressorOn { get; set; }
    public float CompThresholdDb { get; set; } = -20;
    public float CompRatio { get; set; } = 3;

    public bool LimiterOn { get; set; } = true;
    public float LimiterCeilingDb { get; set; } = -1;

    public bool MonitorOn { get; set; }
    public float MonitorVolume { get; set; } = 80;  // %

    public MicSettings Clone()
    {
        var c = (MicSettings)MemberwiseClone();
        c.EqBands = (float[])EqBands.Clone();
        return c;
    }

    public static readonly float[] EqFreqs = { 31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000 };
    public static readonly string[] EqLabels = { "31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k" };
}

/// <summary>Filtro biquad (fórmulas do RBJ Audio EQ Cookbook).</summary>
public sealed class Biquad
{
    private double _b0, _b1, _b2, _a1, _a2;
    private double _z1, _z2;

    public static Biquad HighPass(double fs, double f, double q = 0.707)
    {
        var w = 2 * Math.PI * f / fs; var cos = Math.Cos(w); var alpha = Math.Sin(w) / (2 * q);
        var a0 = 1 + alpha;
        return new Biquad { _b0 = (1 + cos) / 2 / a0, _b1 = -(1 + cos) / a0, _b2 = (1 + cos) / 2 / a0, _a1 = -2 * cos / a0, _a2 = (1 - alpha) / a0 };
    }

    public static Biquad Peaking(double fs, double f, double gainDb, double q = 1.2)
    {
        f = Math.Min(f, fs * 0.45);
        var a = Math.Pow(10, gainDb / 40); var w = 2 * Math.PI * f / fs; var cos = Math.Cos(w); var alpha = Math.Sin(w) / (2 * q);
        var a0 = 1 + alpha / a;
        return new Biquad { _b0 = (1 + alpha * a) / a0, _b1 = -2 * cos / a0, _b2 = (1 - alpha * a) / a0, _a1 = -2 * cos / a0, _a2 = (1 - alpha / a) / a0 };
    }

    public static Biquad LowShelf(double fs, double f, double gainDb) => Shelf(fs, f, gainDb, low: true);
    public static Biquad HighShelf(double fs, double f, double gainDb) => Shelf(fs, Math.Min(f, fs * 0.45), gainDb, low: false);

    private static Biquad Shelf(double fs, double f, double gainDb, bool low)
    {
        var a = Math.Pow(10, gainDb / 40); var w = 2 * Math.PI * f / fs; var cos = Math.Cos(w);
        var alpha = Math.Sin(w) / 2 * Math.Sqrt(2); var sq = 2 * Math.Sqrt(a) * alpha;
        double b0, b1, b2, a0, a1, a2;
        if (low)
        {
            b0 = a * ((a + 1) - (a - 1) * cos + sq); b1 = 2 * a * ((a - 1) - (a + 1) * cos); b2 = a * ((a + 1) - (a - 1) * cos - sq);
            a0 = (a + 1) + (a - 1) * cos + sq; a1 = -2 * ((a - 1) + (a + 1) * cos); a2 = (a + 1) + (a - 1) * cos - sq;
        }
        else
        {
            b0 = a * ((a + 1) + (a - 1) * cos + sq); b1 = -2 * a * ((a - 1) + (a + 1) * cos); b2 = a * ((a + 1) + (a - 1) * cos - sq);
            a0 = (a + 1) - (a - 1) * cos + sq; a1 = 2 * ((a - 1) - (a + 1) * cos); a2 = (a + 1) - (a - 1) * cos - sq;
        }
        return new Biquad { _b0 = b0 / a0, _b1 = b1 / a0, _b2 = b2 / a0, _a1 = a1 / a0, _a2 = a2 / a0 };
    }

    public float Process(float x)
    {
        // Forma direta transposta II
        double y = _b0 * x + _z1;
        _z1 = _b1 * x - _a1 * y + _z2;
        _z2 = _b2 * x - _a2 * y;
        return (float)y;
    }
}

/// <summary>
/// Cadeia de efeitos do microfone (mono):
/// passa-altas → redução de ruído → noise gate → EQ → compressor → ganho → limitador.
/// </summary>
public sealed class DspChain
{
    private readonly int _fs;
    private MicSettings _s;
    private Biquad? _hpf;
    private Biquad[] _eq = Array.Empty<Biquad>();
    private readonly NoiseReducer _nr;

    // envelopes
    private float _gateEnv, _gateGain = 1, _gateHold;
    private float _compEnvDb = -120;
    private float _limGain = 1;

    private float _gainLin = 1, _ceilLin = 0.89f;

    public DspChain(int sampleRate, MicSettings s)
    {
        _fs = sampleRate;
        _nr = new NoiseReducer(sampleRate);
        _s = s;
        Configure(s);
    }

    public void Configure(MicSettings s)
    {
        s = s.Clone();
        _hpf = s.HighPassOn ? Biquad.HighPass(_fs, s.HighPassHz) : null;
        var eq = new List<Biquad>();
        if (s.EqOn)
            for (int i = 0; i < 10; i++)
            {
                var g = s.EqBands[i];
                if (Math.Abs(g) < 0.05f) continue;
                if (i == 0) eq.Add(Biquad.LowShelf(_fs, MicSettings.EqFreqs[i] * 1.5, g));
                else if (i == 9) eq.Add(Biquad.HighShelf(_fs, MicSettings.EqFreqs[i] * 0.7, g));
                else eq.Add(Biquad.Peaking(_fs, MicSettings.EqFreqs[i], g, 1.1));
            }
        _eq = eq.ToArray();
        _nr.Amount = s.NoiseReductionAmount / 100f;
        _gainLin = DbToLin(s.GainDb);
        _ceilLin = DbToLin(s.LimiterCeilingDb);
        _s = s;
    }

    public static float DbToLin(float db) => MathF.Pow(10, db / 20);
    public static float LinToDb(float lin) => 20 * MathF.Log10(Math.Max(lin, 1e-7f));

    private float Coef(float ms) => MathF.Exp(-1f / (_fs * ms / 1000f));

    public void Process(float[] buf, int count)
    {
        var s = _s;
        var hpf = _hpf;
        var eq = _eq;

        if (hpf != null)
            for (int i = 0; i < count; i++) buf[i] = hpf.Process(buf[i]);

        if (s.NoiseReductionOn) _nr.Process(buf, count);

        float gAtk = Coef(1), gRel = Coef(120), gainAtk = Coef(2), gainRel = Coef(60);
        float gateThr = DbToLin(s.GateThresholdDb), holdSamples = _fs * 0.15f;
        float cAtk = Coef(5), cRel = Coef(150);
        float lRel = Coef(60);
        float slope = 1 - 1 / Math.Max(1f, s.CompRatio);

        for (int i = 0; i < count; i++)
        {
            float x = buf[i];

            if (s.GateOn)
            {
                float a = Math.Abs(x);
                _gateEnv = a > _gateEnv ? gAtk * _gateEnv + (1 - gAtk) * a : gRel * _gateEnv + (1 - gRel) * a;
                float target;
                if (_gateEnv >= gateThr) { target = 1; _gateHold = holdSamples; }
                else if (_gateHold > 0) { target = 1; _gateHold--; }
                else target = 0;
                float c = target > _gateGain ? gainAtk : gainRel;
                _gateGain = c * _gateGain + (1 - c) * target;
                x *= _gateGain;
            }

            for (int k = 0; k < eq.Length; k++) x = eq[k].Process(x);

            if (s.CompressorOn)
            {
                float lvl = LinToDb(Math.Abs(x));
                float c = lvl > _compEnvDb ? cAtk : cRel;
                _compEnvDb = c * _compEnvDb + (1 - c) * lvl;
                float over = _compEnvDb - s.CompThresholdDb;
                if (over > 0) x *= DbToLin(-over * slope);
            }

            x *= _gainLin;

            if (s.LimiterOn)
            {
                float a = Math.Abs(x) * _limGain;
                if (a > _ceilLin) _limGain = _ceilLin / Math.Max(Math.Abs(x), 1e-9f);
                else _limGain = lRel * _limGain + (1 - lRel) * 1f;
                if (_limGain > 1) _limGain = 1;
                x *= _limGain;
            }

            buf[i] = Math.Clamp(x, -0.999f, 0.999f);
        }
    }
}

/// <summary>
/// Redução de ruído por subtração espectral (STFT, janela sqrt-Hann, 50% de sobreposição).
/// Aprende o ruído de fundo continuamente (estatística de mínimo suavizada), então não precisa de "captura de perfil".
/// </summary>
public sealed class NoiseReducer
{
    private const int N = 1024;
    private const int Hop = N / 2;
    private const double NoiseBias = 2.2; // compensa o viés do rastreio de mínimo

    public float Amount { get; set; } = 0.6f;

    private readonly float[] _win = new float[N];
    private readonly float[] _frame = new float[N];
    private int _fill;
    private readonly float[] _ola = new float[N];
    private readonly double[] _re = new double[N], _im = new double[N];
    private readonly double[] _noise = new double[N / 2 + 1];
    private readonly double[] _gain = new double[N / 2 + 1];
    private readonly double[] _smooth = new double[N / 2 + 1];
    private readonly Queue<float> _out = new();
    private bool _init;

    public NoiseReducer(int sampleRate)
    {
        for (int i = 0; i < N; i++) _win[i] = (float)Math.Sqrt(0.5 * (1 - Math.Cos(2 * Math.PI * i / N)));
        for (int i = 0; i < Hop; i++) _out.Enqueue(0);
        Array.Fill(_gain, 1.0);
        _fill = N - Hop;
    }

    public void Process(float[] buf, int count)
    {
        for (int i = 0; i < count; i++)
        {
            _frame[_fill++] = buf[i];
            if (_fill == N)
            {
                ProcessFrame();
                Array.Copy(_frame, Hop, _frame, 0, N - Hop);
                _fill = N - Hop;
            }
            buf[i] = _out.Count > 0 ? _out.Dequeue() : 0;
        }
    }

    private void ProcessFrame()
    {
        for (int i = 0; i < N; i++) { _re[i] = _frame[i] * _win[i]; _im[i] = 0; }
        Fft(_re, _im, false);

        double amount = Math.Clamp(Amount, 0, 1);
        double over = 1.0 + 2.5 * amount;               // fator de sobre-subtração
        double floor = Math.Pow(10, -30 * amount / 20);  // até −30 dB de redução (0% = sem efeito)

        for (int k = 0; k <= N / 2; k++)
        {
            double p = _re[k] * _re[k] + _im[k] * _im[k] + 1e-12;
            // espectro suavizado no tempo + rastreio de mínimo (sobe devagar, desce na hora)
            _smooth[k] = _init ? 0.75 * _smooth[k] + 0.25 * p : p;
            if (!_init || _smooth[k] < _noise[k]) _noise[k] = _smooth[k];
            else _noise[k] *= 1.004;   // ~45%/s: acompanha ruído que aumenta

            double g = 1 - over * NoiseBias * _noise[k] / p;
            g = Math.Max(floor, Math.Sqrt(Math.Max(g, 0)));
            // suavização temporal para evitar "ruído musical"
            g = g > _gain[k] ? 0.5 * _gain[k] + 0.5 * g : 0.7 * _gain[k] + 0.3 * g;
            _gain[k] = g;

            _re[k] *= g; _im[k] *= g;
            if (k > 0 && k < N / 2) { _re[N - k] *= g; _im[N - k] *= g; }
        }
        _init = true;

        Fft(_re, _im, true);
        for (int i = 0; i < N; i++) _ola[i] += (float)(_re[i] * _win[i]);
        for (int i = 0; i < Hop; i++) _out.Enqueue(_ola[i]);
        Array.Copy(_ola, Hop, _ola, 0, N - Hop);
        Array.Clear(_ola, N - Hop, Hop);
    }

    /// <summary>FFT radix-2 in-place. Na inversa, já divide por N.</summary>
    public static void Fft(double[] re, double[] im, bool inverse)
    {
        int n = re.Length;
        for (int i = 1, j = 0; i < n; i++)
        {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { (re[i], re[j]) = (re[j], re[i]); (im[i], im[j]) = (im[j], im[i]); }
        }
        for (int len = 2; len <= n; len <<= 1)
        {
            double ang = 2 * Math.PI / len * (inverse ? 1 : -1);
            double wr = Math.Cos(ang), wi = Math.Sin(ang);
            for (int i = 0; i < n; i += len)
            {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++)
                {
                    int a = i + k, b = a + len / 2;
                    double tr = re[b] * cr - im[b] * ci, ti = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - tr; im[b] = im[a] - ti;
                    re[a] += tr; im[a] += ti;
                    double ncr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = ncr;
                }
            }
        }
        if (inverse) for (int i = 0; i < n; i++) { re[i] /= n; im[i] /= n; }
    }
}
