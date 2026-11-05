package com.jv.diablo2.gfx;

/** Палитра на 256 цветов. Файл Pal.dat хранит цвета в порядке B,G,R. Индекс 0 — прозрачный. */
public final class Palette {
    public final int[] argb = new int[256];

    public static Palette fromDat(byte[] d) {
        if (d.length < 768) throw new IllegalArgumentException("Pal.dat должен быть >= 768 байт");
        Palette p = new Palette();
        for (int i = 0; i < 256; i++) {
            int b = d[i * 3] & 0xFF;
            int g = d[i * 3 + 1] & 0xFF;
            int r = d[i * 3 + 2] & 0xFF;
            p.argb[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        p.argb[0] = 0;
        return p;
    }

    public static Palette grayscale() {
        Palette p = new Palette();
        for (int i = 0; i < 256; i++) p.argb[i] = 0xFF000000 | (i << 16) | (i << 8) | i;
        p.argb[0] = 0;
        return p;
    }
}
