using BudsControlPC.Buds;

namespace BudsControlPC.UI;

public sealed class BudsPage : FlowLayoutPanel
{
    private readonly AppSettings _app;
    private readonly BudsClient _buds;
    private bool _sync;

    private readonly ComboBox _devices;
    private readonly Label _status, _batL, _batR, _batC, _inEar, _fw;
    private readonly RadioButton _mAnc, _mTransp, _mOff, _sLight, _sBal, _sDeep, _tNorm, _tVoice, _tAmb;
    private readonly RadioButton[] _eqPresets;
    private readonly CheckBox _adaptive, _dual, _ear, _autoAns, _adaptSound, _autoConnect;
    private readonly TrackBar[] _eq = new TrackBar[10];
    private readonly Label[] _eqVal = new Label[10];
    private readonly ComboBox[] _gest = new ComboBox[8];
    private readonly ComboBox _longL, _longR;
    private readonly TextBox _rawOp, _rawPayload;
    private readonly List<Control> _needConn = new();

    private static readonly (int V, string T)[] SingleFuncs = { (8, "Nenhum"), (1, "Play/Pausa"), (2, "Anterior"), (3, "Próxima"), (4, "Volume +"), (5, "Volume −") };
    private static readonly (int V, string T)[] MultiFuncs = SingleFuncs[1..];
    private static readonly (int V, string T)[] LongModes = { (0, "Assistente de voz"), (6, "Alternar modos de som") };
    private static readonly (int V, string T)[] LongCycle = { (3, "ANC ↔ Desligado"), (5, "Transparência ↔ Desligado"), (6, "ANC ↔ Transparência"), (7, "Todos") };
    private static readonly (int V, string T)[] EqPresetList = { (0, "Padrão"), (1, "Voz"), (5, "Graves"), (6, "Agudos"), (BudsClient.EqCustom, "Personalizado") };

