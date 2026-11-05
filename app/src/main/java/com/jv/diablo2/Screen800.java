package com.jv.diablo2;

import android.graphics.Canvas;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

/** Основа стартовых экранов: виртуальный экран 800x600 с масштабированием и кнопками. */
abstract class Screen800 implements Scene {
    protected final List<Ui.Btn> buttons = new ArrayList<>();
    protected int tick;
    private float scale = 1, ox, oy, lastY;
    private Ui.Btn down;
    private boolean moved;

    @Override
    public void update() {
        tick++;
        onTick();
    }

    protected void onTick() {
    }

    protected abstract void render(Canvas c);

    protected void tap(float x, float y) {
    }

    protected void drag(float dy) {
    }

    @Override
    public final void draw(Canvas c, int w, int h) {
        scale = Math.min(w / 800f, h / 600f);
        ox = (w - 800 * scale) / 2f;
        oy = (h - 600 * scale) / 2f;
        c.drawColor(0xFF000000);
        c.save();
        c.translate(ox, oy);
        c.scale(scale, scale);
        c.clipRect(0, 0, 800, 600);
        render(c);
        for (Ui.Btn b : buttons) Ui.button(c, b);
        c.restore();
    }

    @Override
    public void onTouch(int action, float sx, float sy) {
        float x = (sx - ox) / scale, y = (sy - oy) / scale;
        if (action == MotionEvent.ACTION_DOWN) {
            down = null;
            moved = false;
            lastY = y;
            for (Ui.Btn b : buttons) if (b.enabled && b.contains(x, y)) down = b;
            if (down != null) down.pressed = true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (Math.abs(y - lastY) > 3) moved = true;
            if (down != null) down.pressed = down.contains(x, y);
            else drag(y - lastY);
            lastY = y;
        } else if (action == MotionEvent.ACTION_UP) {
            Ui.Btn b = down;
            down = null;
            if (b != null) {
                boolean hit = b.pressed && b.contains(x, y);
                b.pressed = false;
                if (hit && b.action != null) b.action.run();
            } else if (!moved) {
                tap(x, y);
            }
        }
    }
}
