package com.jv.diablo2.gfx;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Декодер DT1 (тайлы локаций, версия 7.6). Изометрические блоки (полы) и RLE-блоки (стены, крыши)
 * собираются в одно изображение; imgX/imgY — смещение изображения от якоря плитки.
 * ЭКСПЕРИМЕНТАЛЬНО: сверено с описанием формата, но не с реальными файлами игры.
 */
public final class Dt1 {
    public static final class Tile {
        public int direction, roofHeight, height, width, orientation, mainIndex, subIndex, rarity;
        public int imgX, imgY, imgW, imgH;
        public byte[] pixels;
        public byte[] subtileFlags = new byte[25];   // 5x5, индекс y*5+x
    }

    public final List<Tile> tiles = new ArrayList<>();

    private static final int[] XJUMP = {14, 12, 10, 8, 6, 4, 2, 0, 2, 4, 6, 8, 10, 12, 14};
    private static final int[] NBPIX = {4, 8, 12, 16, 20, 24, 28, 32, 28, 24, 20, 16, 12, 8, 4};

    public static Dt1 parse(byte[] d) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        if (d.length < 276 || b.getInt(0) != 7 || b.getInt(4) != 6) throw new IOException("DT1: неверная версия (нужна 7.6)");
        int n = b.getInt(268);
        int ptr = b.getInt(272);
        if (n < 0 || n > 20000 || ptr < 0 || ptr + n * 96L > d.length) throw new IOException("DT1: битый заголовок");
        Dt1 dt = new Dt1();
        for (int i = 0; i < n; i++) {
            int p = ptr + i * 96;
            Tile t = new Tile();
            t.direction = b.getInt(p);
            t.roofHeight = b.getShort(p + 4);
            t.height = b.getInt(p + 8);
            t.width = b.getInt(p + 12);
            t.orientation = b.getInt(p + 20);
            t.mainIndex = b.getInt(p + 24);
            t.subIndex = b.getInt(p + 28);
            t.rarity = b.getInt(p + 32);
            System.arraycopy(d, p + 40, t.subtileFlags, 0, 25);
            int bp = b.getInt(p + 72);
            int nb = b.getInt(p + 80);
            buildImage(d, b, t, bp, nb);
            dt.tiles.add(t);
        }
        return dt;
    }

    private static void buildImage(byte[] d, ByteBuffer b, Tile t, int bp, int nb) throws IOException {
        if (nb < 0 || nb > 400 || bp < 0 || bp + nb * 20L > d.length) throw new IOException("DT1: битые блоки");
        int[] bx = new int[nb], by = new int[nb], bw = new int[nb], bh = new int[nb];
        byte[][] data = new byte[nb][];
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int j = 0; j < nb; j++) {
            int h = bp + j * 20;
            bx[j] = b.getShort(h);
            by[j] = b.getShort(h + 2);
            int format = b.getShort(h + 8);
            int len = b.getInt(h + 10);
            int off = bp + b.getInt(h + 16);
            if (len < 0 || off < 0 || off + len > d.length) throw new IOException("DT1: блок за границей файла");
            if (format == 1) {
                data[j] = decodeIso(d, off, len);
                bw[j] = 32;
                bh[j] = 15;
            } else {
                int[] rows = new int[1];
                data[j] = decodeRle(d, off, len, rows);
                bw[j] = 32;
                bh[j] = rows[0];
            }
            minX = Math.min(minX, bx[j]);
            minY = Math.min(minY, by[j]);
            maxX = Math.max(maxX, bx[j] + bw[j]);
            maxY = Math.max(maxY, by[j] + bh[j]);
        }
        if (nb == 0) {
            t.pixels = new byte[0];
            return;
        }
        t.imgX = minX;
        t.imgY = minY;
        t.imgW = maxX - minX;
        t.imgH = maxY - minY;
        t.pixels = new byte[t.imgW * t.imgH];
        for (int j = 0; j < nb; j++) {
            for (int y = 0; y < bh[j]; y++) {
                for (int x = 0; x < bw[j]; x++) {
                    byte v = data[j][y * 32 + x];
                    if (v != 0) t.pixels[(by[j] - minY + y) * t.imgW + (bx[j] - minX + x)] = v;
                }
            }
        }
    }

    static byte[] decodeIso(byte[] d, int off, int len) {
        byte[] out = new byte[32 * 15];
        int p = off, end = off + len;
        for (int y = 0; y < 15; y++) {
            for (int k = 0; k < NBPIX[y] && p < end; k++) out[y * 32 + XJUMP[y] + k] = d[p++];
        }
        return out;
    }

    static byte[] decodeRle(byte[] d, int off, int len, int[] rowsOut) {
        byte[] buf = new byte[32 * 64];
        int x = 0, y = 0, i = off, end = off + len, maxY = 0;
        while (i + 1 < end) {
            int skip = d[i++] & 0xFF, cnt = d[i++] & 0xFF;
            if (skip == 0 && cnt == 0) {
                x = 0;
                y++;
                continue;
            }
            x += skip;
            for (int k = 0; k < cnt && i < end; k++, i++, x++) {
                if (x < 32 && y < 64) buf[y * 32 + x] = d[i];
            }
            if (y > maxY) maxY = y;
        }
        rowsOut[0] = Math.min(64, maxY + 1);
        return buf;
    }
}
