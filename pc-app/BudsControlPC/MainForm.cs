using BudsControlPC.Audio;
using BudsControlPC.Buds;
using BudsControlPC.UI;

namespace BudsControlPC;

public sealed class MainForm : Form
{
    private readonly AppSettings _app = AppSettings.Load();
    private readonly MicEngine _engine = new();
    private readonly BudsClient _buds = new();
    private readonly MicPage _mic;
    private readonly BudsPage _budsPage;
    private readonly TextBox _log;
    private readonly Panel _content;
    private readonly List<Button> _tabs = new();
    private readonly NotifyIcon _tray;
    private readonly ToolStripMenuItem _trayMic;
    private readonly bool _startMinimized;
    private bool _exiting;

    public MainForm(bool startMinimized)
    {
        _startMinimized = startMinimized;
        Text = "Buds Control PC";
        BackColor = Theme.Bg;
        ForeColor = Theme.Text;
        Font = Theme.Base;
        Size = new Size(1000, 820);
        MinimumSize = new Size(760, 560);
        StartPosition = FormStartPosition.CenterScreen;
        try { Icon = Icon.ExtractAssociatedIcon(Environment.ProcessPath!); } catch { }

        _log = new TextBox
        {
            Multiline = true, ReadOnly = true, ScrollBars = ScrollBars.Vertical, Dock = DockStyle.Fill,
            BackColor = Theme.Card, ForeColor = Color.FromArgb(184, 194, 207), Font = Theme.Mono, BorderStyle = BorderStyle.None,
        };

        _mic = new MicPage(_app, _engine);
        _budsPage = new BudsPage(_app, _buds);
        _mic.Logged += AddLog;
        _engine.Logged += AddLog;
        _buds.Logged += AddLog;

        // ------------------------------------------------------------ cabeçalho e abas
        var header = new Panel { Dock = DockStyle.Top, Height = 92, BackColor = Theme.Bg };
        var title = Theme.Label("Buds Control PC", Theme.Title);
        title.Location = new Point(18, 12);
        var sub = Theme.Label("Microfone avançado + controle dos Redmi Buds", Theme.Small, Theme.Muted);
        sub.Location = new Point(20, 38);
        header.Controls.Add(title);
        header.Controls.Add(sub);

        var tabBar = new FlowLayoutPanel { Location = new Point(14, 56), AutoSize = true, BackColor = Theme.Bg };
        _content = new Panel { Dock = DockStyle.Fill, BackColor = Theme.Bg };
        var logPanel = new Panel { Dock = DockStyle.Fill, Padding = new Padding(12), BackColor = Theme.Bg };
        var logButtons = Theme.Row(
            Theme.Button("Copiar log", (_, _) => { if (_log.TextLength > 0) Clipboard.SetText(_log.Text); }),
            Theme.Button("Limpar", (_, _) => _log.Clear()));
        logButtons.Dock = DockStyle.Top;
        logPanel.Controls.Add(_log);
        logPanel.Controls.Add(logButtons);

        var settingsPage = BuildSettingsPage();
        AddTab(tabBar, "🎙  Microfone", _mic);
        AddTab(tabBar, "🎧  Fones", _budsPage);
        AddTab(tabBar, "⚙  Opções", settingsPage);
        AddTab(tabBar, "📋  Registro", logPanel);
        header.Controls.Add(tabBar);

        Controls.Add(_content);
        Controls.Add(header);
        ShowTab(0);

        // ------------------------------------------------------------ bandeja
        var menu = new ContextMenuStrip();
        menu.Items.Add("Abrir Buds Control", null, (_, _) => ShowFromTray());
        _trayMic = new ToolStripMenuItem("Processamento do microfone", null, (_, _) => _mic.SetPower(!_mic.Running));
        menu.Items.Add(_trayMic);
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("⚡ Conectar fones", null, async (_, _) => await _budsPage.ConnectAsync());
        menu.Items.Add("📱 Passar fones para o celular", null, async (_, _) => await _budsPage.ReleaseAsync());
        menu.Items.Add("Fones: cancelar ruído", null, (_, _) => _buds.SetAmbientMode(1));
        menu.Items.Add("Fones: transparência", null, (_, _) => _buds.SetAmbientMode(2));
        menu.Items.Add("Fones: desligado", null, (_, _) => _buds.SetAmbientMode(0));
        menu.Items.Add("Fones: TURBO ANC", null, (_, _) => _buds.Turbo());
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("Sair", null, (_, _) => { _exiting = true; Close(); });
        _tray = new NotifyIcon { Icon = Icon ?? SystemIcons.Application, Text = "Buds Control PC", ContextMenuStrip = menu, Visible = true };
        _tray.DoubleClick += (_, _) => ShowFromTray();
        _mic.PowerChanged += () => _trayMic.Checked = _mic.Running;
        menu.Opening += (_, _) => _trayMic.Checked = _mic.Running;
    }

