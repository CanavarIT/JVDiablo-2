package com.jv.diablo2;

import android.graphics.Canvas;

import com.jv.diablo2.game.CharClass;

/** Служебные инструменты: просмотр ресурсов и карт, тестовая арена, диагностика, выбор папки с MPQ. */
final class ToolsScene extends Screen800 {
    ToolsScene() {
        add("RESOURCES (DC6 / WAV)", 120, new Runnable() {
            @Override
            public void run() {
                Nav.go(new ViewerScene());
            }
        });
        add("MAPS (DS1 / DT1)", 170, new Runnable() {
            @Override
            public void run() {
                Nav.go(new MapScene());
            }
        });
        add("TEST ARENA", 220, new Runnable() {
            @Override
            public void run() {
                Nav.go(new SandboxScene(CharClass.BARBARIAN));
            }
        });
        add("DIAGNOSTICS / SEND REPORT", 270, new Runnable() {
            @Override
            public void run() {
                Nav.go(new DiagScene());
            }
        });
        add("CHOOSE MPQ FOLDER", 320, new Runnable() {
            @Override
            public void run() {
                Nav.act.pickDataFolder();
            }
        });
        add("BACK", 400, new Runnable() {
            @Override
            public void run() {
                Nav.go(new MainMenuScene());
            }
        });
    }

    private void add(String label, float y, Runnable r) {
        buttons.add(new Ui.Btn(label, 264, y, 272, 35, 16, r));
    }

    @Override
    protected void render(Canvas c) {
        Ui.text(c, "TOOLS", 400, 40, 42, Ui.GOLD, 0);
        Ui.text(c, Session.status, 400, 540, 16, Ui.GRAY, 0);
    }

    @Override
    public boolean onBack() {
        Nav.go(new MainMenuScene());
        return true;
    }
}
