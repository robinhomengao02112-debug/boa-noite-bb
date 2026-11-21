using System.Diagnostics;
using BudsControlPC.Audio;

namespace BudsControlPC.UI;

public sealed class MicPage : FlowLayoutPanel
{
    private readonly AppSettings _app;
    private readonly MicEngine _engine;
    private MicSettings S => _app.Mic;
    private bool _sync;

    private readonly ComboBox _input, _output, _monitorDev, _presets;
    private readonly Button _power;
    private readonly Label _status, _cableInfo, _gainVal, _winLevelVal, _hpfVal, _nrVal, _gateVal, _compThrVal, _compRatioVal, _limVal, _monVolVal;
    private readonly Button _cableBtn;
    private readonly LevelMeter _inMeter, _outMeter;
    private readonly TrackBar _winLevel, _gain, _hpf, _nr, _gate, _compThr, _compRatio, _lim, _monVol;
    private readonly CheckBox _mute, _hpfOn, _nrOn, _gateOn, _eqOn, _compOn, _limOn, _monOn, _autoStart;
    private readonly TrackBar[] _eq = new TrackBar[10];
    private readonly Label[] _eqVal = new Label[10];
    private readonly TextBox _presetName;
    private readonly System.Windows.Forms.Timer _saveTimer = new() { Interval = 600 };

    public event Action<string>? Logged;
    public event Action? PowerChanged;

