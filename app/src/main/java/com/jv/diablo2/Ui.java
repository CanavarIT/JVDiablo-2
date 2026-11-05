package com.jv.diablo2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.jv.diablo2.gfx.D2Font;
import com.jv.diablo2.gfx.Dc6;
import com.jv.diablo2.gfx.Palette;

/** Рисование в виртуальном экране 800x600: фоны из кадров DC6, текст шрифтами игры, кнопки. */
final class Ui {
    private Ui() {
    }

    static final int GOLD = 0xFFC8B478, WHITE = 0xFFFFFFFF, GRAY = 0xFF808080, RED = 0xFFC04040;

    static final class Btn {
        final float x, y, w, h;
        String label;
        final int font;
        boolean enabled = true, pressed;
        Runnable action;

        Btn(String label, float x, float y, float w, float h, int font, Runnable action) {
            this.label = label;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.font = font;
            this.action = action;
        }

        boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint BMP = new Paint();

    static {
        BMP.setFilterBitmap(false);
    }

    /** Фон из кадров DC6: кадры идут по строкам слева направо, строка заканчивается на ширине 800. */
    static void background(Canvas c, Dc6 d, Palette pal) {
        if (d == null) return;
        float x = 0, y = 0, rowH = 0;
        for (int i = 0; i < d.frames.length; i++) {
            Dc6.Frame f = d.frames[i];
            if (x >= 800) {
                x = 0;
                y += rowH;
                rowH = 0;
            }
            Bitmap b = Res.bitmap(d, pal, i);
            if (b != null) c.drawBitmap(b, x, y, BMP);
            x += f.width;
            rowH = Math.max(rowH, f.height);
        }
    }

    /** Кадр DC6 с привязкой «левый нижний угол + смещение» (так рисуются спрайты и логотип). */
    static void sprite(Canvas c, Dc6 d, Palette pal, int frame, float x, float y, boolean additive) {
        if (d == null) return;
        int idx = Math.floorMod(frame, d.frames.length);
        Bitmap b = Res.bitmap(d, pal, idx);
        if (b == null) return;
        Dc6.Frame f = d.frames[idx];
        if (additive) BMP.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        c.drawBitmap(b, x + f.offsetX, y + f.offsetY - f.height, BMP);
        if (additive) BMP.setXfermode(null);
    }

    // ---------- текст ----------

    private static D2Font font(int size) {
        return Res.font("data\\local\\font\\latin\\font" + size);
    }

    private static float fallbackSize(int size) {
        return size >= 42 ? 30 : size >= 30 ? 22 : size >= 24 ? 18 : 13;
    }

    static float measure(String s, int size) {
        D2Font f = font(size);
        if (f == null) {
            P.setTypeface(Typeface.SERIF);
            P.setTextSize(fallbackSize(size));
            return P.measureText(s);
        }
        float w = 0;
        for (int i = 0; i < s.length(); i++) {
            D2Font.Glyph g = f.get(s.charAt(i));
            w += g != null ? g.width : size / 3f;
        }
        return w;
    }

    /** y — верх строки. align: -1 слева, 0 по центру, 1 справа. */
    static void text(Canvas c, String s, float x, float y, int size, int color, int align) {
        float w = measure(s, size);
        float x0 = align == 0 ? x - w / 2 : align > 0 ? x - w : x;
        D2Font f = font(size);
        Dc6 d = f == null ? null : Res.dc6("data\\local\\font\\latin\\font" + size + ".dc6");
        if (f == null || d == null) {
            P.setStyle(Paint.Style.FILL);
            P.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
            P.setTextSize(fallbackSize(size));
            P.setTextAlign(Paint.Align.LEFT);
            P.setColorFilter(null);
            P.setColor(0xFF000000);
            c.drawText(s, x0 + 1, y + fallbackSize(size) + 1, P);
            P.setColor(color);
            c.drawText(s, x0, y + fallbackSize(size), P);
            return;
        }
        Palette pal = FrontEnd.fontPalette();
        BMP.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        float cx = x0;
        for (int i = 0; i < s.length(); i++) {
            D2Font.Glyph g = f.get(s.charAt(i));
            if (g == null) {
                cx += size / 3f;
                continue;
            }
            Bitmap b = Res.bitmap(d, pal, g.frame);
            if (b != null) c.drawBitmap(b, cx, y, BMP);
            cx += g.width;
        }
        BMP.setColorFilter(null);
    }

    static void button(Canvas c, Btn b) {
        Dc6 d = b.w > 200 ? FrontEnd.buttonWide() : FrontEnd.buttonMedium();
        Bitmap bmp = d == null ? null : Res.bitmap(d, FrontEnd.sky(), b.pressed ? 1 : 0);
        if (bmp != null) {
            RectF dst = new RectF(b.x, b.y, b.x + b.w, b.y + b.h);
            BMP.setAlpha(b.enabled ? 255 : 110);
            c.drawBitmap(bmp, null, dst, BMP);
            BMP.setAlpha(255);
        } else {
            P.setColorFilter(null);
            P.setStyle(Paint.Style.FILL);
            P.setColor(b.pressed ? 0xFF4A3A1A : (b.enabled ? 0xFF2A2214 : 0xFF181510));
            c.drawRoundRect(new RectF(b.x, b.y, b.x + b.w, b.y + b.h), 4, 4, P);
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(2);
            P.setColor(0xFF6A5A3A);
            c.drawRoundRect(new RectF(b.x, b.y, b.x + b.w, b.y + b.h), 4, 4, P);
        }
        int fs = b.font;
        while (fs > 16 && measure(b.label, fs) > b.w - 50) fs = fs >= 42 ? 30 : fs >= 30 ? 24 : 16;
        float th = fs >= 42 ? 30 : fs >= 30 ? 22 : fs >= 24 ? 18 : 13;
        text(c, b.label, b.x + b.w / 2f, b.y + (b.h - th) / 2f + (b.pressed ? 2 : 0), fs, b.enabled ? GOLD : GRAY, 0);
    }

    static void panel(Canvas c, float x, float y, float w, float h, boolean selected) {
        P.setColorFilter(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(selected ? 0x30C8B478 : 0x88000000);
        c.drawRoundRect(new RectF(x, y, x + w, y + h), 6, 6, P);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(selected ? 3 : 1.5f);
        P.setColor(selected ? 0xFFC8B478 : 0xFF5A4A2A);
        c.drawRoundRect(new RectF(x, y, x + w, y + h), 6, 6, P);
        P.setStyle(Paint.Style.FILL);
    }
}
