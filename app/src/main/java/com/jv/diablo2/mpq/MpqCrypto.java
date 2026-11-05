package com.jv.diablo2.mpq;

/** Таблица шифрования, хеш имён и (де)шифровка блоков MPQ. */
public final class MpqCrypto {
    private MpqCrypto() {
    }

    public static final int HASH_TABLE_OFFSET = 0;
    public static final int HASH_NAME_A = 1;
    public static final int HASH_NAME_B = 2;
    public static final int HASH_FILE_KEY = 3;

    private static final int[] T = new int[0x500];

    static {
        long seed = 0x00100001L;
        for (int i1 = 0; i1 < 0x100; i1++) {
            int i2 = i1;
            for (int i = 0; i < 5; i++, i2 += 0x100) {
                seed = (seed * 125 + 3) % 0x2AAAABL;
                int t1 = (int) (seed & 0xFFFF) << 16;
                seed = (seed * 125 + 3) % 0x2AAAABL;
                int t2 = (int) (seed & 0xFFFF);
                T[i2] = t1 | t2;
            }
        }
    }

    public static int hash(String s, int type) {
        long seed1 = 0x7FED7FEDL;
        long seed2 = 0xEEEEEEEEL;
        for (int i = 0; i < s.length(); i++) {
            int ch = Character.toUpperCase(s.charAt(i));
            if (ch == '/') ch = '\\';
            long v = T[(type << 8) + (ch & 0xFF)] & 0xFFFFFFFFL;
            seed1 = (v ^ ((seed1 + seed2) & 0xFFFFFFFFL)) & 0xFFFFFFFFL;
            seed2 = (ch + seed1 + seed2 + (seed2 << 5) + 3) & 0xFFFFFFFFL;
        }
        return (int) seed1;
    }

    /** Расшифровывает len/4 слов (little-endian) прямо в буфере. */
    public static void decrypt(byte[] b, int off, int len, int key) {
        int seed = 0xEEEEEEEE;
        int n = len >>> 2;
        for (int i = 0; i < n; i++) {
            int p = off + i * 4;
            int ch = (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8) | ((b[p + 2] & 0xFF) << 16) | ((b[p + 3] & 0xFF) << 24);
            seed += T[0x400 + (key & 0xFF)];
            ch ^= (key + seed);
            key = ((~key << 21) + 0x11111111) | (key >>> 11);
            seed = ch + seed + (seed << 5) + 3;
            b[p] = (byte) ch;
            b[p + 1] = (byte) (ch >>> 8);
            b[p + 2] = (byte) (ch >>> 16);
            b[p + 3] = (byte) (ch >>> 24);
        }
    }

    /** Обратная операция (нужна тестам и будущему редактору сейвов). */
    public static void encrypt(byte[] b, int off, int len, int key) {
        int seed = 0xEEEEEEEE;
        int n = len >>> 2;
        for (int i = 0; i < n; i++) {
            int p = off + i * 4;
            int plain = (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8) | ((b[p + 2] & 0xFF) << 16) | ((b[p + 3] & 0xFF) << 24);
            seed += T[0x400 + (key & 0xFF)];
            int ch = plain ^ (key + seed);
            key = ((~key << 21) + 0x11111111) | (key >>> 11);
            seed = plain + seed + (seed << 5) + 3;
            b[p] = (byte) ch;
            b[p + 1] = (byte) (ch >>> 8);
            b[p + 2] = (byte) (ch >>> 16);
            b[p + 3] = (byte) (ch >>> 24);
        }
    }
}