    public BudsPage(AppSettings app, BudsClient buds)
    {
        _app = app;
        _buds = buds;
        Dock = DockStyle.Fill;
        FlowDirection = FlowDirection.TopDown;
        WrapContents = false;
        AutoScroll = true;
        BackColor = Theme.Bg;
        Padding = new Padding(0, 6, 0, 20);

        // ---------------------------------------------------------------- conexão
        var conn = new CardPanel("Fones Redmi Buds",
            "Os fones precisam estar pareados e conectados a este PC pelo Bluetooth do Windows. Feche o app Xiaomi Earbuds do celular enquanto usa.");
        _devices = Theme.Combo(360);
        conn.Add(Theme.Row(Theme.Label("Fone:", width: 60), _devices,
            Theme.Button("Atualizar", async (_, _) => await LoadDevicesAsync()),
            Theme.Button("Conectar", async (_, _) => await ConnectAsync(), primary: true),
            Theme.Button("Desconectar", (_, _) => _buds.Disconnect())));
        _status = Theme.Label("Desconectado", color: Theme.Muted);
        _autoConnect = Theme.Check("Conectar sozinho ao abrir o app", (_, _) => { if (!_sync) { _app.BudsAutoConnect = _autoConnect.Checked; _app.Save(); } });
        conn.Add(_status);
        conn.Add(_autoConnect);
        Controls.Add(conn);

        // ---------------------------------------------------------------- bateria
        var bat = new CardPanel("Bateria");
        _batL = Theme.Label("E: —", Theme.Big, width: 200);
        _batR = Theme.Label("D: —", Theme.Big, width: 200);
        _batC = Theme.Label("Estojo: —", Theme.Big, width: 260);
        _batL.Height = _batR.Height = _batC.Height = 40;
        bat.Add(Theme.Row(_batL, _batR, _batC));
        _inEar = Theme.Label("", Theme.Small, Theme.Muted);
        _fw = Theme.Label("", Theme.Small, Theme.Muted);
        bat.Add(Theme.Row(_inEar, _fw));
        Controls.Add(bat);

        // ---------------------------------------------------------------- ANC
        var anc = new CardPanel("Cancelamento de ruído");
        _mAnc = Theme.Radio("Cancelar ruído", (_, _) => { if (!_sync && _mAnc.Checked) _buds.SetAmbientMode(1); });
        _mTransp = Theme.Radio("Transparência", (_, _) => { if (!_sync && _mTransp.Checked) _buds.SetAmbientMode(2); });
        _mOff = Theme.Radio("Desligado", (_, _) => { if (!_sync && _mOff.Checked) _buds.SetAmbientMode(0); });
        anc.Add(Group(_mAnc, _mTransp, _mOff));
        anc.Add(Theme.Label("Intensidade do cancelamento", Theme.Small, Theme.Muted));
        _sLight = Theme.Radio("Leve", (_, _) => { if (!_sync && _sLight.Checked) _buds.SetAncStrength(BudsClient.AncLight); });
        _sBal = Theme.Radio("Equilibrado", (_, _) => { if (!_sync && _sBal.Checked) _buds.SetAncStrength(BudsClient.AncBalanced); });
        _sDeep = Theme.Radio("Profundo", (_, _) => { if (!_sync && _sDeep.Checked) _buds.SetAncStrength(BudsClient.AncDeep); });
        anc.Add(Group(_sLight, _sBal, _sDeep));
        anc.Add(Theme.Label("Tipo de transparência", Theme.Small, Theme.Muted));
        _tNorm = Theme.Radio("Normal", (_, _) => { if (!_sync && _tNorm.Checked) _buds.SetTransparencyStrength(0); });
        _tVoice = Theme.Radio("Voz", (_, _) => { if (!_sync && _tVoice.Checked) _buds.SetTransparencyStrength(1); });
        _tAmb = Theme.Radio("Ambiente", (_, _) => { if (!_sync && _tAmb.Checked) _buds.SetTransparencyStrength(2); });
        anc.Add(Group(_tNorm, _tVoice, _tAmb));
        _adaptive = Theme.Check("ANC adaptativo do fone (se o modelo suportar)", (_, _) => { if (!_sync) _buds.SetAdaptiveAnc(_adaptive.Checked); });
        anc.Add(Need(_adaptive));
        var turbo = Theme.Button("⚡  TURBO ANC  (isolamento máximo)", (_, _) => _buds.Turbo(), primary: true, width: 330);
        turbo.Height = 42;
        anc.Add(Need(turbo));
        Controls.Add(anc);

        // ---------------------------------------------------------------- EQ do fone
        var eq = new CardPanel("Equalizador do fone", "Aplicado no chip do fone: vale para qualquer aparelho conectado a ele.");
        _eqPresets = EqPresetList.Select(p => Theme.Radio(p.T, (s, _) =>
        {
            if (!_sync && s is RadioButton { Checked: true }) _buds.SetEqPreset(p.V);
        })).ToArray();
        foreach (var r in _eqPresets) r.Width = 120;
        eq.Add(Group(_eqPresets));
        var bands = new FlowLayoutPanel { AutoSize = true, BackColor = Color.Transparent };
        string[] labels = { "62", "125", "250", "500", "1k", "2k", "4k", "8k", "12k", "16k" };
        for (int i = 0; i < 10; i++)
        {
            int idx = i;
            var col = new FlowLayoutPanel { AutoSize = true, FlowDirection = FlowDirection.TopDown, BackColor = Color.Transparent, Margin = new Padding(4, 0, 4, 0) };
            _eqVal[i] = Theme.Label("0", Theme.Small, Theme.Text, 46);
            _eqVal[i].TextAlign = ContentAlignment.MiddleCenter;
            _eq[i] = Theme.Track(-6, 6, 130, (_, _) => _eqVal[idx].Text = $"{_eq[idx].Value:+0;-0;0}", Orientation.Vertical);
            _eq[i].Margin = new Padding(9, 0, 3, 0);
            var l = Theme.Label(labels[i], Theme.Small, Theme.Muted, 46);
            l.TextAlign = ContentAlignment.MiddleCenter;
            col.Controls.AddRange(new Control[] { _eqVal[i], _eq[i], l });
            bands.Controls.Add(col);
        }
        eq.Add(bands);
        eq.Add(Theme.Row(
            Need(Theme.Button("Aplicar EQ no fone", (_, _) => _buds.SetCustomEq(_eq.Select(t => t.Value).ToArray()), primary: true)),
            Theme.Button("Zerar", (_, _) => SetBands(new int[10])),
            Theme.Button("Mascarar ruído", (_, _) => SetBands(new[] { 2, 2, 1, 0, 0, 1, 1, 0, 0, 0 })),
            Theme.Button("Graves forte", (_, _) => SetBands(new[] { 6, 5, 3, 1, 0, 0, 0, 0, 0, 0 })),
            Theme.Button("Voz/podcast", (_, _) => SetBands(new[] { -3, -2, -1, 1, 3, 4, 3, 1, 0, -1 })),
            Theme.Button("Brilho", (_, _) => SetBands(new[] { 0, 0, 0, 0, 0, 1, 3, 4, 4, 3 }))));
        Controls.Add(eq);

        // ---------------------------------------------------------------- recursos
        var feat = new CardPanel("Recursos e estabilidade");
        _dual = Theme.Check("Conexão dupla (2 aparelhos) — desligar reduz cortes e latência", (_, _) => { if (!_sync) _buds.SetDualConnection(_dual.Checked); });
        _ear = Theme.Check("Detecção de uso (pausa ao tirar o fone)", (_, _) => { if (!_sync) _buds.SetEarDetection(_ear.Checked); });
        _autoAns = Theme.Check("Atender chamadas ao colocar o fone", (_, _) => { if (!_sync) _buds.SetAutoAnswer(_autoAns.Checked); });
        _adaptSound = Theme.Check("Som adaptativo (se o modelo suportar)", (_, _) => { if (!_sync) _buds.SetAdaptiveSound(_adaptSound.Checked); });
        feat.Add(Need(_dual)); feat.Add(Need(_ear)); feat.Add(Need(_autoAns)); feat.Add(Need(_adaptSound));
        Controls.Add(feat);

        // ---------------------------------------------------------------- gestos
        var ges = new CardPanel("Gestos de toque");
        string[] names = { "Toque simples", "Toque duplo", "Toque triplo", "Toque longo" };
        for (int g = 0; g < 4; g++)
        {
            var opts = g == 0 ? SingleFuncs : g == 3 ? LongModes : MultiFuncs;
            var row = new List<Control> { Theme.Label(names[g], width: 120) };
            for (int side = 0; side < 2; side++)
            {
                int slot = g * 2 + side;
                var c = Theme.Combo(190);
                foreach (var o in opts) c.Items.Add(new Opt(o.V, o.T));
                c.SelectedIndexChanged += (_, _) => { if (!_sync && c.SelectedItem is Opt o) _buds.SetGesture(slot, o.V); };
                _gest[slot] = c;
                row.Add(Theme.Label(side == 0 ? "E" : "D", Theme.Small, Theme.Muted));
                row.Add(Need(c));
            }
            ges.Add(Theme.Row(row.ToArray()));
        }
        _longL = Theme.Combo(190); _longR = Theme.Combo(190);
        foreach (var o in LongCycle) { _longL.Items.Add(new Opt(o.V, o.T)); _longR.Items.Add(new Opt(o.V, o.T)); }
        _longL.SelectedIndexChanged += (_, _) => { if (!_sync && _longL.SelectedItem is Opt o) _buds.SetLongCycle(true, o.V); };
        _longR.SelectedIndexChanged += (_, _) => { if (!_sync && _longR.SelectedItem is Opt o) _buds.SetLongCycle(false, o.V); };
        ges.Add(Theme.Row(Theme.Label("Ciclo do longo", width: 120), Theme.Label("E", Theme.Small, Theme.Muted), Need(_longL), Theme.Label("D", Theme.Small, Theme.Muted), Need(_longR)));
        Controls.Add(ges);

        // ---------------------------------------------------------------- console
        var raw = new CardPanel("Console do protocolo", "Para testar comandos não mapeados. Use com cuidado.");
        _rawOp = new TextBox { Text = "08", Width = 60, BackColor = Theme.Field, ForeColor = Theme.Text, BorderStyle = BorderStyle.FixedSingle, Margin = new Padding(3, 6, 6, 3) };
        _rawPayload = new TextBox { Text = "02 04 01", Width = 300, BackColor = Theme.Field, ForeColor = Theme.Text, BorderStyle = BorderStyle.FixedSingle, Margin = new Padding(3, 6, 6, 3) };
        raw.Add(Theme.Row(Theme.Label("Opcode"), _rawOp, Theme.Label("Payload (hex)"), _rawPayload,
            Need(Theme.Button("Enviar", (_, _) => SendRaw())),
            Need(Theme.Button("Reler tudo do fone", (_, _) => _buds.RefreshAll()))));
        Controls.Add(raw);

        _buds.Changed += () => { if (IsHandleCreated) BeginInvoke(SyncFromState); };
        Resize += (_, _) =>
        {
            int w = Math.Max(600, ClientSize.Width - 30);
            foreach (Control c in Controls) { c.MinimumSize = new Size(w, 0); c.MaximumSize = new Size(w, 0); }
        };
        _sync = true; _autoConnect.Checked = _app.BudsAutoConnect; _sync = false;
        SyncFromState();
    }