    public MicPage(AppSettings app, MicEngine engine)
    {
        _app = app;
        _engine = engine;
        Dock = DockStyle.Fill;
        FlowDirection = FlowDirection.TopDown;
        WrapContents = false;
        AutoScroll = true;
        BackColor = Theme.Bg;
        Padding = new Padding(0, 6, 0, 20);

        _saveTimer.Tick += (_, _) => { _saveTimer.Stop(); _app.Save(); };

        // ---------------------------------------------------------------- dispositivos
        var dev = new CardPanel("Microfone e saída",
            "Escolha qualquer microfone ligado ao PC (Fifine, webcam, headset, fone Bluetooth…). " +
            "O áudio tratado vai para a saída escolhida. Com o VB-CABLE, os outros apps (Discord, OBS, jogos, Meet) usam \"CABLE Output\" como microfone.");
        _input = Theme.Combo(420);
        _output = Theme.Combo(420);
        _input.SelectedIndexChanged += (_, _) => OnDeviceChanged();
        _output.SelectedIndexChanged += (_, _) => OnDeviceChanged();
        dev.Add(Theme.Row(Theme.Label("Microfone:", width: 150), _input, Theme.Button("Atualizar lista", (_, _) => LoadDevices())));
        dev.Add(Theme.Row(Theme.Label("Enviar voz tratada para:", width: 150), _output));
        _cableInfo = Theme.Label("", Theme.Small, Theme.Muted);
        _cableInfo.MaximumSize = new Size(700, 0);
        _cableBtn = Theme.Button("", (_, _) => CableAction());
        dev.Add(Theme.Row(_cableInfo, _cableBtn));

        _power = Theme.Button("▶  Ligar processamento", (_, _) => TogglePower(), primary: true, width: 240);
        _power.Height = 40;
        _status = Theme.Label("Desligado", color: Theme.Muted);
        _autoStart = Theme.Check("Ligar sozinho ao abrir o app", (_, _) => { if (!_sync) { S.AutoStart = _autoStart.Checked; Changed(); } });
        dev.Add(Theme.Row(_power, _status));
        dev.Add(_autoStart);

        _inMeter = new LevelMeter();
        _outMeter = new LevelMeter();
        dev.Add(Theme.Row(Theme.Label("Entrada", width: 70), _inMeter));
        dev.Add(Theme.Row(Theme.Label("Saída", width: 70), _outMeter));
        Controls.Add(dev);

        // ---------------------------------------------------------------- presets
        var pre = new CardPanel("Presets", "Configurações prontas para cada situação. Você também pode salvar as suas.");
        _presets = Theme.Combo(300);
        _presetName = new TextBox { Width = 200, BackColor = Theme.Field, ForeColor = Theme.Text, BorderStyle = BorderStyle.FixedSingle, Margin = new Padding(3, 6, 6, 3) };
        pre.Add(Theme.Row(_presets, Theme.Button("Aplicar", (_, _) => ApplyPreset(), primary: true), Theme.Button("Excluir meu preset", (_, _) => DeletePreset())));
        pre.Add(Theme.Row(Theme.Label("Salvar atual como:"), _presetName, Theme.Button("Salvar", (_, _) => SavePreset())));
        Controls.Add(pre);

        // ---------------------------------------------------------------- volume
        var vol = new CardPanel("Volume", "Nível do Windows = sensibilidade do próprio microfone. Ganho extra = reforço digital depois do tratamento (o limitador evita estourar).");
        _winLevel = Theme.Track(0, 100, 360, (_, _) => { if (!_sync) { _engine.SetWindowsLevel(S.InputDeviceId, _winLevel.Value); _winLevelVal.Text = $"{_winLevel.Value}%"; } });
        _winLevelVal = Theme.Label("", width: 60);
        _mute = Theme.Check("Mudo", (_, _) => { if (!_sync) _engine.SetWindowsMute(S.InputDeviceId, _mute.Checked); });
        vol.Add(Theme.Row(Theme.Label("Nível do microfone no Windows", width: 230), _winLevel, _winLevelVal, _mute));
        _gain = Theme.Track(-20, 30, 360, (_, _) => { if (!_sync) { S.GainDb = _gain.Value; Changed(); } });
        _gainVal = Theme.Label("", width: 60);
        vol.Add(Theme.Row(Theme.Label("Ganho extra (mais alto / mais baixo)", width: 230), _gain, _gainVal));
        vol.Add(Theme.Row(
            Theme.Button("− 3 dB", (_, _) => _gain.Value = Math.Max(_gain.Minimum, _gain.Value - 3)),
            Theme.Button("+ 3 dB", (_, _) => _gain.Value = Math.Min(_gain.Maximum, _gain.Value + 3)),
            Theme.Button("Zerar ganho", (_, _) => _gain.Value = 0)));
        Controls.Add(vol);

        // ---------------------------------------------------------------- limpeza
        var clean = new CardPanel("Limpeza da voz");
        _hpfOn = Theme.Check("Corte de graves (tira zumbido, ar-condicionado, batidas na mesa)", (_, _) => { if (!_sync) { S.HighPassOn = _hpfOn.Checked; Changed(); } });
        _hpf = Theme.Track(40, 300, 300, (_, _) => { if (!_sync) { S.HighPassHz = _hpf.Value; Changed(); } });
        _hpfVal = Theme.Label("", width: 70);
        clean.Add(_hpfOn);
        clean.Add(Theme.Row(Theme.Label("Frequência de corte", width: 200), _hpf, _hpfVal));

        _nrOn = Theme.Check("Redução de ruído (ventilador, PC, chiado de fundo)", (_, _) => { if (!_sync) { S.NoiseReductionOn = _nrOn.Checked; Changed(); } });
        _nr = Theme.Track(0, 100, 300, (_, _) => { if (!_sync) { S.NoiseReductionAmount = _nr.Value; Changed(); } });
        _nrVal = Theme.Label("", width: 70);
        clean.Add(_nrOn);
        clean.Add(Theme.Row(Theme.Label("Intensidade", width: 200), _nr, _nrVal));

        _gateOn = Theme.Check("Noise gate (silencia o microfone quando você não está falando)", (_, _) => { if (!_sync) { S.GateOn = _gateOn.Checked; Changed(); } });
        _gate = Theme.Track(-80, -20, 300, (_, _) => { if (!_sync) { S.GateThresholdDb = _gate.Value; Changed(); } });
        _gateVal = Theme.Label("", width: 70);
        clean.Add(_gateOn);
        clean.Add(Theme.Row(Theme.Label("Abre acima de", width: 200), _gate, _gateVal));
        Controls.Add(clean);

        // ---------------------------------------------------------------- equalizador
        var eq = new CardPanel("Equalizador de 10 bandas", "−12 a +12 dB. Graves à esquerda, agudos à direita. Dica: +2 a +4 dB em 2k–4k deixa a voz mais clara.");
        _eqOn = Theme.Check("Equalizador ligado", (_, _) => { if (!_sync) { S.EqOn = _eqOn.Checked; Changed(); } });
        eq.Add(_eqOn);
        var bands = new FlowLayoutPanel { AutoSize = true, FlowDirection = FlowDirection.LeftToRight, BackColor = Color.Transparent };
        for (int i = 0; i < 10; i++)
        {
            int idx = i;
            var col = new FlowLayoutPanel { AutoSize = true, FlowDirection = FlowDirection.TopDown, BackColor = Color.Transparent, Margin = new Padding(4, 0, 4, 0) };
            _eqVal[i] = Theme.Label("0", Theme.Small, Theme.Text, 46);
            _eqVal[i].TextAlign = ContentAlignment.MiddleCenter;
            _eq[i] = Theme.Track(-12, 12, 150, (_, _) =>
            {
                _eqVal[idx].Text = $"{_eq[idx].Value:+0;-0;0}";
                if (!_sync) { S.EqBands[idx] = _eq[idx].Value; Changed(); }
            }, Orientation.Vertical);
            _eq[i].Margin = new Padding(9, 0, 3, 0);
            var lbl = Theme.Label(MicSettings.EqLabels[i], Theme.Small, Theme.Muted, 46);
            lbl.TextAlign = ContentAlignment.MiddleCenter;
            col.Controls.Add(_eqVal[i]);
            col.Controls.Add(_eq[i]);
            col.Controls.Add(lbl);
            bands.Controls.Add(col);
        }
        eq.Add(bands);
        eq.Add(Theme.Row(
            Theme.Button("Zerar EQ", (_, _) => SetEq(new float[10])),
            Theme.Button("Mais clareza", (_, _) => SetEq(new float[] { -2, -1, 0, 0, -1, 1, 3, 4, 2, 0 })),
            Theme.Button("Voz mais grave", (_, _) => SetEq(new float[] { 2, 4, 4, 2, 0, 0, 0, 0, 0, 0 })),
            Theme.Button("Menos chiado (s)", (_, _) => SetEq(new float[] { 0, 0, 0, 0, 0, 0, 0, -2, -5, -6 })),
            Theme.Button("Menos abafado", (_, _) => SetEq(new float[] { 0, 0, -1, -3, -2, 0, 2, 2, 1, 0 }))));
        Controls.Add(eq);

        // ---------------------------------------------------------------- dinâmica
        var dyn = new CardPanel("Dinâmica", "Compressor deixa o volume da voz mais constante (sussurro e grito ficam parecidos). Limitador impede que o som estoure.");
        _compOn = Theme.Check("Compressor", (_, _) => { if (!_sync) { S.CompressorOn = _compOn.Checked; Changed(); } });
        _compThr = Theme.Track(-50, 0, 300, (_, _) => { if (!_sync) { S.CompThresholdDb = _compThr.Value; Changed(); } });
        _compThrVal = Theme.Label("", width: 70);
        _compRatio = Theme.Track(1, 10, 300, (_, _) => { if (!_sync) { S.CompRatio = _compRatio.Value; Changed(); } });
        _compRatioVal = Theme.Label("", width: 70);
        dyn.Add(_compOn);
        dyn.Add(Theme.Row(Theme.Label("Limiar", width: 200), _compThr, _compThrVal));
        dyn.Add(Theme.Row(Theme.Label("Taxa (força)", width: 200), _compRatio, _compRatioVal));
        _limOn = Theme.Check("Limitador (anti-estouro)", (_, _) => { if (!_sync) { S.LimiterOn = _limOn.Checked; Changed(); } });
        _lim = Theme.Track(-12, 0, 300, (_, _) => { if (!_sync) { S.LimiterCeilingDb = _lim.Value; Changed(); } });
        _limVal = Theme.Label("", width: 70);
        dyn.Add(_limOn);
        dyn.Add(Theme.Row(Theme.Label("Teto", width: 200), _lim, _limVal));
        Controls.Add(dyn);

        // ---------------------------------------------------------------- monitor
        var mon = new CardPanel("Ouvir minha voz", "Toca a voz já tratada num fone/caixa para você conferir os ajustes. Use fone para não dar microfonia.");
        _monOn = Theme.Check("Ouvir minha voz", (_, _) => { if (!_sync) { S.MonitorOn = _monOn.Checked; _engine.SetMonitor(S.MonitorOn); Changed(); } });
        _monitorDev = Theme.Combo(360);
        _monitorDev.SelectedIndexChanged += (_, _) =>
        {
            if (_sync) return;
            S.MonitorDeviceId = (_monitorDev.SelectedItem as AudioDevice)?.Id;
            Changed();
            if (_engine.Running && S.MonitorOn) { _engine.SetMonitor(false); _engine.Apply(S); _engine.SetMonitor(true); }
        };
        _monVol = Theme.Track(0, 100, 300, (_, _) => { if (!_sync) { S.MonitorVolume = _monVol.Value; Changed(); } });
        _monVolVal = Theme.Label("", width: 60);
        mon.Add(Theme.Row(_monOn, _monitorDev));
        mon.Add(Theme.Row(Theme.Label("Volume do retorno", width: 200), _monVol, _monVolVal));
        Controls.Add(mon);

        _engine.Levels += (i, o) =>
        {
            if (!IsHandleCreated) return;
            BeginInvoke(() => { _inMeter.SetLevel(i); _outMeter.SetLevel(o); });
        };
        _engine.Stopped += () => { if (IsHandleCreated) BeginInvoke(UpdatePower); };

        Resize += (_, _) => FitCards();
        LoadDevices();
        LoadPresets();
        SyncFromSettings();
    }

