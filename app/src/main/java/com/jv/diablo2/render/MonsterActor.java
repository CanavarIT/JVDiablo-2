package com.jv.diablo2.render;

import com.jv.diablo2.game.Monster;
import com.jv.diablo2.gfx.AnimData;

import java.util.Locale;

/**
 * Монстр на экране: режим анимации выбирается по состоянию (nu — стоит, wl — идёт, a1 — удар, gh — получил удар,
 * dt — умирает, затем последний кадр dt), кадры идут со скоростью из animdata.d2. Длительности удара, получения удара
 * и смерти считаются из числа кадров COF, а момент попадания — из ключевого кадра атаки (флаг 1 в COF).
 */
final class MonsterActor implements LevelRenderer.Actor {
    final Monster m;
    private final Paperdoll d;
    private final Paperdoll.Look lk;
    private final int[] pal;
    private final AnimData ad;
    private final String token;
    private float animPos;
    private String lastMode = "nu";
    /** Имя для полоски жизни. */
    final String displayName;

    MonsterActor(Monster m, Paperdoll d, Paperdoll.Look lk, int[] pal, AnimData ad, String token, String displayName) {
        this.m = m;
        this.d = d;
        this.lk = lk;
        this.pal = pal;
        this.ad = ad;
        this.token = token.toLowerCase(Locale.ROOT);
        this.displayName = displayName;
        configure();
    }

    // ---------------------------------------------------------------- скорость и длительности

    private static float fallbackSpeed(String mode) {
        return mode.equals("nu") ? 128f : 256f;
    }

    /** Кадров за тик для режима: скорость animdata (по имени COF с классом оружия монстра, затем hth) / 256. */
    float speed(String mode) {
        String wc = lk.weaponClass == null ? "hth" : lk.weaponClass.toLowerCase(Locale.ROOT);
        int s = ad.speed(token + mode + wc);
        if (s < 0) s = ad.speed(token + mode + "hth");
        return (s < 0 ? fallbackSpeed(mode) : s) / 256f;
    }

    private int frames(String mode) {
        return d.frames(mode, lk);
    }

    private int ticksFor(String mode, int dflt) {
        int f = frames(mode);
        if (f <= 0) return dflt;
        return Math.max(1, (int) Math.ceil(f / speed(mode)));
    }

    /** Подгоняет тайминги монстра под его анимации. */
    private void configure() {
        m.attackTicks = Math.max(4, ticksFor("a1", 16));
        m.hitTicks = Math.max(2, ticksFor("gh", 6));
        m.deathTicks = Math.max(4, ticksFor("dt", 20));
        int hit = m.attackTicks / 2;
        Paperdoll.Anim a = d.load("a1", lk);
        if (a != null) {
            for (int f = 0; f < a.cof.keyframes.length; f++) {
                if (a.cof.keyframes[f] == 1) {          // ключевой кадр атаки
                    hit = Math.round(f / speed("a1"));
                    break;
                }
            }
        }
        m.attackHitAt = Math.max(1, Math.min(m.attackTicks - 1, hit));
    }

    // ---------------------------------------------------------------- рисование

    @Override
    public float x() {
        return m.x;
    }

    @Override
    public float y() {
        return m.y;
    }

    /** Движение анимаций «по кругу» (стоит, идёт): вызывается раз в тик. */
    void tick() {
        animPos += speed(lastMode);
    }

    private String modeNow() {
        if (m.dead) return "dt";
        if (m.hitProgress() >= 0 && frames("gh") > 0) return "gh";
        if (m.attackProgress() >= 0 && frames("a1") > 0) return "a1";
        if (m.moving && frames("wl") > 0) return "wl";
        return "nu";
    }

    @Override
    public void draw(Fb fb, int sx, int sy) {
        String mode = modeNow();
        if (frames(mode) == 0) mode = "nu";
        int n = frames(mode);
        if (n == 0) return;
        if (!mode.equals(lastMode)) {
            lastMode = mode;
            animPos = 0f;
        }
        int frame;
        switch (mode) {
            case "dt":
                frame = Math.min(n - 1, (int) (m.corpseTimer * speed("dt")));     // после смерти держится последний кадр
                break;
            case "a1":
                frame = Math.min(n - 1, (int) (m.attackProgress() * n));
                break;
            case "gh":
                frame = Math.min(n - 1, (int) (m.hitProgress() * n));
                break;
            default:
                frame = ((int) animPos) % n;
        }
        int dirs = Math.max(1, d.directions(mode, lk));
        float fx = m.faceDx - m.faceDy, fy = (m.faceDx + m.faceDy) * 0.5f;
        d.draw(fb, pal, mode, lk, fileDir(fx, fy, dirs), frame, sx, sy, true);
    }

    /** Файловое направление: 16 — по калибровке, 8 — таблица Paperdoll, 4 и меньше — каждое второе из восьми. */
    private int fileDir(float fx, float fy, int dirs) {
        if (dirs <= 1) return 0;
        if (dirs >= 16) return d.calib().file(fx, fy);
        int d8 = Paperdoll.fileDir(fx, fy, 8);
        return dirs >= 8 ? d8 : (d8 * dirs / 8) % dirs;
    }

    /** Попадает ли точка экрана (px, py) в «коробку» монстра с ногами в (sx, sy). */
    boolean hitTest(int px, int py, int sx, int sy) {
        return !m.dead && px >= sx - 24 && px <= sx + 24 && py >= sy - 70 && py <= sy + 12;
    }
}
