package com.jv.diablo2.render;

import com.jv.diablo2.gfx.Dc6;

/**
 * Нижняя панель игры 640x480 из оригинальных DC6 (палитра "sky"): рамка ctrlpnl7, шары жизни и маны (hlthmana + overlap),
 * кнопка бега, полоса выносливости, пояс, мини-панель (minipanel_s + minipanelbtn) и значки навыков.
 * Координаты сняты с эталонного скриншота 640x480 (сверка попиксельно по центральной части панели).
 */
public final class Hud {
    public static final int W = 640, H = 480;

    // ---- прямоугольники для нажатий: x, y, w, h ----
    public static final int[] LEFT_SKILL = {117, 433, 48, 47};
    public static final int[] RIGHT_SKILL = {475, 433, 48, 47};
    public static final int[] RUN = {175, 451, 16, 20};
    public static final int[] MINI_TOGGLE = {313, 437, 15, 24};
    public static final int[] LIFE_ORB = {30, 388, 80, 80};
    public static final int[] MANA_ORB = {529, 388, 80, 80};
    public static final int[] STAMINA = {193, 452, 102, 19};
    public static final int[] XP = {174, 439, 123, 6};
    public static final int[][] BELT = {{342, 442, 29, 29}, {374, 442, 29, 29}, {406, 442, 29, 29}, {437, 442, 29, 29}};
    public static final int MINI_X = 242, MINI_Y = 407, MINI_BTN0_X = 247, MINI_BTN_PITCH = 21, MINI_BTN_DY = 3;

    /** Кнопки мини-панели в порядке слева направо (одиночная игра) и номер пары кадров в minipanelbtn.dc6. */
    public static final String[] MINI_NAMES = {"char", "inventory", "skills", "quests", "chat", "automap", "menu"};
    private static final int[] MINI_PAIR = {0, 1, 2, 4, 5, 6, 7};

    // ---- состояние ----
    public float life = 1f, mana = 1f, stamina = 1f, xp = 0f;
    public boolean running, miniOpen = true;
    public int pressedMini = -1;           // нажатая кнопка мини-панели
    public boolean pressedRun, pressedToggle;
    public boolean[] miniActive = new boolean[7];
    /** Иконки предметов пояса (inventory-кадры); null — слот пуст. */
    public Dc6[] beltIcons = new Dc6[4];
    public int leftCel = 2, rightCel = 2;  // IconCel навыков (Attack = 2 в skillicon.dc6)

    private final Assets a;
    private final int[] pal;
    private final int[] orbPal, darkPal;
    private final Dc6 panel, orbs, overlap, runBtn, miniBg, miniBtn, menuBtn, skillIcons;

    public Hud(Assets a) {
        this.a = a;
        this.pal = a.pal("sky");
        this.orbPal = new int[256];
        this.darkPal = new int[256];
        for (int i = 0; i < 256; i++) {
            int c = pal[i];
            int r = Math.min(255, (c >> 16 & 0xFF) * 13 / 10), g = Math.min(255, (c >> 8 & 0xFF) * 13 / 10), b = Math.min(255, (c & 0xFF) * 13 / 10);
            orbPal[i] = (c & 0xFF000000) | r << 16 | g << 8 | b;
            darkPal[i] = (c & 0xFF000000) | (r / 9) << 16 | (g / 9) << 8 | (b / 9);
        }
        String ui = "data\\global\\ui\\panel\\";
        panel = a.dc6(ui + "ctrlpnl7.dc6");
        orbs = a.dc6(ui + "hlthmana.dc6");
        overlap = a.dc6(ui + "overlap.dc6");
        runBtn = a.dc6(ui + "runbutton.dc6");
        miniBg = a.dc6(ui + "minipanel_s.dc6");
        miniBtn = a.dc6(ui + "minipanelbtn.dc6");
        menuBtn = a.dc6(ui + "menubutton.dc6");
        Dc6 si = a.dc6Any("data\\global\\ui\\spells\\skillicon.dc6");
        if (si == null) {
            for (String f : a.fs.search("spells\\")) {
                String n = f.substring(f.lastIndexOf('\\') + 1);
                if (n.equals("skillicon.dc6") || n.equals("skilicon.dc6")) {
                    si = a.dc6(f);
                    break;
                }
            }
        }
        skillIcons = si;
    }

    private void frame(Fb fb, Dc6 d, int f, int x, int y) {
        if (d == null || f < 0 || f >= d.frames.length) return;
        Dc6.Frame fr = d.frames[f];
        fb.blit(fr.pixels, fr.width, fr.height, x, y, pal);
    }

