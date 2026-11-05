package com.jv.diablo2.render;

import com.jv.diablo2.gfx.Cof;
import com.jv.diablo2.gfx.Dcc;
import com.jv.diablo2.mpq.MpqFileSystem;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Составной спрайт героя: COF задаёт слои (HD, TR, LG, RA, LA, RH, LH, SH, S1..S8) и порядок их рисования
 * для каждого направления и кадра; каждый слой — отдельный DCC с кодом экипировки в имени:
 * data\global\chars\{класс}\{слой}\{класс}{слой}{код}{режим}{класс оружия}.dcc, где код — "lit" (нет брони),
 * "med", "hvy" или alternategfx предмета ("qlt", "cap", "buc", код оружия).
 */
public final class Paperdoll {
    /** Внешний вид: коды по слоям (индексы как в Cof.COMP). null — слой пустой. */
    public static final class Look {
        public final String[] code = new String[16];
        public String weaponClass = "hth";

        public Look() {
            for (String c : new String[]{"hd", "tr", "lg", "ra", "la", "s1", "s2"}) set(c, "lit");
        }

        private String keyCache;

        public void set(String comp, String c) {
            for (int i = 0; i < Cof.COMP.length; i++) if (Cof.COMP[i].equalsIgnoreCase(comp)) code[i] = c;
            keyCache = null;
        }

        /** Ключ кеша наборов; после прямого изменения code[] / weaponClass вызывать changed(). */
        String key() {
            String k = keyCache;
            if (k == null) {
                StringBuilder sb = new StringBuilder(weaponClass);
                for (String c : code) sb.append('|').append(c);
                keyCache = k = sb.toString();
            }
            return k;
        }

        public void changed() {
            keyCache = null;
        }
    }

    public static final class Anim {
        Cof cof;
        final Dcc[] layer = new Dcc[16];
        String mode;
    }

    private final MpqFileSystem fs;
    private final String token;
    private final String root;
    private final Map<String, Anim> sets = new HashMap<>();
    private DirCalib calib;
    private static final Map<String, DirCalib> CALIB_CACHE = new HashMap<>();

    /** Герой: файлы в data\\global\\chars\\{токен}\\... */
    public Paperdoll(MpqFileSystem fs, String classToken) {
        this(fs, classToken, "data\\global\\chars\\");
    }

    /** root — каталог с токенами: "data\\global\\chars\\" (герои) или "data\\global\\monsters\\" (монстры и NPC). */
    public Paperdoll(MpqFileSystem fs, String token, String root) {
        this.fs = fs;
        this.token = token.toLowerCase(Locale.ROOT);
        this.root = root;
    }

    /** Загружает набор для режима (например, "tn", "tw", "nu", "wl", "rn", "a1") или null, если COF нет. */
    public Anim load(String mode, Look look) {
        String k = mode + "#" + look.key();
        if (sets.containsKey(k)) return sets.get(k);
        Anim s = null;
        String wc = look.weaponClass;
        for (String w : new String[]{wc, "hth"}) {
            String cofPath = root + token + "\\cof\\" + token + mode + w + ".cof";
            if (!fs.exists(cofPath)) continue;
            try {
                Cof cof = Cof.parse(fs.read(cofPath));
                Anim n = new Anim();
                n.cof = cof;
                n.mode = mode;
                for (Cof.Layer l : cof.layers) {
                    int c = l.component;
                    n.layer[c] = layerFile(Cof.COMP[c].toLowerCase(Locale.ROOT), look.code[c], mode, l.weaponClass.toLowerCase(Locale.ROOT));
                }
                s = n;
                break;
            } catch (Exception e) {
                // следующий вариант класса оружия
            }
        }
        sets.put(k, s);
        return s;
    }