    private void FitCards()
    {
        int w = Math.Max(600, ClientSize.Width - 30);
        foreach (Control c in Controls) { c.MinimumSize = new Size(w, 0); c.MaximumSize = new Size(w, 0); }
    }

    private void Log(string s) => Logged?.Invoke($"{DateTime.Now:HH:mm:ss}  [mic] {s}");

    private void Changed()
    {
        _engine.Apply(S);
        UpdateValueLabels();
        _saveTimer.Stop();
        _saveTimer.Start();
    }

    // ---------------------------------------------------------------- dispositivos

    public void LoadDevices()
    {
        _sync = true;
        try
        {
            var ins = _engine.Inputs();
            var outs = _engine.Outputs();
            _input.Items.Clear();
            foreach (var d in ins) _input.Items.Add(d);
            _output.Items.Clear();
            _output.Items.Add(new AudioDevice("", "(nenhuma — só testar e ouvir)"));
            foreach (var d in outs) _output.Items.Add(d);
            _monitorDev.Items.Clear();
            _monitorDev.Items.Add(new AudioDevice("", "(saída padrão do Windows)"));
            foreach (var d in outs) _monitorDev.Items.Add(d);

            // escolhas padrão: Fifine (ou microfone padrão) e CABLE Input
            S.InputDeviceId ??= ins.FirstOrDefault(d => d.Name.Contains("fifine", StringComparison.OrdinalIgnoreCase))?.Id
                                ?? _engine.DefaultId(NAudio.CoreAudioApi.DataFlow.Capture);
            S.OutputDeviceId ??= _engine.FindCablePlayback()?.Id ?? "";

            Select(_input, S.InputDeviceId);
            Select(_output, S.OutputDeviceId);
            Select(_monitorDev, S.MonitorDeviceId ?? "");
            if (_input.SelectedIndex < 0 && _input.Items.Count > 0) _input.SelectedIndex = 0;
            if (_output.SelectedIndex < 0) _output.SelectedIndex = 0;
            if (_monitorDev.SelectedIndex < 0) _monitorDev.SelectedIndex = 0;
            S.InputDeviceId = (_input.SelectedItem as AudioDevice)?.Id;
        }
        finally { _sync = false; }
        UpdateCableInfo();
        SyncWindowsLevel();
    }

