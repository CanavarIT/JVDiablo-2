package com.jv.diablo2.render;

/**
 * Кадровый буфер игры: пиксели ARGB, логический размер 640x480 (как в игре) или 800x600 (меню).
 * Все спрайты Diablo 2 хранятся как индексы палитры; здесь они переводятся в цвет при отрисовке.
 * Чистая Java без Android: тот же код проверяется на JVM, а на устройстве буфер просто выводится на экран.
 */
public final class Fb {
    public static final int NORMAL = 0, SHADOW = 1, ADD = 2, ALPHA = 3, MULTIPLY = 4;

    public final int w, h;
    public final int[] px;

    public Fb(int w, int h) {
        this.w = w;
        this.h = h;
        this.px = new int[w * h];
    }

    public void clear(int argb) {
        java.util.Arrays.fill(px, argb);
    }

    /**
     * Рисует кадр из индексов палитры. dx, dy — левый верхний угол на экране. pal[0] считается прозрачным.
     * shift — необязательная таблица замены индексов (цветовые варианты монстров/предметов).
     * alpha 0..255 используется режимами ALPHA и ADD.
     */
    public void blit(byte[] idx, int sw, int sh, int dx, int dy, int[] pal, byte[] shift, int mode, int alpha) {
        int x0 = Math.max(0, -dx), x1 = Math.min(sw, w - dx);
        int y0 = Math.max(0, -dy), y1 = Math.min(sh, h - dy);
        if (x0 >= x1 || y0 >= y1) return;
        for (int y = y0; y < y1; y++) {
            int si = y * sw + x0;
            int di = (dy + y) * w + dx + x0;
            for (int x = x0; x < x1; x++, si++, di++) {
                int v = idx[si] & 0xFF;
                if (v == 0) continue;
                if (shift != null) v = shift[v] & 0xFF;
                int c = pal[v];
                switch (mode) {
                    case NORMAL:
                        px[di] = c;
                        break;
                    case SHADOW: {
                        int d = px[di];
                        // тень: затемнение подложки примерно вдвое
                        px[di] = 0xFF000000 | (((d >> 16 & 0xFF) * 140 >> 8) << 16) | (((d >> 8 & 0xFF) * 140 >> 8) << 8) | ((d & 0xFF) * 140 >> 8);
                        break;
                    }
                    case ADD: {
                        int d = px[di];
                        int r = Math.min(255, (d >> 16 & 0xFF) + ((c >> 16 & 0xFF) * alpha >> 8));
                        int g = Math.min(255, (d >> 8 & 0xFF) + ((c >> 8 & 0xFF) * alpha >> 8));
                        int b = Math.min(255, (d & 0xFF) + ((c & 0xFF) * alpha >> 8));
                        px[di] = 0xFF000000 | r << 16 | g << 8 | b;
                        break;
                    }
                    case ALPHA: {
                        int d = px[di], ia = 255 - alpha;
                        int r = ((c >> 16 & 0xFF) * alpha + (d >> 16 & 0xFF) * ia) >> 8;
                        int g = ((c >> 8 & 0xFF) * alpha + (d >> 8 & 0xFF) * ia) >> 8;
                        int b = ((c & 0xFF) * alpha + (d & 0xFF) * ia) >> 8;
                        px[di] = 0xFF000000 | r << 16 | g << 8 | b;
                        break;
                    }
                    default: {   // MULTIPLY: затемнение/тонирование по цвету палитры
                        int d = px[di];
                        int r = (d >> 16 & 0xFF) * (c >> 16 & 0xFF) >> 8;
                        int g = (d >> 8 & 0xFF) * (c >> 8 & 0xFF) >> 8;
                        int b = (d & 0xFF) * (c & 0xFF) >> 8;
                        px[di] = 0xFF000000 | r << 16 | g << 8 | b;
                    }
                }
            }
        }
    }

    public void blit(byte[] idx, int sw, int sh, int dx, int dy, int[] pal) {
        blit(idx, sw, sh, dx, dy, pal, null, NORMAL, 255);
    }

