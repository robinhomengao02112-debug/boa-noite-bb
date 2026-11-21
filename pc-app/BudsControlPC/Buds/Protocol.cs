// Protocolo de controle dos fones Xiaomi/Redmi (canal RFCOMM 0xFD2D).
// Engenharia reversa original: Gadgetbridge (GNU AGPL v3). Este arquivo segue a mesma licença.
// Quadro: FE DC BA | tipo | opcode | tamanho(2, BE) | [status se resposta] | seq | payload | EF
namespace BudsControlPC.Buds;

public static class MsgType
{
    public const int PhoneRequest = 0xC4;
    public const int Response = 0x04;
    public static bool IsRequest(int type) => (type & 0x40) != 0;
}

public static class Op
{
    public const int GetDeviceInfo = 0x02;
    public const int Anc = 0x08;
    public const int GetDeviceRunInfo = 0x09;
    public const int ReportStatus = 0x0E;
    public const int AuthChallenge = 0x50;
    public const int AuthConfirm = 0x51;
    public const int SetConfig = 0xF2;
    public const int GetConfig = 0xF3;
    public const int NotifyConfig = 0xF4;
}

public static class Cfg
{
    public const int Gestures = 0x02;
    public const int AutoAnswer = 0x03;
    public const int DoubleConnection = 0x04;
    public const int EarDetection = 0x06;
    public const int EqPreset = 0x07;
    public const int LongGestures = 0x0A;
    public const int EffectStrength = 0x0B;
    public const int AdaptiveAnc = 0x25;
    public const int AdaptiveSound = 0x29;
    public const int EqCurve = 0x37;

    public static readonly int[] AllReadable =
    {
        EffectStrength, AdaptiveAnc, Gestures, LongGestures, EarDetection,
        DoubleConnection, AutoAnswer, AdaptiveSound, EqPreset, EqCurve
    };
}

public static class StrengthTarget
{
    public const int Anc = 0x01;
    public const int Transparency = 0x02;
}

public sealed class Message
{
    public int Type { get; }
    public int Opcode { get; }
    public int Seq { get; }
    public byte[] Payload { get; }

    public Message(int type, int opcode, int seq, byte[] payload)
    {
        Type = type; Opcode = opcode; Seq = seq; Payload = payload;
    }

    public byte[] Encode()
    {
        int extra = MsgType.IsRequest(Type) ? 1 : 2;
        int len = Payload.Length + extra;
        var o = new List<byte>(Payload.Length + 8 + extra) { 0xFE, 0xDC, 0xBA, (byte)Type, (byte)Opcode, (byte)(len >> 8), (byte)len };
        if (!MsgType.IsRequest(Type)) o.Add(0x00);
        o.Add((byte)Seq);
        o.AddRange(Payload);
        o.Add(0xEF);
        return o.ToArray();
    }

    public static Message FromFrame(byte[] f)
    {
        int type = f[3], opcode = f[4];
        int payloadOffset = 3 + (MsgType.IsRequest(type) ? 5 : 6);
        int seq = f[payloadOffset - 1];
        int end = f.Length - 1;
        var payload = end > payloadOffset ? f[payloadOffset..end] : Array.Empty<byte>();
        return new Message(type, opcode, seq, payload);
    }

    public override string ToString() => $"type={Type:X2} op={Opcode:X2} seq={Seq} payload={Hex.Of(Payload)}";
}

/// <summary>Remonta quadros de um fluxo que pode chegar fragmentado ou agrupado.</summary>
public sealed class FrameAssembler
{
    private readonly List<byte> _buf = new();

    public List<Message> Feed(byte[] data, int count)
    {
        _buf.AddRange(data.AsSpan(0, count).ToArray());
        var output = new List<Message>();
        int pos = 0;
        while (true)
        {
            int start = FindHeader(pos);
            if (start < 0) { pos = Math.Max(pos, _buf.Count - 2); break; }
            if (_buf.Count - start < 7) { pos = start; break; }
            int len = (_buf[start + 5] << 8) | _buf[start + 6];
            if (len > 4096) { pos = start + 3; continue; }
            int total = len + 8;
            if (_buf.Count - start < total) { pos = start; break; }
            var frame = _buf.GetRange(start, total).ToArray();
            try { output.Add(Message.FromFrame(frame)); } catch { /* quadro malformado */ }
            pos = start + total;
        }
        if (pos > 0) _buf.RemoveRange(0, Math.Min(pos, _buf.Count));
        return output;
    }

