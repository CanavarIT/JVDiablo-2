package com.jv.diablo2.render;

import com.jv.diablo2.gfx.D2Font;
import com.jv.diablo2.gfx.Dc6;
import com.jv.diablo2.gfx.Palette;
import com.jv.diablo2.gfx.StringTbl;
import com.jv.diablo2.mpq.MpqFileSystem;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Кеш ресурсов игры для отрисовщика (чистая Java, без Android): палитры, DC6, шрифты, таблица строк.
 * Всё берётся из MpqFileSystem по оригинальным путям, поэтому тот же код работает на устройстве и на JVM-тестах.
 */
public final class Assets {
    public final MpqFileSystem fs;
    private final Map<String, Object> cache = new HashMap<>();
    private static final Object NONE = new Object();
    /** Что не нашлось — для экрана диагностики. */
    public final java.util.List<String> missing = new java.util.ArrayList<>();

    public Assets(MpqFileSystem fs) {
        this.fs = fs;
    }

    private Object get(String key) {
        return cache.get(key);
    }

    /** Палитра по имени каталога: "act1", "sky", "units", "static", "loading", "menu3"... Нет файла — серая. */
    public synchronized Palette palette(String name) {
        String key = "pal:" + name;
        Object c = get(key);
        if (c != null) return (Palette) c;
        Palette p = null;
        String path = "data\\global\\palette\\" + name + "\\pal.dat";
        try {
            if (fs.exists(path)) p = Palette.fromDat(fs.read(path));
        } catch (Exception e) {
            p = null;
        }
        if (p == null) {
            missing.add(path);
            p = Palette.grayscale();
        }
        cache.put(key, p);
        return p;
    }

    public int[] pal(String name) {
        return palette(name).argb;
    }

    /** Палитра с поправкой яркости (гамма < 1 светлее): игра показывает мир светлее, чем «сырая» палитра. */
    public synchronized int[] palGamma(String name, double gamma) {
        String key = "palg:" + name + ":" + gamma;
        Object c = get(key);
        if (c != null) return (int[]) c;
        int[] src = pal(name), out = new int[256];
        for (int i = 0; i < 256; i++) {
            int v = src[i];
            int r = (int) Math.round(255 * Math.pow((v >> 16 & 0xFF) / 255.0, gamma));
            int g = (int) Math.round(255 * Math.pow((v >> 8 & 0xFF) / 255.0, gamma));
            int b = (int) Math.round(255 * Math.pow((v & 0xFF) / 255.0, gamma));
            out[i] = (v & 0xFF000000) | r << 16 | g << 8 | b;
        }
        cache.put(key, out);
        return out;
    }

    /** DC6 по пути; null — нет такого файла (записывается в missing). */
    public synchronized Dc6 dc6(String path) {
        String p = path.toLowerCase(Locale.ROOT).replace('/', '\\');
        String key = "dc6:" + p;
        Object c = get(key);
        if (c != null) return c == NONE ? null : (Dc6) c;
        Dc6 d = null;
        try {
            if (fs.exists(p)) d = Dc6.parse(fs.read(p));
        } catch (Exception e) {
            d = null;
        }
        if (d == null) missing.add(p);
        cache.put(key, d == null ? NONE : d);
        return d;
    }

    /** Первый существующий из списка путей. */
    public Dc6 dc6Any(String... paths) {
        for (String p : paths) {
            String q = p.toLowerCase(Locale.ROOT).replace('/', '\\');
            if (fs.exists(q)) {
                Dc6 d = dc6(q);
                if (d != null) return d;
            }
        }
        return null;
    }

    /** Шрифт игры: размеры 6, 8, 16, 24, 30, 42 (латиница) — пара fontNN.tbl + fontNN.dc6. */
    public synchronized D2Font font(int size) {
        String key = "font:" + size;
        Object c = get(key);
        if (c != null) return c == NONE ? null : (D2Font) c;
        D2Font f = null;
        Dc6 d = fontDc6(size);
        try {
            String t = "data\\local\\font\\latin\\font" + size + ".tbl";
            if (d != null && fs.exists(t)) f = D2Font.parse(fs.read(t), d.frames.length);
        } catch (Exception e) {
            f = null;
        }
        cache.put(key, f == null ? NONE : f);
        return f;
    }

    public Dc6 fontDc6(int size) {
        return dc6("data\\local\\font\\latin\\font" + size + ".dc6");
    }

    /** Строки игры: string.tbl, поверх expansionstring.tbl и patchstring.tbl. */
    public synchronized StringTbl strings() {
        Object c = get("strings");
        if (c != null) return (StringTbl) c;
        StringTbl t = new StringTbl();
        for (String f : new String[]{"string.tbl", "expansionstring.tbl", "patchstring.tbl"}) {
            String p = "data\\local\\lng\\eng\\" + f;
            try {
                if (fs.exists(p)) t.add(fs.read(p));
            } catch (Exception e) {
                missing.add(p + " (" + e.getMessage() + ")");
            }
        }
        cache.put("strings", t);
        return t;
    }
}