    private static void Select(ComboBox c, string? id)
    {
        for (int i = 0; i < c.Items.Count; i++)
            if (c.Items[i] is AudioDevice d && d.Id == (id ?? "")) { c.SelectedIndex = i; return; }
    }

    private void OnDeviceChanged()
    {
        if (_sync) return;
        S.InputDeviceId = (_input.SelectedItem as AudioDevice)?.Id;
        S.OutputDeviceId = (_output.SelectedItem as AudioDevice)?.Id;
        Changed();
        SyncWindowsLevel();
        if (_engine.Running) StartEngine();
    }

    private void SyncWindowsLevel()
    {
        _sync = true;
        try
        {
            var lvl = _engine.GetWindowsLevel(S.InputDeviceId);
            _winLevel.Enabled = lvl != null;
            _winLevel.Value = (int)Math.Round(lvl ?? 0);
            _winLevelVal.Text = lvl != null ? $"{_winLevel.Value}%" : "—";
            _mute.Checked = _engine.GetWindowsMute(S.InputDeviceId);
        }
        finally { _sync = false; }
    }

    private void UpdateCableInfo()
    {
        var cableOut = _engine.FindCableRecording();
        if (_engine.FindCablePlayback() == null)
        {
            _cableInfo.Text = "VB-CABLE não encontrado. Para que Discord/OBS/jogos recebam a voz tratada, instale o VB-CABLE (grátis) e reinicie o PC. Sem ele você ainda pode ajustar o nível do microfone e ouvir sua voz.";
            _cableInfo.ForeColor = Theme.Warn;
            _cableBtn.Text = "Baixar VB-CABLE";
        }
        else
        {
            _cableInfo.Text = "VB-CABLE encontrado ✔  Nos outros apps, escolha \"CABLE Output\" como microfone (ou use o botão ao lado para torná-lo o microfone padrão do Windows).";
            _cableInfo.ForeColor = Theme.Good;
            _cableBtn.Text = cableOut != null ? "Usar como microfone padrão" : "Atualizar";
        }
    }

