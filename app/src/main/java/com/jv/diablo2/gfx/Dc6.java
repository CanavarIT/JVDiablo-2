package com.jv.diablo2.gfx;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Декодер DC6 (интерфейс, предметы, часть эффектов). Пиксели — индексы палитры, 0 = прозрачно. */
public final class Dc6 {
    public static final class Frame {
        public int width, height, offsetX, offsetY;
        public byte[] pixels;

        public int[] toArgb(Palette pal) {
            int[] out = new int[width * height];
            for (int i = 0; i < out.length; i++) out[i] = pal.argb[pixels[i] & 0xFF];
            return out;
        }
    }

    public final int directions;
    public final int framesPerDirection;
    public final Frame[] frames;

    private Dc6(int directions, int framesPerDirection, Frame[] frames) {
        this.directions = directions;
        this.framesPerDirection = framesPerDirection;
        this.frames = frames;
    }

    public Frame frame(int dir, int index) {
        return frames[dir * framesPerDirection + index];
    }

    public static Dc6 parse(byte[] data) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data.length < 24 || b.getInt(0) != 6) throw new IOException("DC6: неверная версия");
        int dirs = b.getInt(16);
        int fpd = b.getInt(20);
        int total = dirs * fpd;
        if (dirs <= 0 || fpd <= 0 || total > 100000 || 24 + total * 4 > data.length) throw new IOException("DC6: битый заголовок");
        Frame[] frames = new Frame[total];
        for (int f = 0; f < total; f++) {
            int p = b.getInt(24 + f * 4);
            if (p < 0 || p + 32 > data.length) throw new IOException("DC6: битый указатель кадра");
            Frame fr = new Frame();
            fr.width = b.getInt(p + 4);
            fr.height = b.getInt(p + 8);
            fr.offsetX = b.getInt(p + 12);
            fr.offsetY = b.getInt(p + 16);
            int length = b.getInt(p + 28);
            if (fr.width < 0 || fr.height < 0 || fr.width > 4096 || fr.height > 4096) throw new IOException("DC6: странный размер кадра");
            int start = p + 32;
            if (length < 0 || start + length > data.length) throw new IOException("DC6: битая длина кадра");
            fr.pixels = new byte[fr.width * fr.height];
            decodeFrame(data, start, length, fr);
            frames[f] = fr;
        }
        return new Dc6(dirs, fpd, frames);
    }

    /** RLE: 0x80 — конец строки (строки идут снизу вверх); b>0x80 — пропустить b&0x7F; иначе b пикселей подряд. */
    private static void decodeFrame(byte[] d, int start, int length, Frame fr) {
        int x = 0, y = fr.height - 1;
        int i = start, end = start + length;
        while (i < end) {
            int c = d[i++] & 0xFF;
            if (c == 0x80) {
                x = 0;
                y--;
            } else if ((c & 0x80) != 0) {
                x += c & 0x7F;
            } else {
                for (int k = 0; k < c && i < end; k++, i++, x++) {
                    if (y >= 0 && x < fr.width) fr.pixels[y * fr.width + x] = d[i];
                }
            }
        }
    }
}
