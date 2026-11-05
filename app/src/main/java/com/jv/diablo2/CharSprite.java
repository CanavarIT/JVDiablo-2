package com.jv.diablo2;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.jv.diablo2.game.CharClass;
import com.jv.diablo2.game.Iso;
import com.jv.diablo2.game.Player;
import com.jv.diablo2.gfx.Cof;
import com.jv.diablo2.gfx.Dcc;
import com.jv.diablo2.gfx.Palette;
import com.jv.diablo2.render.DirCalib;
import com.jv.diablo2.mpq.MpqFileSystem;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Живой спрайт персонажа: COF задаёт слои и порядок их отрисовки, каждый слой — свой DCC.
 * Кадры собираются в один Bitmap лениво и кешируются.
 */
final class CharSprite {
    private static final int CW = 180, CH = 180, AX = 90, AY = 130; // холст кадра и точка привязки (ноги)

    enum Anim {
        IDLE(new String[]{"nu", "tn"}, 10f), RUN(new String[]{"rn", "wl"}, 16f), ATTACK(new String[]{"a1"}, 0f);
        final String[] modes;
        final float fps;

        Anim(String[] m, float f) {
            modes = m;
            fps = f;
        }
    }

    private static final class Set {
        Cof cof;
        final Dcc[] layer = new Dcc[16];
        final Map<Integer, Bitmap> frames = new HashMap<>();

        boolean usable() {
            return cof != null;
        }
    }

    private final String token;
    private final String weaponClass;
    private final Palette pal;
    private final Map<Anim, Set> sets = new HashMap<>();
    private DirCalib calib;

    private CharSprite(String token, String wc, Palette pal) {
        this.token = token;
        this.weaponClass = wc;
        this.pal = pal;
    }

    static String tokenOf(CharClass c) {
        switch (c) {
            case AMAZON: return "am";
            case SORCERESS: return "so";
            case NECROMANCER: return "ne";
            case PALADIN: return "pa";
            case BARBARIAN: return "ba";
            case DRUID: return "dz";
            default: return "as";
        }
    }

    /** null, если данных игры нет или у класса не нашлось анимации. */
    static CharSprite tryLoad(CharClass cls) {
        MpqFileSystem fs = Session.fs;
        if (fs == null) return null;
        Palette pal = Res.palette("data\\global\\palette\\units\\pal.dat");
        CharSprite s = new CharSprite(tokenOf(cls), "hth", pal);
        return s.set(Anim.IDLE).usable() ? s : null;
    }

    private Set set(Anim a) {
        Set s = sets.get(a);
        if (s != null) return s;
        s = new Set();
        sets.put(a, s);
        MpqFileSystem fs = Session.fs;
        for (String mode : a.modes) {
            String cofPath = "data\\global\\chars\\" + token + "\\cof\\" + token + mode + weaponClass + ".cof";
            if (!fs.exists(cofPath)) continue;
            try {
                s.cof = Cof.parse(fs.read(cofPath));
                for (Cof.Layer l : s.cof.layers) {
                    s.layer[l.component] = loadLayer(fs, Cof.COMP[l.component].toLowerCase(Locale.ROOT), mode);
                }
                return s;
            } catch (Exception e) {
                Res.note("  COF ОШИБКА " + cofPath + ": " + e.getMessage());
                s.cof = null;
            }
        }
        return s;
    }

    /** Код экипировки: «lit» (лёгкая одежда), иначе первый найденный для брони; для оружия и щита — ничего. */
    private Dcc loadLayer(MpqFileSystem fs, String comp, String mode) throws Exception {
        String dir = "data\\global\\chars\\" + token + "\\" + comp + "\\";
        String suffix = mode + weaponClass + ".dcc";
        String[] gear = {"lit", token + "1", "ba1"};
        String path = null;
        for (String g : gear) {
            String p = dir + token + comp + g + suffix;
            if (fs.exists(p)) {
                path = p;
                break;
            }
        }
        boolean optional = comp.equals("rh") || comp.equals("lh") || comp.equals("sh") || comp.equals("s1") || comp.equals("s2");
        if (path == null && !optional) {
            String best = null;
            for (String f : fs.search(dir, suffix)) if (best == null || f.compareTo(best) < 0) best = f;
            path = best;
        }
        if (path == null) return null;
        return Dcc.parse(fs.read(path));
    }

