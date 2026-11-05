package com.jv.diablo2.gfx;

import java.io.IOException;

/**
 * Декодер DCC (анимации персонажей, монстров, объектов). Алгоритм Paul Siramy.
 * Пиксели — индексы палитры, 0 = прозрачно. Все направления декодируются лениво.
 */
public final class Dcc {
    public static final class Frame {
        public int width, height;   // размер кадра
        public int offsetX, offsetY; // левый верхний угол кадра относительно точки привязки
        public byte[] pixels;       // width*height, сверху вниз
    }

    public final int directions, framesPerDirection;
    private final byte[] data;
    private final int[] dirOffset;
    private final Frame[][] decoded;

    private static final int[] BITS = {0, 1, 2, 4, 6, 8, 10, 12, 14, 16, 20, 24, 26, 28, 30, 32};
    private static final int[] NUM_PIX = {0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4};

    /** Битовый поток: младшие биты первыми. */
    private static final class Bits {
        final byte[] d; long pos; final long end; // в битах, абсолютные
        Bits(byte[] d, long start, long end) { this.d = d; this.pos = start; this.end = end; }
        int read(int n) throws IOException {
            if (n == 0) return 0;
            if (pos + n > end) throw new IOException("DCC: конец потока");
            long v = 0;
            for (int i = 0; i < n; i++) {
                long p = pos + i;
                if ((d[(int) (p >> 3)] >> (p & 7) & 1) != 0) v |= 1L << i;
            }
            pos += n;
            return (int) v;
        }
        int readSigned(int n) throws IOException {
            int v = read(n);
            if (n > 0 && n < 32 && (v & (1 << (n - 1))) != 0) v |= ~((1 << n) - 1);
            return v;
        }
    }

    private Dcc(byte[] data, int dirs, int fpd, int[] offs) {
        this.data = data; this.directions = dirs; this.framesPerDirection = fpd; this.dirOffset = offs;
        this.decoded = new Frame[dirs][];
    }

    public static Dcc parse(byte[] d) throws IOException {
        if (d.length < 15 || (d[0] & 0xFF) != 0x74) throw new IOException("DCC: неверная сигнатура");
        int dirs = d[2] & 0xFF;
        int fpd = le32(d, 3);
        if (dirs <= 0 || dirs > 32 || fpd <= 0 || fpd > 256 || 15 + dirs * 4 > d.length) throw new IOException("DCC: битый заголовок");
        int[] offs = new int[dirs];
        for (int i = 0; i < dirs; i++) {
            offs[i] = le32(d, 15 + i * 4);
            if (offs[i] < 0 || offs[i] >= d.length) throw new IOException("DCC: битое смещение направления");
        }
        return new Dcc(d, dirs, fpd, offs);
    }

    private static int le32(byte[] d, int p) {
        return (d[p] & 0xFF) | (d[p + 1] & 0xFF) << 8 | (d[p + 2] & 0xFF) << 16 | (d[p + 3] & 0xFF) << 24;
    }

    public synchronized Frame[] direction(int dir) throws IOException {
        if (decoded[dir] == null) decoded[dir] = decodeDirection(dir);
        return decoded[dir];
    }

    private static final class FrameInfo {
        int var0, w, h, xo, yo, optBytes, codedBytes, flipped;
        int minX, maxX, minY, maxY, boxW, boxH;
        int cw, ch;          // число ячеек
        int[] cx0, cy0, cwid, chei;
    }

