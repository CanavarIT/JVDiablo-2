package com.jv.diablo2.gfx;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * Шрифты игры: пара файлов fontNN.dc6 (картинки символов) + fontNN.tbl (таблица символов, записи по 14 байт).
 * Начало записей определяется автоматически по проверке всей таблицы, поэтому точный размер заголовка не нужен.
 */
public final class D2Font {
    public static final class Glyph {
        public int code, width, height, frame;
    }

    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    public int maxHeight;

    public Glyph get(int code) {
        return glyphs.get(code);
    }

    public int size() {
        return glyphs.size();
    }

    public static D2Font parse(byte[] tbl, int frameCount) throws IOException {
        if (tbl.length < 20 || tbl[0] != 'W' || tbl[1] != 'o' || tbl[2] != 'o' || tbl[3] != '!') throw new IOException("TBL шрифта: нет сигнатуры Woo!");
        ByteBuffer b = ByteBuffer.wrap(tbl).order(ByteOrder.LITTLE_ENDIAN);
        for (int start = 8; start <= 40; start++) {
            if ((tbl.length - start) % 14 != 0) continue;
            int n = (tbl.length - start) / 14;
            if (n < 1 || n > 70000) continue;
            if (valid(b, start, n, frameCount)) return read(b, start, n);
        }
        throw new IOException("TBL шрифта: не удалось определить формат записей (" + tbl.length + " байт, кадров " + frameCount + ")");
    }

    private static boolean valid(ByteBuffer b, int start, int n, int frames) {
        for (int i = 0; i < n; i++) {
            int p = start + i * 14;
            int code = b.getShort(p) & 0xFFFF;
            int w = b.get(p + 3) & 0xFF, h = b.get(p + 4) & 0xFF;
            int frame = b.getShort(p + 8) & 0xFFFF;
            if (code == 0 || w > 200 || h > 200 || frame >= frames) return false;
        }
        return true;
    }

    private static D2Font read(ByteBuffer b, int start, int n) {
        D2Font f = new D2Font();
        for (int i = 0; i < n; i++) {
            int p = start + i * 14;
            Glyph g = new Glyph();
            g.code = b.getShort(p) & 0xFFFF;
            g.width = b.get(p + 3) & 0xFF;
            g.height = b.get(p + 4) & 0xFF;
            g.frame = b.getShort(p + 8) & 0xFFFF;
            f.glyphs.put(g.code, g);
            f.maxHeight = Math.max(f.maxHeight, g.height);
        }
        return f;
    }
}
