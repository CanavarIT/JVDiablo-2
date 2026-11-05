package com.jv.diablo2.mpq;

import java.io.IOException;

/**
 * Распаковка PKWARE DCL "implode" (формат сжатия секторов MPQ у Diablo 2).
 * Алгоритм эквивалентен публичному blast.c (Марк Адлер), написан заново на Java.
 */
public final class Pkware {
    private Pkware() {
    }

    private static final int MAXBITS = 13;

    private static final int[] LITLEN = {
            11, 124, 8, 7, 28, 7, 188, 13, 76, 4, 10, 8, 12, 10, 12, 10, 8, 23, 8, 9,
            7, 6, 7, 8, 7, 6, 55, 8, 23, 24, 12, 11, 7, 9, 11, 12, 6, 7, 22, 5,
            7, 24, 6, 11, 9, 6, 7, 22, 7, 11, 38, 7, 9, 8, 25, 11, 8, 11, 9, 12,
            8, 12, 5, 38, 5, 38, 5, 11, 7, 5, 6, 21, 6, 10, 53, 8, 7, 24, 10, 27,
            44, 253, 253, 253, 252, 252, 252, 13, 12, 45, 12, 45, 12, 61, 12, 45, 44, 173};
    private static final int[] LENLEN = {2, 35, 36, 53, 38, 23};
    private static final int[] DISTLEN = {2, 20, 53, 230, 247, 151, 248};
    private static final int[] BASE = {3, 2, 4, 5, 6, 7, 8, 9, 10, 12, 16, 24, 40, 72, 136, 264};
    private static final int[] EXTRA = {0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8};

    private static final Huff LIT = new Huff(LITLEN);
    private static final Huff LEN = new Huff(LENLEN);
    private static final Huff DIST = new Huff(DISTLEN);

    private static final class Huff {
        final short[] count = new short[MAXBITS + 1];
        final short[] symbol = new short[256];

        Huff(int[] rep) {
            short[] length = new short[256];
            int sym = 0;
            for (int r : rep) {
                int left = (r >> 4) + 1;
                int len = r & 15;
                do {
                    length[sym++] = (short) len;
                } while (--left > 0);
            }
            for (int s = 0; s < sym; s++) count[length[s]]++;
            short[] offs = new short[MAXBITS + 2];
            for (int len = 1; len < MAXBITS; len++) offs[len + 1] = (short) (offs[len] + count[len]);
            for (int s = 0; s < sym; s++) {
                if (length[s] != 0) symbol[offs[length[s]]++] = (short) s;
            }
        }
    }

    private static final class In {
        final byte[] b;
        int pos;
        final int end;
        int cur;
        int cnt;

        In(byte[] b, int off, int len) {
            this.b = b;
            this.pos = off;
            this.end = off + len;
        }

        int bit() throws IOException {
            if (cnt == 0) {
                if (pos >= end) throw new IOException("PKWARE: неожиданный конец потока");
                cur = b[pos++] & 0xFF;
                cnt = 8;
            }
            int r = cur & 1;
            cur >>>= 1;
            cnt--;
            return r;
        }

        int bits(int n) throws IOException {
            int v = 0;
            for (int i = 0; i < n; i++) v |= bit() << i;
            return v;
        }

        int decode(Huff h) throws IOException {
            int code = 0, first = 0, index = 0;
            for (int len = 1; len <= MAXBITS; len++) {
                code |= bit() ^ 1;
                int count = h.count[len];
                if (code < first + count) return h.symbol[index + (code - first)];
                index += count;
                first += count;
                first <<= 1;
                code <<= 1;
            }
            throw new IOException("PKWARE: неверный код Хаффмана");
        }
    }

    public static byte[] explode(byte[] in, int off, int len, int expected) throws IOException {
        In s = new In(in, off, len);
        int lit = s.bits(8);
        int dict = s.bits(8);
        if (lit > 1) throw new IOException("PKWARE: плохой заголовок (lit)");
        if (dict < 4 || dict > 6) throw new IOException("PKWARE: плохой заголовок (dict)");
        byte[] out = new byte[expected];
        int o = 0;
        while (true) {
            if (s.bit() == 1) {
                int sym = s.decode(LEN);
                int l = BASE[sym] + s.bits(EXTRA[sym]);
                if (l == 519) break;
                int shift = (l == 2) ? 2 : dict;
                int dist = (s.decode(DIST) << shift) + s.bits(shift) + 1;
                if (dist > o) throw new IOException("PKWARE: расстояние за началом буфера");
                if (o + l > expected) throw new IOException("PKWARE: переполнение выхода");
                for (int k = 0; k < l; k++, o++) out[o] = out[o - dist];
            } else {
                int sym = (lit == 1) ? s.decode(LIT) : s.bits(8);
                if (o >= expected) throw new IOException("PKWARE: переполнение выхода");
                out[o++] = (byte) sym;
            }
        }
        if (o != expected) throw new IOException("PKWARE: размер " + o + " вместо " + expected);
        return out;
    }
}