    private int FindHeader(int from)
    {
        for (int i = Math.Max(0, from); i <= _buf.Count - 3; i++)
            if (_buf[i] == 0xFE && _buf[i + 1] == 0xDC && _buf[i + 2] == 0xBA) return i;
        return -1;
    }
}

/// <summary>Fábrica de comandos com controle de sequência.</summary>
public sealed class Commands
{
    private int _seq;
    private int Next() => (_seq++) & 0xFF;

    private byte[] Req(int op, params int[] p) =>
        new Message(MsgType.PhoneRequest, op, Next(), p.Select(x => (byte)x).ToArray()).Encode();

    public byte[] StartAuth()
    {
        var p = new byte[17];
        p[0] = 1;
        Array.Copy(SaferAuth.RandomChallenge(), 0, p, 1, 16);
        return new Message(MsgType.PhoneRequest, Op.AuthChallenge, Next(), p).Encode();
    }

    public byte[] AuthConfirm() => Req(Op.AuthConfirm, 0x01, 0x00);
    public byte[] GetDeviceInfo() => Req(Op.GetDeviceInfo, 0xFF, 0xFF, 0xFF, 0xFF);
    public byte[] GetRunInfo() => Req(Op.GetDeviceRunInfo, 0xFF, 0xFF, 0xFF, 0xFF);
    public byte[] GetAllConfig() => Cfg.AllReadable.SelectMany(c => Req(Op.GetConfig, 0x00, c)).ToArray();

    /// <summary>0 desligado, 1 cancelamento de ruído, 2 transparência.</summary>
    public byte[] SetAmbientMode(int mode) => Req(Op.Anc, 0x02, 0x04, mode);
    public byte[] SetStrength(int target, int level) => Req(Op.SetConfig, 0x04, 0x00, Cfg.EffectStrength, target, level);
    public byte[] SetBool(int cfg, bool on) => Req(Op.SetConfig, 0x03, 0x00, cfg, on ? 1 : 0);
    public byte[] SetInt(int cfg, int v) => Req(Op.SetConfig, 0x03, 0x00, cfg, v);
    public byte[] SetEarDetection(bool on) => Req(Op.Anc, 0x02, 0x06, on ? 0 : 1);

    public byte[] SetGesture(int interaction, bool left, int value) =>
        Req(Op.SetConfig, 0x05, 0x00, Cfg.Gestures, interaction, left ? value : 0xFF, left ? 0xFF : value);

    public byte[] SetLongGestureMode(bool left, int value) =>
        Req(Op.SetConfig, 0x04, 0x00, Cfg.LongGestures, left ? value : 0xFF, left ? 0xFF : value);

    public byte[] SetCustomEq(int[] bandsDb)
    {
        int[] freqs = { 62, 125, 250, 500, 1000, 2016, 4000, 8000, 12000, 16000 };
        var p = new List<int> { 0x24, 0x00, Cfg.EqCurve, 0x05, 0x01, 0x01, 0x0A };
        for (int i = 0; i < 10; i++)
        {
            p.Add((freqs[i] >> 8) & 0xFF);
            p.Add(freqs[i] & 0xFF);
            p.Add(EqDbToByte(bandsDb[i]));
        }
        return Req(Op.SetConfig, p.ToArray());
    }

    public byte[] Raw(int op, byte[] payload) => new Message(MsgType.PhoneRequest, op, Next(), payload).Encode();

    public static int EqDbToByte(int db)
    {
        int v = Math.Clamp(db, -6, 6);
        return v >= 0 ? v : 128 - v;
    }

    public static int EqByteToDb(int b) => (b & 0x80) != 0 ? -(b & 0x7F) : b;
}

public static class Hex
{
    public static string Of(byte[] b) => string.Join(" ", b.Select(x => x.ToString("X2")));

    public static byte[] Parse(string s)
    {
        var clean = new string(s.Where(Uri.IsHexDigit).ToArray());
        if (clean.Length % 2 != 0) throw new FormatException("Hex com número ímpar de dígitos");
        return Enumerable.Range(0, clean.Length / 2).Select(i => Convert.ToByte(clean.Substring(i * 2, 2), 16)).ToArray();
    }
}