    private Control BuildSettingsPage()
    {
        var page = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.TopDown, WrapContents = false, AutoScroll = true, BackColor = Theme.Bg, Padding = new Padding(0, 6, 0, 0) };
        var card = new CardPanel("Opções do aplicativo");
        var startWin = Theme.Check("Iniciar junto com o Windows (minimizado na bandeja)", (s, _) =>
        {
            try { AppSettings.StartWithWindows = ((CheckBox)s!).Checked; } catch (Exception e) { AddLog($"Erro: {e.Message}"); }
        });
        try { startWin.Checked = AppSettings.StartWithWindows; } catch { }
        var tray = Theme.Check("Ao fechar a janela, continuar rodando na bandeja (o microfone continua tratado)", (s, _) =>
        {
            _app.MinimizeToTray = ((CheckBox)s!).Checked; _app.Save();
        });
        tray.Checked = _app.MinimizeToTray;
        card.Add(startWin);
        card.Add(tray);
        page.Controls.Add(card);

        var help = new CardPanel("Como usar o microfone tratado no Discord, OBS, jogos e reuniões",
            "1. Instale o VB-CABLE (botão na aba Microfone) e reinicie o PC.\n" +
            "2. Na aba Microfone, escolha seu microfone (ex.: Fifine) em \"Microfone\" e \"CABLE Input\" em \"Enviar voz tratada para\".\n" +
            "3. Clique em \"Ligar processamento\".\n" +
            "4. No Discord/OBS/jogo, escolha \"CABLE Output\" como microfone — ou clique em \"Usar como microfone padrão\".\n" +
            "5. Ajuste ganho, limpeza e EQ ouvindo sua voz com \"Ouvir minha voz\".\n\n" +
            "Sem o VB-CABLE, o app ainda ajusta o nível real do microfone no Windows (mais alto/mais baixo) e deixa você ouvir e testar os efeitos.");
        page.Controls.Add(help);

        var about = new CardPanel("Sobre",
            "Buds Control PC 1.0 · controle dos fones portado do Buds Control (Android), com protocolo baseado no projeto Gadgetbridge (GNU AGPL v3). " +
            "Áudio via NAudio (WASAPI). Configurações salvas em %APPDATA%\\BudsControlPC.");
        page.Controls.Add(about);
        page.Resize += (_, _) =>
        {
            int w = Math.Max(600, page.ClientSize.Width - 30);
            foreach (Control c in page.Controls) { c.MinimumSize = new Size(w, 0); c.MaximumSize = new Size(w, 0); }
        };
        return page;
    }

    private void AddTab(FlowLayoutPanel bar, string text, Control page)
    {
        int idx = _tabs.Count;
        var b = Theme.Button(text, (_, _) => ShowTab(idx));
        b.Tag = page;
        b.Width = 150;
        b.AutoSize = false;
        _tabs.Add(b);
        bar.Controls.Add(b);
    }

    private void ShowTab(int idx)
    {
        _content.Controls.Clear();
        var page = (Control)_tabs[idx].Tag!;
        page.Dock = DockStyle.Fill;
        _content.Controls.Add(page);
        for (int i = 0; i < _tabs.Count; i++)
        {
            _tabs[i].BackColor = i == idx ? Theme.Accent : Theme.Field;
            _tabs[i].ForeColor = i == idx ? Color.Black : Theme.Text;
        }
    }

    private readonly Queue<string> _pendingLog = new();

    private void AddLog(string line)
    {
        if (!IsHandleCreated) { lock (_pendingLog) _pendingLog.Enqueue(line); return; }
        BeginInvoke(() =>
        {
            if (_log.TextLength > 200_000) _log.Text = _log.Text[^100_000..];
            _log.AppendText(line + Environment.NewLine);
        });
    }

    protected override async void OnShown(EventArgs e)
    {
        base.OnShown(e);
        lock (_pendingLog) while (_pendingLog.Count > 0) _log.AppendText(_pendingLog.Dequeue() + Environment.NewLine);
        if (_startMinimized) BeginInvoke(() => { Hide(); });
        if (_app.Mic.AutoStart) _mic.StartEngine();
        await _budsPage.LoadDevicesAsync();
        if (_app.BudsAutoConnect && _app.BudsDeviceId != null) await _budsPage.AutoConnectAsync();
    }

    private void ShowFromTray()
    {
        Show();
        if (WindowState == FormWindowState.Minimized) WindowState = FormWindowState.Normal;
        Activate();
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        if (!_exiting && _app.MinimizeToTray && e.CloseReason == CloseReason.UserClosing)
        {
            e.Cancel = true;
            Hide();
            _tray.ShowBalloonTip(2500, "Buds Control PC", "Continuo rodando aqui na bandeja. Clique com o botão direito para sair.", ToolTipIcon.Info);
            return;
        }
        _app.Save();
        _tray.Visible = false;
        _engine.Dispose();
        _buds.Dispose();
        base.OnFormClosing(e);
    }
}
