package com.jv.diablo2;

import com.jv.diablo2.game.Hero;
import com.jv.diablo2.gfx.Dc6;
import com.jv.diablo2.render.Assets;
import com.jv.diablo2.render.Fb;
import com.jv.diablo2.render.GameFrame;
import com.jv.diablo2.render.Text;

/**
 * Экран загрузки как в оригинале: чёрный фон и один кадр 256x256 из loadingscreen.dc6 по центру (надпись LOADING
 * нарисована в самом кадре); кадры — анимация открывающейся двери, номер кадра растёт вместе с прогрессом загрузки.
 */
final class LoadingScene extends FbScene {
    private static final int MIN_TICKS = 30;     // дверь успевает открыться даже при мгновенной загрузке
    private final Assets as = LevelLoader.assets();
    private final Dc6 frames = as.dc6("data\\global\\ui\\loading\\loadingscreen.dc6");
    private final int[] pal = as.pal("loading");
    private volatile GameFrame loaded;
    private volatile String err = "";
    private int errTicks;

    LoadingScene(final Hero hero) {
        super(640, 480);
        GameFrame.progress = 0f;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LevelLoader.Result r = LevelLoader.load(1, hero);
                    loaded = r.frame;
                } catch (Throwable e) {
                    err = String.valueOf(e);
                    Res.note("ЗАГРУЗКА КАРТЫ: " + e);
                }
            }
        }, "jvd2-level").start();
    }

    @Override
    protected void onTick() {
        if (!err.isEmpty()) {
            if (++errTicks > 100) Nav.go(new CharSelectScene());
            return;
        }
        GameFrame g = loaded;
        if (g != null && tick >= MIN_TICKS) Nav.go(new GameScene(g));
    }

    @Override
    protected void render(Fb fb) {
        fb.clear(0xFF000000);
        if (frames != null) {
            float p = Math.max(GameFrame.progress, Math.min(1f, tick / (float) MIN_TICKS));
            int idx = Math.min(frames.frames.length - 1, (int) (p * (frames.frames.length - 1) + 0.5f));
            Dc6.Frame f = frames.frames[idx];
            fb.blit(f.pixels, f.width, f.height, 320 - f.width / 2, 240 - f.height / 2, pal);
        } else {
            Text.draw(fb, as, 30, "LOADING...", 320, 220, Text.GOLD, Text.CENTER);
        }
        if (!err.isEmpty()) Text.drawShadow(fb, as, 16, err.length() > 70 ? err.substring(0, 70) : err, 320, 440, Text.RED, Text.CENTER);
    }
}