    /** Рисует индексный кадр, умножая цвет на tint (для текста и подсветки). */
    public void blitTint(byte[] idx, int sw, int sh, int dx, int dy, int[] pal, int tint) {
        int tr = tint >> 16 & 0xFF, tg = tint >> 8 & 0xFF, tb = tint & 0xFF;
        int x0 = Math.max(0, -dx), x1 = Math.min(sw, w - dx);
        int y0 = Math.max(0, -dy), y1 = Math.min(sh, h - dy);
        for (int y = y0; y < y1; y++) {
            int si = y * sw + x0, di = (dy + y) * w + dx + x0;
            for (int x = x0; x < x1; x++, si++, di++) {
                int v = idx[si] & 0xFF;
                if (v == 0) continue;
                int c = pal[v];
                px[di] = 0xFF000000 | ((c >> 16 & 0xFF) * tr / 255) << 16 | ((c >> 8 & 0xFF) * tg / 255) << 8 | ((c & 0xFF) * tb / 255);
            }
        }
    }

    // ---- тень юнита: спрайт проецируется на землю (сплющивается и наклоняется влево-вниз), накладывается один раз ----
    private static final int SH_W = 320, SH_H = 96, SH_OU = 192, SH_OV = 16;
    private final boolean[] shMask = new boolean[SH_W * SH_H];

    public void shadowBegin() {
        java.util.Arrays.fill(shMask, false);
    }

    /** Добавляет кадр в маску тени. fx, fy — точка ног юнита на экране; dx, dy — левый верхний угол кадра. */
    public void shadowAdd(byte[] idx, int sw, int sh, int dx, int dy, int fx, int fy) {
        for (int y = 0; y < sh; y++) {
            int rel = dy + y - fy;          // < 0 выше ног
            float hgt = -rel;
            if (hgt < -8) continue;
            int v = Math.round(hgt * 0.30f);
            int vv = v + SH_OV;
            if (vv < 0 || vv >= SH_H) continue;
            for (int x = 0; x < sw; x++) {
                if (idx[y * sw + x] == 0) continue;
                int u = Math.round(dx + x - fx - hgt * 0.60f);
                int uu = u + SH_OU;
                if (uu >= 0 && uu < SH_W) shMask[vv * SH_W + uu] = true;
            }
        }
    }

    public void shadowEnd(int fx, int fy) {
        for (int vv = 0; vv < SH_H; vv++) {
            int Y = fy + vv - SH_OV;
            if (Y < 0 || Y >= h) continue;
            for (int uu = 0; uu < SH_W; uu++) {
                if (!shMask[vv * SH_W + uu]) continue;
                int X = fx + uu - SH_OU;
                if (X < 0 || X >= w) continue;
                int d = px[Y * w + X];
                px[Y * w + X] = 0xFF000000 | (((d >> 16 & 0xFF) * 120 >> 8) << 16) | (((d >> 8 & 0xFF) * 120 >> 8) << 8) | ((d & 0xFF) * 120 >> 8);
            }
        }
    }

    public void fillRect(int x, int y, int rw, int rh, int argb) {
        int x0 = Math.max(0, x), x1 = Math.min(w, x + rw), y0 = Math.max(0, y), y1 = Math.min(h, y + rh);
        int a = argb >>> 24;
        for (int yy = y0; yy < y1; yy++) {
            for (int xx = x0; xx < x1; xx++) {
                int i = yy * w + xx;
                if (a == 255) {
                    px[i] = argb;
                } else {
                    int d = px[i];
                    int r = ((argb >> 16 & 0xFF) * a + (d >> 16 & 0xFF) * (255 - a)) / 255;
                    int g = ((argb >> 8 & 0xFF) * a + (d >> 8 & 0xFF) * (255 - a)) / 255;
                    int b = ((argb & 0xFF) * a + (d & 0xFF) * (255 - a)) / 255;
                    px[i] = 0xFF000000 | r << 16 | g << 8 | b;
                }
            }
        }
    }

    /** Затемняет прямоугольник (0..255, 255 = чёрный). */
    public void darken(int x, int y, int rw, int rh, int amount) {
        fillRect(x, y, rw, rh, amount << 24);
    }
}
