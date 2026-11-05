package com.jv.diablo2;

import android.graphics.Canvas;

import com.jv.diablo2.game.CharClass;
import com.jv.diablo2.game.PlayerStats;
import com.jv.diablo2.gfx.Dc6;
import com.jv.diablo2.gfx.Palette;

import java.util.List;

/** Выбор героя: до 8 слотов (сохранения), предпросмотр класса, OK / создать / удалить / выход. */
final class CharSelectScene extends Screen800 {
    private final Dc6 bg = FrontEnd.charSelectBg();
    private final Palette pal = FrontEnd.fechar();
    private final List<Saves.Entry> list = Saves.list();
    private int sel;
    private int confirmUntil;
    private final Ui.Btn del;

    CharSelectScene() {
        buttons.add(new Ui.Btn("EXIT", 33, 540, 168, 35, 24, new Runnable() {
            @Override
            public void run() {
                Nav.go(new MainMenuScene());
            }
        }));
        buttons.add(new Ui.Btn("CREATE NEW CHARACTER", 264, 540, 272, 35, 24, new Runnable() {
            @Override
            public void run() {
                Nav.go(new CharCreateScene());
            }
        }));
        buttons.add(new Ui.Btn("OK", 599, 540, 168, 35, 24, new Runnable() {
            @Override
            public void run() {
                if (sel < list.size()) Nav.go(new LoadingScene(list.get(sel).hero));
            }
        }));
        del = new Ui.Btn("DELETE CHARACTER", 599, 495, 168, 35, 16, new Runnable() {
            @Override
            public void run() {
                if (sel >= list.size()) return;
                if (tick < confirmUntil) {
                    list.get(sel).file.delete();
                    Nav.go(new CharSelectScene());
                } else {
                    confirmUntil = tick + 75;
                }
            }
        });
        buttons.add(del);
    }

    private float slotX(int i) {
        return 30 + (i % 2) * 190;
    }

    private float slotY(int i) {
        return 110 + (i / 2) * 90;
    }

    @Override
    protected void render(Canvas c) {
        Ui.background(c, bg, pal);
        Ui.text(c, "SELECT CHARACTER", 400, 20, 30, Ui.GOLD, 0);
        del.label = tick < confirmUntil ? "CONFIRM DELETE?" : "DELETE CHARACTER";
        if (list.isEmpty()) {
            Ui.text(c, "NO CHARACTERS - CREATE A NEW ONE", 400, 300, 24, Ui.WHITE, 0);
            return;
        }
        for (int i = 0; i < Math.min(8, list.size()); i++) {
            PlayerStats s = list.get(i).hero.stats;
            Ui.panel(c, slotX(i), slotY(i), 180, 80, i == sel);
            Ui.text(c, list.get(i).hero.name, slotX(i) + 90, slotY(i) + 10, 24, Ui.GOLD, 0);
            Ui.text(c, "Level " + s.clvl + " " + s.cls.title, slotX(i) + 90, slotY(i) + 46, 16, Ui.WHITE, 0);
        }
        if (sel < list.size()) {
            CharClass cls = list.get(sel).hero.stats.cls;
            Dc6 fig = FrontEnd.classIdle(cls);
            if (fig != null) Ui.sprite(c, fig, pal, tick / 2, 600, 450, false);
            else Ui.text(c, cls.title.toUpperCase(), 600, 300, 42, Ui.GOLD, 0);
        }
    }

    @Override
    protected void tap(float x, float y) {
        for (int i = 0; i < Math.min(8, list.size()); i++) {
            if (x >= slotX(i) && x <= slotX(i) + 180 && y >= slotY(i) && y <= slotY(i) + 80) {
                if (sel == i && System.nanoTime() - lastTap < 400_000_000L) Nav.go(new LoadingScene(list.get(i).hero));
                sel = i;
                lastTap = System.nanoTime();
            }
        }
    }

    private long lastTap;

    @Override
    public boolean onBack() {
        Nav.go(new MainMenuScene());
        return true;
    }
}