    private Dcc layerFile(String comp, String code, String mode, String wc) {
        if (code == null) return null;
        String dir = root + token + "\\" + comp + "\\";
        boolean body = comp.equals("hd") || comp.equals("tr") || comp.equals("lg") || comp.equals("ra") || comp.equals("la")
                || comp.equals("s1") || comp.equals("s2");
        String[] tries = body ? new String[]{code, "lit"} : new String[]{code};   // для оружия и щита запасного "lit" нет
        for (String c : tries) {
            String p = dir + token + comp + c + mode + wc + ".dcc";
            if (!fs.exists(p)) continue;
            try {
                return Dcc.parse(fs.read(p));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Раскладка 16 файловых направлений героя, измеренная по спрайтам (см. {@link DirCalib}): куда реально смотрит
     * каждый файл DCC. Считается один раз на класс и кешируется; если измерить не удалось — прежняя раскладка.
     */
    public synchronized DirCalib calib() {
        if (calib != null) return calib;
        String key = root + token;
        synchronized (CALIB_CACHE) {
            calib = CALIB_CACHE.get(key);
        }
        if (calib != null) return calib;
        DirCalib c = DirCalib.DEFAULT;
        try {
            Look l = new Look();
            l.weaponClass = "hth";
            Anim a = load("tn", l);
            if (a == null || a.cof.directions < 16) a = load("nu", l);
            if (a != null && a.cof.directions >= 16) {
                // боковая ось тела: правая рука относительно левой; без рук — правое и левое плечо
                c = DirCalib.measure(a.layer[3], a.layer[4]);
                if (!c.fitted) {
                    DirCalib sh = DirCalib.measure(a.layer[8], a.layer[9]);
                    if (sh.fitted) c = sh;
                }
            }
        } catch (RuntimeException e) {
            c = DirCalib.DEFAULT;
        }
        calib = c;
        synchronized (CALIB_CACHE) {
            CALIB_CACHE.put(key, c);
        }
        return c;
    }

    /** Файловое направление для вектора взгляда на экране (sx вправо, sy вниз). Для героев лучше {@link #calib()}. */
    public static int fileDir(float sx, float sy, int dirs) {
        double theta = Math.atan2(sx, sy);           // 0 = на зрителя, +90° = вправо
        if (dirs >= 16) return DirCalib.DEFAULT.file(sx, sy);
        // 8 направлений (монстры, NPC): файлы 0..7 соответствуют направлениям игрока 8..15 -> юго-восток, восток, ... юг
        int idx = (int) Math.round(theta / (Math.PI * 2) * 8);      // 0 = юг, 1 = юго-восток?, см. таблицу ниже
        return EIGHT[Math.floorMod(idx, 8)];
    }

    // индекс по часовой стрелке от юга: S, SE, E, NE, N, NW, W, SW (как на экране при sx вправо) -> файл монстра
    private static final int[] EIGHT = {7, 0, 1, 2, 3, 4, 5, 6};

    public int directions(String mode, Look look) {
        Anim s = load(mode, look);
        return s == null ? 0 : s.cof.directions;
    }

    public int frames(String mode, Look look) {
        Anim s = load(mode, look);
        return s == null ? 0 : s.cof.framesPerDir;
    }

    /** Рисует кадр в точку (x, y) = между ног героя. dir — файловое направление. Возвращает false, если нет данных. */
    public boolean draw(Fb fb, int[] pal, String mode, Look look, int dir, int frame, int x, int y) {
        return draw(fb, pal, mode, look, dir, frame, x, y, true);
    }

    /** То же, с тенью под юнитом (shadow = true). */
    public boolean draw(Fb fb, int[] pal, String mode, Look look, int dir, int frame, int x, int y, boolean shadow) {
        Anim s = load(mode, look);
        if (s == null) return false;
        Cof cof = s.cof;
        dir = Math.floorMod(dir, cof.directions);
        frame = Math.floorMod(frame, cof.framesPerDir);
        if (shadow) {
            fb.shadowBegin();
            for (int i = 0; i < cof.layerCount; i++) {
                int comp = cof.priority[dir][frame][i] & 0xFF;
                if (comp >= 16 || s.layer[comp] == null) continue;
                try {
                    Dcc d = s.layer[comp];
                    Dcc.Frame[] fr = d.direction(dir % d.directions);
                    Dcc.Frame fm = fr[frame % fr.length];
                    fb.shadowAdd(fm.pixels, fm.width, fm.height, x + fm.offsetX, y + fm.offsetY, x, y);
                } catch (Exception e) {
                    // слой пропускается
                }
            }
            fb.shadowEnd(x, y);
        }
        for (int i = 0; i < cof.layerCount; i++) {
            int comp = cof.priority[dir][frame][i] & 0xFF;
            if (comp >= 16 || s.layer[comp] == null) continue;
            try {
                Dcc d = s.layer[comp];
                Dcc.Frame[] fr = d.direction(dir % d.directions);
                Dcc.Frame fm = fr[frame % fr.length];
                fb.blit(fm.pixels, fm.width, fm.height, x + fm.offsetX, y + fm.offsetY, pal);
            } catch (Exception e) {
                // битый слой не должен ронять кадр
            }
        }
        return true;
    }
}