    private sealed record Opt(int V, string T) { public override string ToString() => T; }

    private T Need<T>(T c) where T : Control { _needConn.Add(c); return c; }

    private FlowLayoutPanel Group(params RadioButton[] radios)
    {
        var p = new FlowLayoutPanel { AutoSize = true, BackColor = Color.Transparent, Margin = new Padding(0, 2, 0, 6) };
        foreach (var r in radios) { p.Controls.Add(r); _needConn.Add(r); }
        return p;
    }

    private void SetBands(int[] v)
    {
        for (int i = 0; i < 10; i++) _eq[i].Value = Math.Clamp(v[i], -6, 6);
    }

    private void SendRaw()
    {
        try { _buds.SendRaw(Convert.ToInt32(_rawOp.Text.Trim(), 16), Hex.Parse(_rawPayload.Text)); }
        catch (Exception e) { MessageBox.Show($"Hex inválido: {e.Message}", "Buds Control"); }
    }

    public async Task LoadDevicesAsync()
    {
        try
        {
            var list = await BudsClient.ListPairedAsync();
            _devices.Items.Clear();
            foreach (var d in list) _devices.Items.Add(d);
            var saved = list.FirstOrDefault(d => d.Id == _app.BudsDeviceId)
                        ?? list.FirstOrDefault(d => d.Name.Contains("buds", StringComparison.OrdinalIgnoreCase));
            if (saved != null) _devices.SelectedItem = saved;
            else if (list.Count > 0) _devices.SelectedIndex = 0;
            if (list.Count == 0) _status.Text = "Nenhum aparelho Bluetooth pareado. Pareie os fones em Configurações → Bluetooth.";
        }
        catch (Exception e)
        {
            _status.Text = $"Bluetooth indisponível: {e.Message}";
        }
    }

