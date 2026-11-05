package com.jv.diablo2;

import com.jv.diablo2.game.CharClass;
import com.jv.diablo2.game.D2Random;
import com.jv.diablo2.game.Hero;
import com.jv.diablo2.gfx.Dc6;
import com.jv.diablo2.render.Assets;
import com.jv.diablo2.render.Fb;
import com.jv.diablo2.render.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * «Select Hero Class» (800x600) по эталонному скриншоту: семь героев стоят в лагере в порядке Amazon, Assassin, Necromancer,
 * Barbarian, Paladin, Sorceress, Druid; выбранный освещён, остальные затемнены; под ними костёр, поле «Character Name»,
 * флажки «Expansion Character» и «Hardcore», кнопки EXIT и OK. Фон — charactercreationscreen.dc6, кадры 256x256 мозаикой.
 */
final class CharCreateScene extends FbScene {
    /** Порядок слева направо, как в оригинале. */
    private static final CharClass[] ORDER = {CharClass.AMAZON, CharClass.ASSASSIN, CharClass.NECROMANCER, CharClass.BARBARIAN,
            CharClass.PALADIN, CharClass.SORCERESS, CharClass.DRUID};
    /** Центр фигуры по X и линия ног по Y в экране 800x600 (сняты с эталона). */
    private static final int[] FIG_X = {98, 200, 300, 393, 520, 610, 715};
    private static final int[] FIG_FEET = {413, 417, 390, 393, 387, 400, 413};
    /**
     * Описания под названием класса. Дословно сверен только Barbarian (по скриншоту); остальные — по памяти об игре,
     * их нужно заменить точным текстом из D2Launch.dll/скриншотов (строки в string.tbl отсутствуют).
     */
    private static final String[][] DESC = {
            {"She is skilled in the use of the", "bow and the javelin."},
            {"She is a master of traps and", "martial arts."},
            {"He is a summoner of the dead and", "a master of curses."},
            {"He is unequaled in close-quarters", "combat and mastery of weapons."},
            {"He is a holy warrior who fights", "with auras and righteous might."},
            {"She is a student of the arcane who", "commands fire, cold and lightning."},
            {"He is a shapeshifter who calls on", "the elements and the wild."}};
    private static final String[] FOLDER = {"amazon", "assassin", "necromancer", "barbarian", "paladin", "sorceress", "druid"};
    private static final int GOLD = 0xFFC8B464, TITLE = 0xFFB4A064, NAME = 0xFFD09848;

    private final Assets as = LevelLoader.assets();
    private final Dc6 bg;
    private final int[] pal, dimPal;
    private final Dc6[] idle = new Dc6[7], lit = new Dc6[7];
    private final Dc6 fire, btn, check;
    private int sel = -1;
    private String name = "";
    private boolean expansion = true, hardcore;
    private int pressed;          // 1 EXIT, 2 OK

    CharCreateScene() {
        super(800, 600);
        bg = FrontEnd.charCreateBg();
        String pn = as.fs.exists("data\\global\\palette\\fechar\\pal.dat") ? "fechar" : "sky";
        pal = as.pal(pn);
        dimPal = new int[256];
        for (int i = 0; i < 256; i++) {
            int c = pal[i];
            dimPal[i] = (c & 0xFF000000) | ((c >> 16 & 0xFF) * 140 >> 8) << 16 | ((c >> 8 & 0xFF) * 140 >> 8) << 8 | ((c & 0xFF) * 140 >> 8);
        }
        for (int i = 0; i < 7; i++) {
            List<String> files = new ArrayList<>();
            for (String f : as.fs.search("frontend\\" + FOLDER[i] + "\\")) if (f.endsWith(".dc6")) files.add(f);
            String a = null, b = null;
            for (String f : files) {
                String n = f.substring(f.lastIndexOf('\\') + 1);
                if (n.contains("nu1") && a == null) a = f;
                if (n.contains("nu2") && b == null) b = f;
            }
            if (a == null) for (String f : files) if (f.substring(f.lastIndexOf('\\') + 1).contains("nu")) {
                a = f;
                break;
            }
            if (a == null && !files.isEmpty()) a = files.get(0);
            idle[i] = a == null ? null : as.dc6(a);
            lit[i] = b == null ? idle[i] : as.dc6(b);
        }
        String fireFile = null;
        for (String f : as.fs.search("frontend\\")) {
            String n = f.toLowerCase(Locale.ROOT);
            if (n.endsWith(".dc6") && n.contains("fire") && !n.contains("logo")) {
                fireFile = f;
                break;
            }
        }
        fire = fireFile == null ? null : as.dc6(fireFile);
        btn = as.dc6("data\\global\\ui\\frontend\\mediumbuttonblank.dc6");
        check = as.dc6("data\\global\\ui\\panel\\clickbox.dc6");
    }

    private void tile(Fb fb) {
        fb.clear(0xFF000000);
        if (bg == null) return;
        int x = 0, y = 0, rowH = 0;
        for (Dc6.Frame f : bg.frames) {
            if (x >= 800) {
                x = 0;
                y += rowH;
                rowH = 0;
            }
            fb.blit(f.pixels, f.width, f.height, x, y, pal);
            x += f.width;
            rowH = Math.max(rowH, f.height);
        }
    }

