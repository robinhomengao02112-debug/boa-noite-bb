using System.Drawing.Drawing2D;

namespace BudsControlPC.UI;

public static class Theme
{
    public static readonly Color Bg = Color.FromArgb(14, 17, 22);
    public static readonly Color Card = Color.FromArgb(24, 28, 35);
    public static readonly Color Field = Color.FromArgb(35, 40, 51);
    public static readonly Color Text = Color.FromArgb(235, 238, 242);
    public static readonly Color Muted = Color.FromArgb(154, 164, 178);
    public static readonly Color Accent = Color.FromArgb(255, 109, 0);
    public static readonly Color Good = Color.FromArgb(76, 175, 80);
    public static readonly Color Warn = Color.FromArgb(255, 179, 0);
    public static readonly Color Bad = Color.FromArgb(229, 57, 53);

    public static readonly Font Base = new("Segoe UI", 9.75f);
    public static readonly Font Small = new("Segoe UI", 8.5f);
    public static readonly Font Bold = new("Segoe UI Semibold", 10.5f);
    public static readonly Font Title = new("Segoe UI Semibold", 13f);
    public static readonly Font Big = new("Segoe UI Semibold", 18f);
    public static readonly Font Mono = new("Consolas", 8.5f);

    public static Label Label(string text, Font? font = null, Color? color = null, int width = 0)
    {
        var l = new Label
        {
            Text = text, Font = font ?? Base, ForeColor = color ?? Text, AutoSize = width == 0,
            Margin = new Padding(3, 7, 3, 3), BackColor = Color.Transparent,
        };
        if (width > 0) { l.Width = width; l.Height = 22; }
        return l;
    }

    public static Button Button(string text, EventHandler onClick, bool primary = false, int width = 0)
    {
        var b = new Button
        {
            Text = text, FlatStyle = FlatStyle.Flat, Font = primary ? Bold : Base,
            BackColor = primary ? Accent : Field, ForeColor = primary ? Color.Black : Text,
            AutoSize = width == 0, Height = 32, Padding = new Padding(8, 2, 8, 2), Margin = new Padding(3, 3, 6, 3),
            Cursor = Cursors.Hand,
        };
        if (width > 0) b.Width = width;
        b.FlatAppearance.BorderColor = primary ? Accent : Color.FromArgb(55, 62, 75);
        b.Click += onClick;
        return b;
    }

    public static CheckBox Check(string text, EventHandler onChange)
    {
        var c = new CheckBox { Text = text, ForeColor = Text, Font = Base, AutoSize = true, Margin = new Padding(3, 6, 12, 3) };
        c.CheckedChanged += onChange;
        return c;
    }

    public static ComboBox Combo(int width)
    {
        return new ComboBox
        {
            DropDownStyle = ComboBoxStyle.DropDownList, Width = width, BackColor = Field, ForeColor = Text,
            FlatStyle = FlatStyle.Flat, Font = Base, Margin = new Padding(3, 4, 6, 3),
        };
    }

    public static TrackBar Track(int min, int max, int width, EventHandler onChange, Orientation o = Orientation.Horizontal)
    {
        var t = new TrackBar
        {
            Minimum = min, Maximum = max, TickStyle = TickStyle.None, BackColor = Card, Orientation = o,
            AutoSize = false, Margin = new Padding(3, 2, 3, 2),
        };
        if (o == Orientation.Horizontal) { t.Width = width; t.Height = 30; }
        else { t.Width = 34; t.Height = width; }
        t.ValueChanged += onChange;
        return t;
    }

    public static RadioButton Radio(string text, EventHandler onChange)
    {
        var r = new RadioButton
        {
            Text = text, Appearance = Appearance.Button, FlatStyle = FlatStyle.Flat, BackColor = Field, ForeColor = Text,
            Font = Base, TextAlign = ContentAlignment.MiddleCenter, Width = 150, Height = 34, Margin = new Padding(3),
            Cursor = Cursors.Hand,
        };
        r.FlatAppearance.CheckedBackColor = Accent;
        r.FlatAppearance.BorderColor = Color.FromArgb(55, 62, 75);
        r.CheckedChanged += (s, e) => { r.ForeColor = r.Checked ? Color.Black : Text; onChange(s, e); };
        return r;
    }