    public async Task ConnectAsync()
    {
        if (_devices.SelectedItem is not PairedDevice d) { _status.Text = "Escolha os fones na lista."; return; }
        _app.BudsDeviceId = d.Id;
        _app.Save();
        await _buds.ConnectAsync(d.Id);
    }

    private static void Check(RadioButton r, bool v) { if (r.Checked != v) r.Checked = v; }

    private static string Bat(string p, Battery? b) => b == null ? $"{p}: —" : $"{p}: {b.Level}%{(b.Charging ? " ⚡" : "")}";

    private void SyncFromState()
    {
        var s = _buds.State;
        _sync = true;
        try
        {
            bool on = s.Link == Link.Connected;
            foreach (var c in _needConn) c.Enabled = on;
            _status.Text = (s.DeviceName != null ? s.DeviceName + " — " : "") + s.Status;
            _status.ForeColor = s.Link switch
            {
                Link.Connected => Theme.Good,
                Link.Connecting or Link.Authenticating => Theme.Warn,
                Link.Error => Theme.Bad,
                _ => Theme.Muted,
            };
            _batL.Text = Bat("E", s.Left); _batR.Text = Bat("D", s.Right); _batC.Text = Bat("Estojo", s.Case);
            _inEar.Text = s.InEar ?? "";
            _fw.Text = s.Firmware != null ? $"   Firmware {s.Firmware}" : "";

            Check(_mAnc, s.AmbientMode == 1); Check(_mTransp, s.AmbientMode == 2); Check(_mOff, s.AmbientMode == 0);
            Check(_sLight, s.AncStrength == BudsClient.AncLight); Check(_sBal, s.AncStrength == BudsClient.AncBalanced); Check(_sDeep, s.AncStrength == BudsClient.AncDeep);
            Check(_tNorm, s.TransparencyStrength == 0); Check(_tVoice, s.TransparencyStrength == 1); Check(_tAmb, s.TransparencyStrength == 2);
            _adaptive.Checked = s.AdaptiveAnc == true;
            for (int i = 0; i < _eqPresets.Length; i++) Check(_eqPresets[i], s.EqPreset == EqPresetList[i].V);
            for (int i = 0; i < 10; i++) _eq[i].Value = Math.Clamp(s.EqBands[i], -6, 6);
            _dual.Checked = s.DualConnection == true;
            _ear.Checked = s.EarDetection == true;
            _autoAns.Checked = s.AutoAnswer == true;
            _adaptSound.Checked = s.AdaptiveSound == true;
            for (int k = 0; k < 8; k++) SelectOpt(_gest[k], s.Gestures[k]);
            SelectOpt(_longL, s.LongCycle?.L);
            SelectOpt(_longR, s.LongCycle?.R);
        }
        finally { _sync = false; }
    }

    private static void SelectOpt(ComboBox c, int? v)
    {
        if (v == null) return;
        for (int i = 0; i < c.Items.Count; i++)
            if (c.Items[i] is Opt o && o.V == v) { c.SelectedIndex = i; return; }
    }
}