    private void CableAction()
    {
        if (_engine.FindCablePlayback() == null)
        {
            Process.Start(new ProcessStartInfo("https://vb-audio.com/Cable/") { UseShellExecute = true });
            return;
        }
        var rec = _engine.FindCableRecording();
        if (rec == null) { LoadDevices(); return; }
        if (DefaultDevice.Set(rec.Id))
        {
            Log("CABLE Output agora é o microfone padrão do Windows");
            MessageBox.Show("Pronto! \"CABLE Output\" agora é o microfone padrão do Windows.\n\nDeixe o processamento ligado no Buds Control para a sua voz tratada chegar aos apps.",
                "Buds Control", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }
        else MessageBox.Show("Não consegui mudar o padrão automaticamente. Abra Configurações → Sistema → Som → Entrada e escolha \"CABLE Output\".", "Buds Control");
    }

    // ---------------------------------------------------------------- ligar / desligar

    private void TogglePower()
    {
        if (_engine.Running) { _engine.Stop(); Log("Processamento desligado"); }
        else StartEngine();
        UpdatePower();
    }

    public void StartEngine()
    {
        try
        {
            if (S.InputDeviceId == S.OutputDeviceId && !string.IsNullOrEmpty(S.OutputDeviceId)) throw new InvalidOperationException("Entrada e saída iguais.");
            var inName = (_input.SelectedItem as AudioDevice)?.Name ?? "";
            if (inName.Contains("CABLE Output", StringComparison.OrdinalIgnoreCase))
                throw new InvalidOperationException("Escolha o seu microfone real (ex.: Fifine) como entrada, e não o CABLE Output.");
            _engine.Start(S);
            Log("Processamento ligado");
        }
        catch (Exception e)
        {
            Log($"Erro ao ligar: {e.Message}");
            MessageBox.Show($"Não consegui ligar o microfone:\n{e.Message}", "Buds Control", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
        UpdatePower();
    }

    public bool Running => _engine.Running;

    private void UpdatePower()
    {
        bool on = _engine.Running;
        _power.Text = on ? "■  Desligar processamento" : "▶  Ligar processamento";
        _power.BackColor = on ? Theme.Good : Theme.Accent;
        _status.Text = on ? "Ligado: sua voz está sendo tratada" : "Desligado";
        _status.ForeColor = on ? Theme.Good : Theme.Muted;
        PowerChanged?.Invoke();
    }

    public void SetPower(bool on)
    {
        if (on != _engine.Running) TogglePower();
    }

    // ---------------------------------------------------------------- presets

    private void LoadPresets()
    {
        _presets.Items.Clear();
        foreach (var k in Presets.BuiltIn.Keys) _presets.Items.Add(k);
        foreach (var k in _app.UserPresets.Keys) _presets.Items.Add("★ " + k);
        if (_presets.Items.Count > 0) _presets.SelectedIndex = 0;
    }

    private void ApplyPreset()
    {
        var name = _presets.SelectedItem as string;
        if (name == null) return;
        if (name.StartsWith("★ ") && _app.UserPresets.TryGetValue(name[2..], out var u)) CopyEffects(u, S);
        else if (Presets.BuiltIn.TryGetValue(name, out var apply)) apply(S);
        SyncFromSettings();
        Changed();
        Log($"Preset aplicado: {name}");
    }

    private void SavePreset()
    {
        var n = _presetName.Text.Trim();
        if (n.Length == 0) return;
        var p = new MicSettings();
        CopyEffects(S, p);
        _app.UserPresets[n] = p;
        _app.Save();
        _presetName.Text = "";
        LoadPresets();
        _presets.SelectedItem = "★ " + n;
    }

    private void DeletePreset()
    {
        if (_presets.SelectedItem is string name && name.StartsWith("★ "))
        {
            _app.UserPresets.Remove(name[2..]);
            _app.Save();
            LoadPresets();
        }
    }

    private static void CopyEffects(MicSettings from, MicSettings to)
    {
        to.GainDb = from.GainDb; to.HighPassOn = from.HighPassOn; to.HighPassHz = from.HighPassHz;
        to.NoiseReductionOn = from.NoiseReductionOn; to.NoiseReductionAmount = from.NoiseReductionAmount;
        to.GateOn = from.GateOn; to.GateThresholdDb = from.GateThresholdDb;
        to.EqOn = from.EqOn; to.EqBands = (float[])from.EqBands.Clone();
        to.CompressorOn = from.CompressorOn; to.CompThresholdDb = from.CompThresholdDb; to.CompRatio = from.CompRatio;
        to.LimiterOn = from.LimiterOn; to.LimiterCeilingDb = from.LimiterCeilingDb;
    }

    private void SetEq(float[] v)
    {
        S.EqBands = (float[])v.Clone();
        S.EqOn = true;
        SyncFromSettings();
        Changed();
    }

    // ---------------------------------------------------------------- sincronização

    private static int Clamp(TrackBar t, float v) => Math.Clamp((int)Math.Round(v), t.Minimum, t.Maximum);

    private void SyncFromSettings()
    {
        _sync = true;
        try
        {
            if (S.EqBands.Length != 10) S.EqBands = new float[10];
            _autoStart.Checked = S.AutoStart;
            _gain.Value = Clamp(_gain, S.GainDb);
            _hpfOn.Checked = S.HighPassOn; _hpf.Value = Clamp(_hpf, S.HighPassHz);
            _nrOn.Checked = S.NoiseReductionOn; _nr.Value = Clamp(_nr, S.NoiseReductionAmount);
            _gateOn.Checked = S.GateOn; _gate.Value = Clamp(_gate, S.GateThresholdDb);
            _eqOn.Checked = S.EqOn;
            for (int i = 0; i < 10; i++)
            {
                _eq[i].Value = Clamp(_eq[i], S.EqBands[i]);
                _eqVal[i].Text = $"{_eq[i].Value:+0;-0;0}";
            }
            _compOn.Checked = S.CompressorOn; _compThr.Value = Clamp(_compThr, S.CompThresholdDb); _compRatio.Value = Clamp(_compRatio, S.CompRatio);
            _limOn.Checked = S.LimiterOn; _lim.Value = Clamp(_lim, S.LimiterCeilingDb);
            _monOn.Checked = S.MonitorOn; _monVol.Value = Clamp(_monVol, S.MonitorVolume);
        }
        finally { _sync = false; }
        UpdateValueLabels();
    }

    private void UpdateValueLabels()
    {
        _gainVal.Text = $"{S.GainDb:+0;-0;0} dB";
        _hpfVal.Text = $"{S.HighPassHz:0} Hz";
        _nrVal.Text = $"{S.NoiseReductionAmount:0}%";
        _gateVal.Text = $"{S.GateThresholdDb:0} dB";
        _compThrVal.Text = $"{S.CompThresholdDb:0} dB";
        _compRatioVal.Text = $"{S.CompRatio:0}:1";
        _limVal.Text = $"{S.LimiterCeilingDb:0} dB";
        _monVolVal.Text = $"{S.MonitorVolume:0}%";
    }
}