    /** Рисует героя в экранной точке (sx, sy) — между ног. */
    void draw(Canvas c, float sx, float sy, Player pl, long nowMs) {
        float fx = Iso.dirX(pl.faceDx, pl.faceDy), fy = Iso.dirY(pl.faceDx, pl.faceDy);
        Anim a = pl.attacking() ? Anim.ATTACK : (pl.moving ? Anim.RUN : Anim.IDLE);
        Set s = set(a);
        if (!s.usable()) {
            a = Anim.IDLE;
            s = set(a);
        }
        if (!s.usable()) return;
        int dirs = s.cof.directions, fpd = s.cof.framesPerDir;
        int dir = dirs == 16 ? calib().file(fx, fy) : fileDir(fx, fy, dirs);
        int frame;
        if (a == Anim.ATTACK) frame = Math.max(0, Math.min(fpd - 1, (int) (pl.attackProgress() * fpd)));
        else frame = (int) ((nowMs / 1000f) * a.fps) % fpd;
        Bitmap bmp = frameBitmap(s, dir, frame);
        if (bmp != null) c.drawBitmap(bmp, sx - AX, sy - AY, null);
    }

    /** Раскладка направлений, измеренная по рукам героя (см. DirCalib); при неудаче — прежняя. */
    private DirCalib calib() {
        if (calib == null) {
            Set s = set(Anim.IDLE);
            DirCalib c = s.usable() ? DirCalib.measure(s.layer[3], s.layer[4]) : DirCalib.DEFAULT;
            if (!c.fitted && s.usable()) {
                DirCalib sh = DirCalib.measure(s.layer[8], s.layer[9]);
                if (sh.fitted) c = sh;
            }
            calib = c;
        }
        return calib;
    }

    /** Экранный вектор взгляда -> индекс файлового направления DCC (запасной путь для 4/8 направлений). */
    static int fileDir(float sx, float sy, int dirs) {
        if (dirs == 16) return DirCalib.DEFAULT.file(sx, sy);
        double theta = Math.atan2(-sx, sy);   // 0 = на зрителя
        int idx = (int) Math.round(theta / (Math.PI * 2) * dirs);
        return ((idx % dirs) + dirs) % dirs;
    }

    private Bitmap frameBitmap(Set s, int dir, int frame) {
        int key = dir * 1000 + frame;
        Bitmap b = s.frames.get(key);
        if (b != null) return b;
        int[] px = new int[CW * CH];
        try {
            for (int i = 0; i < s.cof.layerCount; i++) {
                int comp = s.cof.priority[dir][frame][i] & 0xFF;
                if (comp >= 16 || s.layer[comp] == null) continue;
                Dcc.Frame[] fr = s.layer[comp].direction(dir % s.layer[comp].directions);
                Dcc.Frame fm = fr[frame % fr.length];
                for (int y = 0; y < fm.height; y++) {
                    int py = AY + fm.offsetY + y;
                    if (py < 0 || py >= CH) continue;
                    for (int x = 0; x < fm.width; x++) {
                        int v = fm.pixels[y * fm.width + x] & 0xFF;
                        if (v == 0) continue;
                        int pxx = AX + fm.offsetX + x;
                        if (pxx >= 0 && pxx < CW) px[py * CW + pxx] = pal.argb[v];
                    }
                }
            }
        } catch (Exception e) {
            Res.note("  DCC ОШИБКА " + token + ": " + e.getMessage());
        }
        b = Bitmap.createBitmap(px, CW, CH, Bitmap.Config.ARGB_8888);
        s.frames.put(key, b);
        return b;
    }
}
