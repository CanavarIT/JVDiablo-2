package com.jv.diablo2;

import android.graphics.Bitmap;

import com.jv.diablo2.gfx.D2Font;
import com.jv.diablo2.gfx.Dc6;
import com.jv.diablo2.gfx.Palette;
import com.jv.diablo2.mpq.MpqFileSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Поиск и кеш ресурсов игры в MPQ. Каждый ресурс ищется сначала по известным путям, потом по (listfile)
 * по подстрокам. Результат поиска пишется в отчёт (экран «Диагностика»), чтобы расхождения были видны.
 */
final class Res {
    private Res() {
    }

    private static final Map<String, Object> cache = new HashMap<>();
    private static final Object NONE = new Object();
    private static final List<String> report = new ArrayList<>();

    static synchronized void reset() {
        cache.clear();
        report.clear();
    }

    static synchronized void note(String line) {
        report.add(line);
    }

    static synchronized List<String> report() {
        return new ArrayList<>(report);
    }

    /** Путь файла: сначала кандидаты, потом поиск по (listfile) с тем же расширением. null — не найден. */
    static synchronized String find(String key, String[] cands, String... search) {
        String ck = "path:" + key;
        Object c = cache.get(ck);
        if (c != null) return c == NONE ? null : (String) c;
        String found = null;
        MpqFileSystem fs = Session.fs;
        if (fs != null) {
            found = fs.firstExisting(cands);
            if (found == null && search.length > 0) {
                String ext = cands.length > 0 && cands[0].lastIndexOf('.') >= 0 ? cands[0].substring(cands[0].lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
                for (String f : fs.search(search)) {
                    if (f.endsWith(ext) && (found == null || f.length() < found.length())) found = f;
                }
            }
        }
        cache.put(ck, found == null ? NONE : found);
        report.add(String.format(Locale.ROOT, "%-22s %s", key, found == null ? "НЕ НАЙДЕНО" : found));
        return found;
    }

    static synchronized Dc6 dc6(String key, String[] cands, String... search) {
        String path = find(key, cands, search);
        if (path == null) return null;
        return dc6(path);
    }

    static synchronized Dc6 dc6(String path) {
        String ck = "dc6:" + path;
        Object c = cache.get(ck);
        if (c != null) return c == NONE ? null : (Dc6) c;
        Dc6 d = null;
        try {
            d = Dc6.parse(Session.fs.read(path));
            Dc6.Frame f = d.frames[0];
            report.add(String.format(Locale.ROOT, "  dc6 %s: кадров %d (напр. %d), первый %dx%d смещ. %d,%d", path, d.frames.length, d.directions, f.width, f.height, f.offsetX, f.offsetY));
        } catch (Exception e) {
            report.add("  dc6 ОШИБКА " + path + ": " + e.getMessage());
        }
        cache.put(ck, d == null ? NONE : d);
        return d;
    }

    static synchronized Palette palette(String... cands) {
        String ck = "pal:" + String.join("|", cands);
        Object c = cache.get(ck);
        if (c != null) return (Palette) c;
        Palette p = null;
        MpqFileSystem fs = Session.fs;
        if (fs != null) {
            for (String path : cands) {
                try {
                    p = Palette.fromDat(fs.read(path));
                    report.add("  палитра " + path);
                    break;
                } catch (Exception ignored) {
                }
            }
        }
        if (p == null) {
            p = Palette.grayscale();
            report.add("  палитра: не найдена (" + cands[0] + " ...), серая");
        }
        cache.put(ck, p);
        return p;
    }

    static synchronized D2Font font(String path) {
        String ck = "font:" + path;
        Object c = cache.get(ck);
        if (c != null) return c == NONE ? null : (D2Font) c;
        D2Font f = null;
        try {
            Dc6 d = dc6(path + ".dc6");
            if (d != null) f = D2Font.parse(Session.fs.read(path + ".tbl"), d.frames.length);
            if (f != null) report.add("  шрифт " + path + ": символов " + f.size());
        } catch (Exception e) {
            report.add("  шрифт ОШИБКА " + path + ": " + e.getMessage());
        }
        cache.put(ck, f == null ? NONE : f);
        return f;
    }

    static synchronized Bitmap bitmap(Dc6 d, Palette pal, int frame) {
        if (d == null || frame < 0 || frame >= d.frames.length) return null;
        String ck = "bmp:" + System.identityHashCode(d) + ":" + System.identityHashCode(pal) + ":" + frame;
        Object c = cache.get(ck);
        if (c != null) return c == NONE ? null : (Bitmap) c;
        Dc6.Frame f = d.frames[frame];
        Bitmap b = (f.width <= 0 || f.height <= 0) ? null : Bitmap.createBitmap(f.toArgb(pal), f.width, f.height, Bitmap.Config.ARGB_8888);
        cache.put(ck, b == null ? NONE : b);
        return b;
    }

    /** Полный отчёт для отправки разработчику. */
    static String fullReport() {
        StringBuilder sb = new StringBuilder();
        MpqFileSystem fs = Session.fs;
        sb.append("JVDiablo2 отчёт\n").append(Session.status).append("\n");
        if (fs == null) return sb.append("MPQ не подключены\n").toString();
        sb.append("Архивы: ").append(fs.archiveNames()).append("\n");
        Map<String, Integer> ext = new TreeMap<>();
        for (String f : fs.files()) {
            int k = f.lastIndexOf('.');
            String e = k >= 0 ? f.substring(k) : "(нет)";
            Integer n = ext.get(e);
            ext.put(e, n == null ? 1 : n + 1);
        }
        sb.append("Файлов в (listfile): ").append(fs.files().size()).append("  по расширениям: ").append(ext).append("\n\n");
        for (String l : report()) sb.append(l).append("\n");
        return sb.toString();
    }
}
