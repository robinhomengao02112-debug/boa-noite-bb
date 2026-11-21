// Autenticação dos fones Xiaomi/Redmi (variante Bluetooth do SAFER+, 128 bits, 8 rodadas).
// Portado de Gadgetbridge (GNU AGPL v3). Este arquivo segue a mesma licença.
using System.Numerics;
using System.Security.Cryptography;

namespace BudsControlPC.Buds;

public sealed class SaferAuth
{
    private const int Pattern = 0x9999;
    private const int BlockSize = 16;

    private static readonly byte[] Seq = { 0x11, 0x22, 0x33, 0x33, 0x22, 0x11, 0x11, 0x22, 0x33, 0x33, 0x22, 0x11, 0x11, 0x22, 0x33, 0x33 };

    private static readonly int[,] Coefficients =
    {
        {2,1,1,1,4,2,1,1,2,2,4,2,4,4,16,8},
        {2,1,1,1,4,2,1,1,1,1,2,1,2,2,8,4},
        {1,1,4,2,2,2,4,2,16,8,4,4,2,1,1,1},
        {1,1,4,2,1,1,2,1,8,4,2,2,2,1,1,1},
        {16,8,2,2,4,2,4,4,1,1,4,2,1,1,2,1},
        {8,4,1,1,2,1,2,2,1,1,4,2,1,1,2,1},
        {2,2,4,2,4,4,16,8,2,1,1,1,4,2,1,1},
        {1,1,2,1,2,2,8,4,2,1,1,1,4,2,1,1},
        {4,2,4,4,16,8,2,2,1,1,2,1,1,1,4,2},
        {2,1,2,2,8,4,1,1,1,1,2,1,1,1,4,2},
        {4,4,16,8,1,1,2,1,4,2,1,1,4,2,2,2},
        {2,2,8,4,1,1,2,1,4,2,1,1,2,1,1,1},
        {1,1,2,1,1,1,4,2,4,4,16,8,2,2,4,2},
        {1,1,2,1,1,1,4,2,2,2,8,4,1,1,2,1},
        {4,2,1,1,2,1,1,1,4,2,2,2,16,8,4,4},
        {4,2,1,1,2,1,1,1,2,1,1,1,8,4,2,2},
    };

    private readonly byte[,] _bias = new byte[16, 16];
    private readonly byte[] _exp = new byte[256];
    private readonly byte[] _log = new byte[256];

    public SaferAuth()
    {
        var b = new BigInteger(45);
        var m = new BigInteger(257);
        for (int i = 0; i < 16; i++)
            for (int j = 0; j < 16; j++)
            {
                int exponent = 17 * (i + 2) + (j + 1);
                int inner = (int)BigInteger.ModPow(b, exponent, m);
                int v = (int)BigInteger.ModPow(b, inner, m);
                _bias[i, j] = (byte)(v == 256 ? 0 : v);
            }
        for (int i = 0; i < 256; i++)
        {
            int v = (int)BigInteger.ModPow(b, i, m);
            _exp[i] = (byte)(i == 128 ? 0 : v);
        }
        _log[0] = 128;
        for (int i = 1; i < 256; i++)
        {
            int v = (int)BigInteger.ModPow(b, i, m);
            if (v != 256) _log[v] = (byte)i;
        }
    }

    private byte[][] KeySchedule(byte[] keyInit)
    {
        keyInit[15] ^= 6;
        var keys = new byte[17][];
        keys[0] = keyInit;
        var reg = new byte[17];
        byte x = 0;
        for (int i = 0; i < 16; i++) { reg[i] = keyInit[i]; x ^= keyInit[i]; }
        reg[16] = x;
        for (int k = 1; k < 17; k++)
        {
            var key = new byte[16];
            for (int i = 0; i < 17; i++)
            {
                int r = reg[i];
                reg[i] = (byte)((r >> 5) | (r << 3));
            }
            for (int i = 0; i < 16; i++)
                key[i] = (byte)(reg[(k + i) % 17] + _bias[k - 1, i]);
            keys[k] = key;
        }
        return keys;
    }

    private static bool Xor(int i) => ((1 << i) & Pattern) != 0;

    private byte[] Encrypt(byte[] plain, byte[][] keys)
    {
        var c = (byte[])plain.Clone();
        for (int round = 0; round < 8; round++)
        {
            if (round == 2)
                for (int i = 0; i < BlockSize; i++)
                    c[i] = Xor(i) ? (byte)(c[i] ^ plain[i]) : (byte)(c[i] + plain[i]);
            for (int i = 0; i < BlockSize; i++)
                c[i] = Xor(i) ? (byte)(c[i] ^ keys[round * 2][i]) : (byte)(c[i] + keys[round * 2][i]);
            for (int i = 0; i < BlockSize; i++)
                c[i] = Xor(i) ? _exp[c[i]] : _log[c[i]];
            for (int i = 0; i < BlockSize; i++)
                c[i] = Xor(i) ? (byte)(keys[round * 2 + 1][i] + c[i]) : (byte)(keys[round * 2 + 1][i] ^ c[i]);
            var copy = (byte[])c.Clone();
            for (int i = 0; i < BlockSize; i++)
            {
                int sum = 0;
                for (int j = 0; j < BlockSize; j++) sum += Coefficients[i, j] * (sbyte)copy[j];
                c[i] = (byte)sum;
            }
        }
        for (int i = 0; i < BlockSize; i++)
            c[i] = Xor(i) ? (byte)(keys[16][i] ^ c[i]) : (byte)(keys[16][i] + c[i]);
        return c;
    }

    public static byte[] RandomChallenge() => RandomNumberGenerator.GetBytes(BlockSize);

    public byte[] Respond(byte[] challenge) => Encrypt(Seq, KeySchedule((byte[])challenge.Clone()));
}