    private Frame[] decodeDirection(int dir) throws IOException {
        final int fpd = framesPerDirection;
        long start = (long) dirOffset[dir] * 8;
        long end = (dir == directions - 1 ? (long) data.length : (long) dirOffset[dir + 1]) * 8;
        Bits bs = new Bits(data, start, end);

        bs.read(32); // outsize coded
        int comp = bs.read(2);
        int var0Bits = BITS[bs.read(4)], wBits = BITS[bs.read(4)], hBits = BITS[bs.read(4)];
        int xBits = BITS[bs.read(4)], yBits = BITS[bs.read(4)], optBits = BITS[bs.read(4)], codBits = BITS[bs.read(4)];

        FrameInfo[] fr = new FrameInfo[fpd];
        int dMinX = Integer.MAX_VALUE, dMinY = Integer.MAX_VALUE, dMaxX = Integer.MIN_VALUE, dMaxY = Integer.MIN_VALUE;
        for (int f = 0; f < fpd; f++) {
            FrameInfo fi = new FrameInfo();
            fi.var0 = bs.read(var0Bits);
            fi.w = bs.read(wBits);
            fi.h = bs.read(hBits);
            fi.xo = bs.readSigned(xBits);
            fi.yo = bs.readSigned(yBits);
            fi.optBytes = bs.read(optBits);
            fi.codedBytes = bs.read(codBits);
            fi.flipped = bs.read(1);
            fi.minX = fi.xo;
            fi.maxX = fi.minX + fi.w - 1;
            if (fi.flipped != 0) { fi.minY = fi.yo; fi.maxY = fi.minY + fi.h - 1; }
            else { fi.maxY = fi.yo; fi.minY = fi.maxY - fi.h + 1; }
            fi.boxW = fi.maxX - fi.minX + 1;
            fi.boxH = fi.maxY - fi.minY + 1;
            if (fi.w <= 0 || fi.h <= 0 || fi.w > 2048 || fi.h > 2048) throw new IOException("DCC: странный размер кадра " + fi.w + "x" + fi.h);
            fr[f] = fi;
            dMinX = Math.min(dMinX, fi.minX); dMinY = Math.min(dMinY, fi.minY);
            dMaxX = Math.max(dMaxX, fi.maxX); dMaxY = Math.max(dMaxY, fi.maxY);
        }
        final int dW = dMaxX - dMinX + 1, dH = dMaxY - dMinY + 1;

        // необязательные байты — пропускаем
        long optTotal = 0;
        for (FrameInfo fi : fr) optTotal += fi.optBytes;
        if (optTotal > 0) {
            if ((bs.pos & 7) != 0) bs.pos += 8 - (bs.pos & 7);
            bs.pos += optTotal * 8;
        }

        int eqSize = 0, maskSize, encSize = 0, rawSize = 0;
        if ((comp & 2) != 0) eqSize = bs.read(20);
        maskSize = bs.read(20);
        if ((comp & 1) != 0) { encSize = bs.read(20); rawSize = bs.read(20); }

        int[] palKey = new int[256];
        int np = 0;
        for (int i = 0; i < 256; i++) if (bs.read(1) != 0) palKey[np++] = i;

        long g = bs.pos;
        Bits eq = null, mask, enc = null, raw = null, code;
        if ((comp & 2) != 0) { eq = new Bits(data, g, g + eqSize); g += eqSize; }
        mask = new Bits(data, g, g + maskSize); g += maskSize;
        if ((comp & 1) != 0) {
            enc = new Bits(data, g, g + encSize); g += encSize;
            raw = new Bits(data, g, g + rawSize); g += rawSize;
        }
        code = new Bits(data, g, end);

        // сетка ячеек направления (4x4)
        final int gW = 1 + (dW - 1) / 4, gH = 1 + (dH - 1) / 4;

        // ячейки кадров
        for (FrameInfo fi : fr) {
            int w = 4 - ((fi.minX - dMinX) % 4);
            int nw;
            if (fi.boxW - w <= 1) nw = 1; else { int t = fi.boxW - w - 1; nw = 2 + t / 4; if (t % 4 == 0) nw--; }
            int h = 4 - ((fi.minY - dMinY) % 4);
            int nh;
            if (fi.boxH - h <= 1) nh = 1; else { int t = fi.boxH - h - 1; nh = 2 + t / 4; if (t % 4 == 0) nh--; }
            int[] cwid = new int[nw], chei = new int[nh];
            if (nw == 1) cwid[0] = fi.boxW; else { cwid[0] = w; for (int i = 1; i < nw - 1; i++) cwid[i] = 4; cwid[nw - 1] = fi.boxW - w - 4 * (nw - 2); }
            if (nh == 1) chei[0] = fi.boxH; else { chei[0] = h; for (int i = 1; i < nh - 1; i++) chei[i] = 4; chei[nh - 1] = fi.boxH - h - 4 * (nh - 2); }
            fi.cw = nw; fi.ch = nh;
            fi.cx0 = new int[nw * nh]; fi.cy0 = new int[nw * nh]; fi.cwid = new int[nw * nh]; fi.chei = new int[nw * nh];
            int y0 = fi.minY - dMinY;
            for (int y = 0; y < nh; y++) {
                int x0 = fi.minX - dMinX;
                for (int x = 0; x < nw; x++) {
                    int c = x + y * nw;
                    fi.cx0[c] = x0; fi.cy0[c] = y0; fi.cwid[c] = cwid[x]; fi.chei[c] = chei[y];
                    x0 += cwid[x];
                }
                y0 += chei[y];
            }
        }

        // --- заполнение буфера пикселей (до 4 цветов на ячейку) ---
        int cap = 1 << 16, pbn = 0;
        int[] pbVal = new int[cap * 4], pbFrame = new int[cap], pbCell = new int[cap];
        int[] cellBuf = new int[gW * gH];
        java.util.Arrays.fill(cellBuf, -1);
        int[] rp = new int[4];
        for (int f = 0; f < fpd; f++) {
            FrameInfo fi = fr[f];
            int cell0X = (fi.minX - dMinX) / 4, cell0Y = (fi.minY - dMinY) / 4;
            for (int y = 0; y < fi.ch; y++) {
                int cy = cell0Y + y;
                for (int x = 0; x < fi.cw; x++) {
                    int cx = cell0X + x;
                    int cc = cx + cy * gW;
                    if (cc >= gW * gH) throw new IOException("DCC: ячейка за пределами сетки");
                    boolean next = false;
                    int pm;
                    if (cellBuf[cc] != -1) {
                        int t = (eqSize > 0) ? eq.read(1) : 0;
                        if (t == 0) pm = mask.read(4); else { next = true; pm = 0; }
                    } else pm = 0x0F;
                    if (next) continue;
                    rp[0] = rp[1] = rp[2] = rp[3] = 0;
                    int last = 0, n = NUM_PIX[pm];
                    int et = (n > 0 && encSize > 0) ? enc.read(1) : 0;
                    int decoded = 0;
                    for (int i = 0; i < n; i++) {
                        if (et != 0) rp[i] = raw.read(8);
                        else {
                            rp[i] = last;
                            int disp;
                            do { disp = code.read(4); rp[i] += disp; } while (disp == 15);
                        }
                        if (rp[i] == last) { rp[i] = 0; i = n; }
                        else { last = rp[i]; decoded++; }
                    }
                    if (pbn >= cap) {
                        cap *= 2;
                        pbVal = java.util.Arrays.copyOf(pbVal, cap * 4);
                        pbFrame = java.util.Arrays.copyOf(pbFrame, cap);
                        pbCell = java.util.Arrays.copyOf(pbCell, cap);
                    }
                    int old = cellBuf[cc];
                    int idx = decoded - 1;
                    for (int i = 0; i < 4; i++) {
                        if ((pm & (1 << i)) != 0) pbVal[pbn * 4 + i] = (idx >= 0) ? rp[idx--] & 0xFF : 0;
                        else pbVal[pbn * 4 + i] = pbVal[old * 4 + i];
                    }
                    cellBuf[cc] = pbn;
                    pbFrame[pbn] = f;
                    pbCell[pbn] = x + y * fi.cw;
                    pbn++;
                }
            }
        }
        for (int i = 0; i < pbn * 4; i++) pbVal[i] = palKey[pbVal[i] & 0xFF];

        // --- сборка кадров ---
        byte[] dbmp = new byte[dW * dH]; // общий холст направления
        int[] lastW = new int[gW * gH], lastH = new int[gW * gH], lastX = new int[gW * gH], lastY = new int[gW * gH];
        java.util.Arrays.fill(lastW, -1); java.util.Arrays.fill(lastH, -1);
        Frame[] out = new Frame[fpd];
        int pe = 0;
        for (int f = 0; f < fpd; f++) {
            FrameInfo fi = fr[f];
            byte[] fb = new byte[dW * dH];
            int nc = fi.cw * fi.ch;
            for (int c = 0; c < nc; c++) {
                int cx0 = fi.cx0[c], cy0 = fi.cy0[c], cw = fi.cwid[c], ch = fi.chei[c];
                int bi = (cx0 / 4) + (cy0 / 4) * gW;
                boolean isNew = pe < pbn && pbFrame[pe] == f && pbCell[pe] == c;
                if (!isNew) {
                    if (cw != lastW[bi] || ch != lastH[bi]) {
                        clear(dbmp, dW, cx0, cy0, cw, ch, 0);
                    } else {
                        copy(dbmp, dW, cx0, cy0, lastX[bi], lastY[bi], cw, ch);
                        copy2(fb, dbmp, dW, cx0, cy0, cw, ch);
                    }
                } else {
                    int v0 = pbVal[pe * 4], v1 = pbVal[pe * 4 + 1], v2 = pbVal[pe * 4 + 2];
                    if (v0 == v1) clear(dbmp, dW, cx0, cy0, cw, ch, v0);
                    else {
                        int nb = (v1 == v2) ? 1 : 2;
                        for (int y = 0; y < ch; y++)
                            for (int x = 0; x < cw; x++) {
                                int p = code.read(nb);
                                dbmp[(cy0 + y) * dW + cx0 + x] = (byte) pbVal[pe * 4 + p];
                            }
                    }
                    copy2(fb, dbmp, dW, cx0, cy0, cw, ch);
                    pe++;
                }
                lastW[bi] = cw; lastH[bi] = ch; lastX[bi] = cx0; lastY[bi] = cy0;
            }
            // вырезаем рамку кадра из холста направления
            Frame o = new Frame();
            o.width = fi.boxW; o.height = fi.boxH; o.offsetX = fi.minX; o.offsetY = fi.minY;
            o.pixels = new byte[o.width * o.height];
            int ox = fi.minX - dMinX, oy = fi.minY - dMinY;
            for (int y = 0; y < o.height; y++)
                System.arraycopy(fb, (oy + y) * dW + ox, o.pixels, y * o.width, o.width);
            out[f] = o;
        }
        return out;
    }

    private static void clear(byte[] b, int w, int x0, int y0, int cw, int ch, int v) {
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) b[(y0 + y) * w + x0 + x] = (byte) v;
    }
    private static void copy(byte[] b, int w, int dx, int dy, int sx, int sy, int cw, int ch) {
        byte[] t = new byte[cw * ch];
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) t[y * cw + x] = b[(sy + y) * w + sx + x];
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) b[(dy + y) * w + dx + x] = t[y * cw + x];
    }
    private static void copy2(byte[] dst, byte[] src, int w, int x0, int y0, int cw, int ch) {
        for (int y = 0; y < ch; y++) System.arraycopy(src, (y0 + y) * w + x0, dst, (y0 + y) * w + x0, cw);
    }
}
