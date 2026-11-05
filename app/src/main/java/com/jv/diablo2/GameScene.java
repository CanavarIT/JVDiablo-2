package com.jv.diablo2;

import com.jv.diablo2.render.Fb;
import com.jv.diablo2.render.GameFrame;

/** Игра: город/уровень, герой, панель. Вся логика и рисование — в GameFrame (проверяется на JVM). */
final class GameScene extends FbScene {
    private final GameFrame game;

    GameScene(GameFrame game) {
        super(640, 480);
        this.game = game;
        game.listener = new GameFrame.Listener() {
            @Override
            public void onMini(String name) {
                if (name.equals("menu")) Nav.go(new MainMenuScene());
                else Nav.toast("Эта панель ещё не перенесена в новый движок: " + name);
            }
        };
    }

    @Override
    protected void onTick() {
        game.update();
    }

    @Override
    protected void render(Fb fb) {
        game.render(fb);
    }

    @Override
    protected void onPointer(int action, int x, int y) {
        game.pointer(action, x, y);
    }

    @Override
    public boolean onBack() {
        Nav.go(new MainMenuScene());
        return true;
    }
}