    @Override
    protected void render(Fb fb) {
        tile(fb);
        Text.drawShadow(fb, as, 30, "Select Hero Class", 400, 22, TITLE, Text.CENTER);
        if (sel >= 0) {
            CharClass c = ORDER[sel];
            Text.drawShadow(fb, as, 30, c.title, 400, 70, NAME, Text.CENTER);
            for (int l = 0; l < DESC[sel].length; l++) Text.drawShadow(fb, as, 16, DESC[sel][l], 400, 110 + l * 13, 0xFFE8E0C8, Text.CENTER);
        }
        // герои: сначала невыбранные, выбранный поверх
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 7; i++) {
                if ((pass == 1) != (i == sel)) continue;
                Dc6 d = i == sel ? lit[i] : idle[i];
                if (d == null) {
                    Text.draw(fb, as, 16, ORDER[i].title, FIG_X[i], FIG_FEET[i] - 40, i == sel ? GOLD : 0xFF808080, Text.CENTER);
                    continue;
                }
                Dc6.Frame f = d.frames[(tick / 2) % d.frames.length];
                fb.blit(f.pixels, f.width, f.height, FIG_X[i] - f.width / 2, FIG_FEET[i] - f.height, i == sel ? pal : dimPal);
            }
        }
        if (fire != null) {
            Dc6.Frame f = fire.frames[(tick / 2) % fire.frames.length];
            fb.blit(f.pixels, f.width, f.height, 393 + f.offsetX, 425 + f.offsetY - f.height, pal, null, Fb.NORMAL, 255);
        }
        // имя героя
        Text.drawShadow(fb, as, 16, "Character Name", 320, 478, GOLD, Text.LEFT);
        fb.fillRect(320, 495, 168, 24, 0xFF000000);
        rect(fb, 320, 495, 168, 24, 0xFF8C7C50);
        String shown = name + ((tick / 12) % 2 == 0 ? "_" : "");
        Text.draw(fb, as, 16, shown, 326, 500, 0xFFFFFFFF, Text.LEFT);
        box(fb, 320, 527, expansion);
        Text.drawShadow(fb, as, 16, "Expansion Character", 340, 527, GOLD, Text.LEFT);
        box(fb, 320, 547, hardcore);
        Text.drawShadow(fb, as, 16, "Hardcore", 340, 547, GOLD, Text.LEFT);
        button(fb, 33, 538, "EXIT", pressed == 1);
        button(fb, 627, 538, "OK", pressed == 2);
    }

    private static void rect(Fb fb, int x, int y, int w, int h, int argb) {
        fb.fillRect(x, y, w, 1, argb);
        fb.fillRect(x, y + h - 1, w, 1, argb);
        fb.fillRect(x, y, 1, h, argb);
        fb.fillRect(x + w - 1, y, 1, h, argb);
    }

    private void box(Fb fb, int x, int y, boolean on) {
        int[] sky = as.pal("sky");
        if (check != null) {
            Dc6.Frame f = check.frames[on ? 1 : 0];
            fb.blit(f.pixels, f.width, f.height, x, y, sky);
        } else {
            rect(fb, x, y, 14, 14, GOLD);
            if (on) fb.fillRect(x + 3, y + 3, 8, 8, GOLD);
        }
    }

    private void button(Fb fb, int x, int y, String label, boolean down) {
        if (btn != null) {
            Dc6.Frame f = btn.frames[down && btn.frames.length > 1 ? 1 : 0];
            fb.blit(f.pixels, f.width, f.height, x, y, as.pal("sky"));
        } else {
            fb.fillRect(x, y, 128, 35, 0xFF2A2214);
            rect(fb, x, y, 128, 35, 0xFF6A5A3A);
        }
        Text.drawShadow(fb, as, 16, label, x + 64, y + 11 + (down ? 1 : 0), GOLD, Text.CENTER);
    }

    @Override
    protected void onPointer(int action, int x, int y) {
        if (action == 0) {
            pressed = in(x, y, 33, 538, 128, 35) ? 1 : in(x, y, 627, 538, 128, 35) ? 2 : 0;
        } else if (action == 1) {
            int p = pressed;
            pressed = 0;
            if (p == 1 && in(x, y, 33, 538, 128, 35)) {
                Nav.go(new CharSelectScene());
            } else if (p == 2 && in(x, y, 627, 538, 128, 35)) {
                create();
            } else if (p == 0) {
                tap(x, y);
            }
        }
    }

    private static boolean in(int x, int y, int rx, int ry, int rw, int rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }

    private void tap(int x, int y) {
        if (in(x, y, 320, 495, 168, 24)) {
            Nav.askName("Имя героя (латиница, до 15 символов)", name, new Nav.TextCb() {
                @Override
                public void done(String text) {
                    name = Saves.safeName(text);
                }
            });
            return;
        }
        if (in(x, y, 320, 527, 170, 18)) {
            expansion = !expansion;
            return;
        }
        if (in(x, y, 320, 547, 90, 18)) {
            hardcore = !hardcore;
            return;
        }
        int best = -1;
        int bd = 70;
        for (int i = 0; i < 7; i++) {
            if (y < FIG_FEET[i] - 200 || y > FIG_FEET[i] + 10) continue;
            int d = Math.abs(x - FIG_X[i]);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        if (best >= 0) sel = best;
    }

    private void create() {
        if (sel < 0) {
            Nav.toast("Select a hero class");
            return;
        }
        if (name.isEmpty()) {
            Nav.toast("Enter a character name");
            return;
        }
        Hero h = new Hero(ORDER[sel]);
        h.name = name;
        if (Session.itemGen != null) SandboxScene.starterKit(h, new D2Random(System.nanoTime()));
        Saves.save(h);
        Nav.go(new LoadingScene(h));
    }

    @Override
    public boolean onBack() {
        Nav.go(new CharSelectScene());
        return true;
    }
}