    public static FlowLayoutPanel Row(params Control[] controls)
    {
        var p = new FlowLayoutPanel
        {
            FlowDirection = FlowDirection.LeftToRight, AutoSize = true, WrapContents = true,
            BackColor = Color.Transparent, Margin = new Padding(0, 2, 0, 2),
        };
        p.Controls.AddRange(controls);
        return p;
    }
}

/// <summary>Cartão com título, subtítulo e conteúdo empilhado.</summary>
public sealed class CardPanel : Panel
{
    private readonly FlowLayoutPanel _body;

    public CardPanel(string title, string? subtitle = null)
    {
        BackColor = Theme.Card;
        Padding = new Padding(16, 12, 16, 14);
        Margin = new Padding(12, 6, 12, 6);
        AutoSize = true;
        AutoSizeMode = AutoSizeMode.GrowAndShrink;

        _body = new FlowLayoutPanel
        {
            FlowDirection = FlowDirection.TopDown, WrapContents = false, AutoSize = true,
            BackColor = Color.Transparent, Dock = DockStyle.Top,
        };
        _body.Controls.Add(Theme.Label(title, Theme.Bold));
        if (subtitle != null)
        {
            var sub = Theme.Label(subtitle, Theme.Small, Theme.Muted);
            sub.MaximumSize = new Size(820, 0);
            sub.Margin = new Padding(3, 0, 3, 6);
            _body.Controls.Add(sub);
        }
        Controls.Add(_body);
    }

    public T Add<T>(T c) where T : Control { _body.Controls.Add(c); return c; }

    protected override void OnPaint(PaintEventArgs e)
    {
        base.OnPaint(e);
        e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
        using var pen = new Pen(Color.FromArgb(38, 44, 55));
        e.Graphics.DrawRectangle(pen, 0, 0, Width - 1, Height - 1);
    }
}

/// <summary>Medidor de nível (−60 a 0 dBFS) com retenção de pico.</summary>
public sealed class LevelMeter : Control
{
    private float _db = -90, _peak = -90;
    private DateTime _peakAt;

    public LevelMeter()
    {
        DoubleBuffered = true;
        SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer, true);
        Height = 16; Width = 420; Margin = new Padding(3, 6, 3, 6);
    }

    public void SetLevel(float db)
    {
        _db = db > _db ? db : _db - 1.5f;   // queda suave
        if (db >= _peak || (DateTime.Now - _peakAt).TotalSeconds > 1.2) { _peak = db; _peakAt = DateTime.Now; }
        Invalidate();
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics;
        g.Clear(Theme.Field);
        float Frac(float db) => Math.Clamp((db + 60) / 60f, 0, 1);
        int w = (int)(Width * Frac(_db));
        if (w > 0)
        {
            using var br = new LinearGradientBrush(new Rectangle(0, 0, Width, Height), Theme.Good, Theme.Bad, 0f);
            var blend = new ColorBlend
            {
                Colors = new[] { Theme.Good, Theme.Good, Theme.Warn, Theme.Bad },
                Positions = new[] { 0f, 0.7f, 0.88f, 1f },
            };
            br.InterpolationColors = blend;
            g.FillRectangle(br, 0, 0, w, Height);
        }
        int px = (int)(Width * Frac(_peak));
        using (var p = new Pen(Color.White, 2)) g.DrawLine(p, px, 0, px, Height);
        // marcas a cada 10 dB
        using var tick = new Pen(Color.FromArgb(70, 0, 0, 0));
        for (int db = -50; db < 0; db += 10) { int x = (int)(Width * Frac(db)); g.DrawLine(tick, x, 0, x, Height); }
    }
}
