using BudsControlPC.Buds;

namespace BudsControlPC.UI;

/// <summary>Procura e pareia fones novos sem abrir as configurações do Windows.</summary>
public sealed class PairDialog : Form
{
    private readonly ListBox _list;
    private readonly Label _status;
    private readonly Button _scan, _pair;

    /// <summary>Id do aparelho pareado com sucesso.</summary>
    public string? PairedId { get; private set; }

    public PairDialog()
    {
        Text = "Parear fones novos";
        BackColor = Theme.Bg;
        ForeColor = Theme.Text;
        Font = Theme.Base;
        Size = new Size(520, 460);
        StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = MinimizeBox = false;

        var help = Theme.Label("1. Coloque os fones no estojo, abra a tampa e segure o botão do estojo por ~3 s até a luz piscar.\n2. Clique em \"Procurar\", escolha os fones e clique em \"Parear\".",
            Theme.Small, Theme.Muted);
        help.MaximumSize = new Size(470, 0);
        help.Location = new Point(16, 14);

        _list = new ListBox
        {
            Location = new Point(16, 70), Size = new Size(470, 250), BackColor = Theme.Card, ForeColor = Theme.Text,
            BorderStyle = BorderStyle.FixedSingle, Font = Theme.Base,
        };
        _list.DoubleClick += async (_, _) => await PairSelected();
        _status = Theme.Label("", Theme.Small, Theme.Muted);
        _status.Location = new Point(16, 328);
        _status.MaximumSize = new Size(470, 0);

        _scan = Theme.Button("Procurar", async (_, _) => await Scan(), width: 140);
        _scan.Location = new Point(16, 370);
        _pair = Theme.Button("Parear", async (_, _) => await PairSelected(), primary: true, width: 140);
        _pair.Location = new Point(166, 370);
        var close = Theme.Button("Fechar", (_, _) => Close(), width: 120);
        close.Location = new Point(366, 370);

        Controls.AddRange(new Control[] { help, _list, _status, _scan, _pair, close });
        Shown += async (_, _) => await Scan();
    }

    private async Task Scan()
    {
        _scan.Enabled = false;
        _list.Items.Clear();
        _status.Text = "Procurando aparelhos por perto…";
        try
        {
            await BtAudio.ScanAsync(TimeSpan.FromSeconds(12), f => BeginInvoke(() =>
            {
                // fones Redmi/Xiaomi primeiro
                if (f.Name.Contains("buds", StringComparison.OrdinalIgnoreCase) || f.Name.Contains("redmi", StringComparison.OrdinalIgnoreCase))
                    _list.Items.Insert(0, f);
                else _list.Items.Add(f);
                if (_list.SelectedIndex < 0) _list.SelectedIndex = 0;
            }));
            _status.Text = _list.Items.Count == 0
                ? "Nada encontrado. Confira se os fones estão piscando (modo de pareamento) e procure de novo."
                : "Escolha os fones e clique em Parear.";
        }
        catch (Exception e) { _status.Text = $"Bluetooth indisponível: {e.Message}"; }
        _scan.Enabled = true;
    }

    private async Task PairSelected()
    {
        if (_list.SelectedItem is not BtAudio.FoundDevice d) return;
        _pair.Enabled = false;
        _status.Text = $"Pareando com {d.Name}…";
        try
        {
            var (ok, msg) = await BtAudio.PairAsync(d.Id);
            _status.Text = msg;
            _status.ForeColor = ok ? Theme.Good : Theme.Bad;
            if (ok) { PairedId = d.Id; DialogResult = DialogResult.OK; await Task.Delay(700); Close(); }
        }
        catch (Exception e) { _status.Text = $"Erro: {e.Message}"; }
        _pair.Enabled = true;
    }
}
