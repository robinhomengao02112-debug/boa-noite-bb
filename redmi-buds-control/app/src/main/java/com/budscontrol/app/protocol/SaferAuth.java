/*
 * Autenticação dos fones Xiaomi/Redmi (variante Bluetooth do SAFER+, chave de 128 bits, 8 rodadas).
 *
 * Portado de Gadgetbridge (nodomain.freeyourgadget.gadgetbridge.service.devices.redmibuds5pro.protocol),
 * licenciado sob GNU AGPL v3 ou posterior. Este arquivo mantém a mesma licença.
 */
package com.budscontrol.app.protocol;

import java.math.BigInteger;
import java.security.SecureRandom;

public final class SaferAuth {
    private static final int PATTERN = 0x9999;
    private static final int BLOCK_SIZE = 16;

    private static final byte[] SEQ = {0x11, 0x22, 0x33, 0x33, 0x22, 0x11, 0x11, 0x22, 0x33, 0x33, 0x22, 0x11, 0x11, 0x22, 0x33, 0x33};

    private static final int[][] COEFFICIENTS = {
            {2, 1, 1, 1, 4, 2, 1, 1, 2, 2, 4, 2, 4, 4, 16, 8},
            {2, 1, 1, 1, 4, 2, 1, 1, 1, 1, 2, 1, 2, 2, 8, 4},
            {1, 1, 4, 2, 2, 2, 4, 2, 16, 8, 4, 4, 2, 1, 1, 1},
            {1, 1, 4, 2, 1, 1, 2, 1, 8, 4, 2, 2, 2, 1, 1, 1},
            {16, 8, 2, 2, 4, 2, 4, 4, 1, 1, 4, 2, 1, 1, 2, 1},
            {8, 4, 1, 1, 2, 1, 2, 2, 1, 1, 4, 2, 1, 1, 2, 1},
            {2, 2, 4, 2, 4, 4, 16, 8, 2, 1, 1, 1, 4, 2, 1, 1},
            {1, 1, 2, 1, 2, 2, 8, 4, 2, 1, 1, 1, 4, 2, 1, 1},
            {4, 2, 4, 4, 16, 8, 2, 2, 1, 1, 2, 1, 1, 1, 4, 2},
            {2, 1, 2, 2, 8, 4, 1, 1, 1, 1, 2, 1, 1, 1, 4, 2},
            {4, 4, 16, 8, 1, 1, 2, 1, 4, 2, 1, 1, 4, 2, 2, 2},
            {2, 2, 8, 4, 1, 1, 2, 1, 4, 2, 1, 1, 2, 1, 1, 1},
            {1, 1, 2, 1, 1, 1, 4, 2, 4, 4, 16, 8, 2, 2, 4, 2},
            {1, 1, 2, 1, 1, 1, 4, 2, 2, 2, 8, 4, 1, 1, 2, 1},
            {4, 2, 1, 1, 2, 1, 1, 1, 4, 2, 2, 2, 16, 8, 4, 4},
            {4, 2, 1, 1, 2, 1, 1, 1, 2, 1, 1, 1, 8, 4, 2, 2}
    };

    private final byte[][] biasMatrix = new byte[16][16];
    private final byte[] expTab = new byte[256];
    private final byte[] logTab = new byte[256];

    public SaferAuth() {
        BigInteger base = BigInteger.valueOf(45);
        BigInteger mod = BigInteger.valueOf(257);
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int exponent = 17 * (i + 2) + (j + 1);
                int inner = base.modPow(BigInteger.valueOf(exponent), mod).intValue();
                int v = base.modPow(BigInteger.valueOf(inner), mod).intValue();
                biasMatrix[i][j] = (byte) (v == 256 ? 0 : v);
            }
        }
        for (int i = 0; i < 256; i++) {
            int v = base.modPow(BigInteger.valueOf(i), mod).intValue();
            expTab[i] = (byte) (i == 128 ? 0 : v);
        }
        logTab[0] = (byte) 128;
        for (int i = 1; i < 256; i++) {
            int v = base.modPow(BigInteger.valueOf(i), mod).intValue();
            if (v != 256) {
                logTab[v] = (byte) i;
            }
        }
    }

    private byte[][] keySchedule(byte[] keyInit) {
        keyInit[15] ^= 6;
        byte[][] keys = new byte[17][];
        keys[0] = keyInit;
        byte[] register = new byte[17];
        byte xor = 0;
        for (int i = 0; i < 16; i++) {
            register[i] = keyInit[i];
            xor ^= keyInit[i];
        }
        register[16] = xor;
        for (int keyIdx = 1; keyIdx < keys.length; keyIdx++) {
            byte[] keyI = new byte[16];
            for (int i = 0; i < 17; i++) {
                int r = register[i] & 0xff;
                register[i] = (byte) ((r >>> 5) | (r << 3));
            }
            for (int i = 0; i < 16; i++) {
                keyI[i] = (byte) (register[(keyIdx + i) % 17] + biasMatrix[keyIdx - 1][i]);
            }
            keys[keyIdx] = keyI;
        }
        return keys;
    }

    private byte[] encrypt(byte[] plaintext, byte[][] keys) {
        byte[] c = plaintext.clone();
        for (int round = 0; round < 8; round++) {
            if (round == 2) {
                for (int i = 0; i < BLOCK_SIZE; i++) {
                    if (((1 << i) & PATTERN) != 0) c[i] ^= plaintext[i];
                    else c[i] += plaintext[i];
                }
            }
            for (int i = 0; i < BLOCK_SIZE; i++) {
                if (((1 << i) & PATTERN) != 0) c[i] ^= keys[round * 2][i];
                else c[i] += keys[round * 2][i];
            }
            for (int i = 0; i < BLOCK_SIZE; i++) {
                if (((1 << i) & PATTERN) != 0) c[i] = expTab[c[i] & 0xff];
                else c[i] = logTab[c[i] & 0xff];
            }
            for (int i = 0; i < BLOCK_SIZE; i++) {
                if (((1 << i) & PATTERN) != 0) c[i] = (byte) (keys[round * 2 + 1][i] + c[i]);
                else c[i] = (byte) (keys[round * 2 + 1][i] ^ c[i]);
            }
            byte[] copy = c.clone();
            for (int i = 0; i < BLOCK_SIZE; i++) {
                byte sum = 0;
                for (int j = 0; j < BLOCK_SIZE; j++) {
                    sum += (byte) (COEFFICIENTS[i][j] * copy[j]);
                }
                c[i] = sum;
            }
        }
        for (int i = 0; i < BLOCK_SIZE; i++) {
            if (((1 << i) & PATTERN) != 0) c[i] = (byte) (keys[16][i] ^ c[i]);
            else c[i] = (byte) (keys[16][i] + c[i]);
        }
        return c;
    }

    public static byte[] randomChallenge() {
        byte[] res = new byte[BLOCK_SIZE];
        new SecureRandom().nextBytes(res);
        return res;
    }

    public byte[] respond(byte[] challenge) {
        return encrypt(SEQ, keySchedule(challenge.clone()));
    }
}
