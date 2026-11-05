package com.jv.diablo2.render;

import com.jv.diablo2.gfx.D2Font;
import com.jv.diablo2.gfx.Dc6;

/** Текст оригинальными шрифтами игры в кадровый буфер. Цвет задаётся множителем (как цветовые коды D2). */
public final class Text {
    public static final int LEFT = -1, CENTER = 0, RIGHT = 1;

    // цвета текста игры (коды ÿc0..ÿc;): белый, красный, зелёный, синий, золотой, серый, чёрный, жёлтый, оранжевый
    public static final int WHITE = 0xFFFFFFFF, RED = 0xFFFF4040, GREEN = 0xFF30D030, BLUE = 0xFF6060FF,
            GOLD = 0xFFC8B464, GRAY = 0xFFA0A0A0, ORANGE = 0xFFFFA020, YELLOW = 0xFFFFFF60;

    private Text() {
    }

    public static int width(Assets a, int size, String s) {
        D2Font f = a.font(size);
        if (f == null) return s.length() * size / 3;
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            D2Font.Glyph g = f.get(s.charAt(i));
            w += g != null ? g.width : size / 3;
        }
        return w;
    }

    public static int height(Assets a, int size) {
        D2Font f = a.font(size);
        return f == null ? size : f.maxHeight;
    }

    /** y — верх строки. Возвращает ширину. */
    public static int draw(Fb fb, Assets a, int size, String s, int x, int y, int tint, int align) {
        D2Font f = a.font(size);
        Dc6 d = a.fontDc6(size);
        int w = width(a, size, s);
        if (f == null || d == null) return w;
        int cx = align == CENTER ? x - w / 2 : align == RIGHT ? x - w : x;
        int[] pal = a.pal("static");
        for (int i = 0; i < s.length(); i++) {
            D2Font.Glyph g = f.get(s.charAt(i));
            if (g == null) {
                cx += size / 3;
                continue;
            }
            Dc6.Frame fr = d.frames[g.frame];
            fb.blitTint(fr.pixels, fr.width, fr.height, cx, y, pal, tint);
            cx += g.width;
        }
        return w;
    }

    /** Текст с чёрной тенью на 1 пиксель (как в меню). */
    public static int drawShadow(Fb fb, Assets a, int size, String s, int x, int y, int tint, int align) {
        draw(fb, a, size, s, x + 1, y + 1, 0xFF000000, align);
        return draw(fb, a, size, s, x, y, tint, align);
    }
}
