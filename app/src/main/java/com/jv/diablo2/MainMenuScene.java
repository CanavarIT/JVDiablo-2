package com.jv.diablo2;

import android.graphics.Canvas;

import com.jv.diablo2.gfx.Dc6;
import com.jv.diablo2.gfx.Palette;

/**
 * Главное меню: фон и логотип из MPQ, кнопки в стиле игры. Расположение кнопок взято по памяти об игре;
 * точные координаты уточняются по скриншоту оригинала. Левый нижний угол — служебные инструменты.
 */
final class MainMenuScene extends Screen800 {
    private static boolean musicStarted;
    private final Dc6 bg = FrontEnd.mainMenuBg();
    private final Dc6 fireL = FrontEnd.logo("FireLeft"), fireR = FrontEnd.logo("FireRight");
    private final Dc6 blackL = FrontEnd.logo("BlackLeft"), blackR = FrontEnd.logo("BlackRight");
    private final Palette sky = FrontEnd.sky(), units = FrontEnd.units();

    MainMenuScene() {
        Ui.Btn bnet = new Ui.Btn("BATTLE.NET", 264, 270, 272, 35, 30, null);
        bnet.enabled = false;
        Ui.Btn multi = new Ui.Btn("OTHER MULTIPLAYER", 264, 320, 272, 35, 30, null);
        multi.enabled = false;
        buttons.add(bnet);
        buttons.add(multi);
        buttons.add(new Ui.Btn("SINGLE PLAYER", 264, 370, 272, 35, 30, new Runnable() {
            @Override
            public void run() {
                Nav.go(new CharSelectScene());
            }
        }));
        buttons.add(new Ui.Btn("EXIT DIABLO II", 264, 435, 272, 35, 30, new Runnable() {
            @Override
            public void run() {
                Nav.exit();
            }
        }));
        Ui.Btn credits = new Ui.Btn("CREDITS", 33, 530, 168, 35, 24, null);
        credits.enabled = false;
        Ui.Btn cinematics = new Ui.Btn("CINEMATICS", 599, 530, 168, 35, 24, null);
        cinematics.enabled = false;
        buttons.add(credits);
        buttons.add(cinematics);
        startMusic();
    }

    private void startMusic() {
        if (musicStarted || Session.fs == null) return;
        musicStarted = true;
        final String path = Res.find("music-intro", new String[]{"data\\global\\music\\introedit.wav"}, "music", "intro");
        if (path == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    AudioPlayer.play(Session.fs.read(path));
                } catch (Exception e) {
                    Res.note("музыка меню: " + e.getMessage());
                }
            }
        }, "jvd2-music").start();
    }

    @Override
    protected void render(Canvas c) {
        Ui.background(c, bg, sky);
        if (bg == null) Ui.text(c, "DIABLO II", 400, 80, 42, Ui.GOLD, 0);
        Ui.sprite(c, blackL, units, 0, 400, 120, false);
        Ui.sprite(c, blackR, units, 0, 400, 120, false);
        Ui.sprite(c, fireL, units, tick / 2, 400, 120, true);
        Ui.sprite(c, fireR, units, tick / 2, 400, 120, true);
    }

    @Override
    protected void tap(float x, float y) {
        if (x < 260 && y > 572) Nav.go(new ToolsScene());
    }

    @Override
    public boolean onBack() {
        Nav.exit();
        return true;
    }
}