    /** Рисует панель поверх игрового кадра. */
    public void draw(Fb fb) {
        // 1. рамка из ctrlpnl7: левый и правый декор, три средние секции
        frame(fb, panel, 0, 0, 376);
        frame(fb, panel, 4, 523, 376);
        frame(fb, panel, 1, 165, 425);
        frame(fb, panel, 2, 293, 425);
        frame(fb, panel, 3, 421, 425);
        // 2. шары поверх рамки, затем декор overlap (змея и рога по краям шара)
        orb(fb, 0, LIFE_ORB, life);
        orb(fb, 1, MANA_ORB, mana);
        frame(fb, overlap, 0, 30, 385);
        frame(fb, overlap, 1, 528, 385);

        // 3. значки навыков
        skill(fb, LEFT_SKILL, leftCel);
        skill(fb, RIGHT_SKILL, rightCel);

        // 4. опыт, выносливость, бег
        bar(fb, XP, xp, 0xFFC8A040);
        bar(fb, STAMINA, stamina, 0xFFFED55D);
        frame(fb, runBtn, (running ? 2 : 0) + (pressedRun ? 1 : 0), RUN[0], RUN[1]);

        // 5. пояс
        for (int i = 0; i < 4; i++) {
            int[] r = BELT[i];
            Dc6 ic = beltIcons[i];
            if (ic != null && ic.frames.length > 0) {
                Dc6.Frame f = ic.frames[0];
                fb.blit(f.pixels, f.width, f.height, r[0] + (r[2] - f.width) / 2, r[1] + (r[3] - f.height) / 2, pal);
                Text.draw(fb, a, 8, String.valueOf(i + 1), r[0] + 2, r[1] + r[3] - 10, Text.GOLD, Text.LEFT);
            }
        }

        // 6. кнопка мини-панели и сама мини-панель
        frame(fb, menuBtn, (miniOpen ? 2 : 0) + (pressedToggle ? 1 : 0), MINI_TOGGLE[0], MINI_TOGGLE[1]);
        if (miniOpen) {
            frame(fb, miniBg, 0, MINI_X, MINI_Y);
            for (int i = 0; i < 7; i++) {
                int pair = MINI_PAIR[i];
                boolean down = pressedMini == i || miniActive[i];
                frame(fb, miniBtn, pair * 2 + (down ? 1 : 0), MINI_BTN0_X + i * MINI_BTN_PITCH, MINI_Y + MINI_BTN_DY);
            }
        }
    }

    private void orb(Fb fb, int which, int[] r, float pct) {
        if (orbs == null) return;
        pct = Math.max(0f, Math.min(1f, pct));
        Dc6.Frame f = orbs.frames[which == 0 ? 0 : 1];
        fb.blit(f.pixels, f.width, f.height, r[0], r[1], darkPal);      // пустая часть шара — тёмное стекло
        int skip = Math.round(f.height * (1f - pct));
        if (skip >= f.height) return;
        // строки с skip по конец: срез кадра
        byte[] part = new byte[f.width * (f.height - skip)];
        System.arraycopy(f.pixels, skip * f.width, part, 0, part.length);
        fb.blit(part, f.width, f.height - skip, r[0], r[1] + skip, orbPal);
    }

    private void bar(Fb fb, int[] r, float pct, int argb) {
        int w = Math.round(r[2] * Math.max(0f, Math.min(1f, pct)));
        if (w > 0) fb.fillRect(r[0], r[1], w, r[3], argb);
    }

    private void skill(Fb fb, int[] r, int cel) {
        if (skillIcons != null && cel >= 0 && cel < skillIcons.frames.length) {
            Dc6.Frame f = skillIcons.frames[cel];
            fb.blit(f.pixels, f.width, f.height, r[0], r[1], pal);
        } else {
            fb.fillRect(r[0] + 2, r[1] + 2, r[2] - 4, r[3] - 4, 0xFF501010);
        }
    }

    /** Номер кнопки мини-панели под точкой (x, y) в координатах 640x480 или -1. */
    public int miniAt(int x, int y) {
        if (!miniOpen) return -1;
        for (int i = 0; i < 7; i++) {
            int bx = MINI_BTN0_X + i * MINI_BTN_PITCH, by = MINI_Y + MINI_BTN_DY;
            if (x >= bx && x < bx + 20 && y >= by && y < by + 20) return i;
        }
        return -1;
    }

    public static boolean in(int[] r, int x, int y) {
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    /** true, если точка лежит на панели (клики по ней не должны двигать героя). */
    public boolean covers(int x, int y) {
        if (y >= 425) return true;
        if (x < 117 && y >= 376) return true;
        if (x >= 523 && y >= 376) return true;
        if (miniOpen && x >= MINI_X && x < MINI_X + 156 && y >= MINI_Y) return true;
        return false;
    }
}
