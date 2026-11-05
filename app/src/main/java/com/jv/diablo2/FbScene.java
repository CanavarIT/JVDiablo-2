package com.jv.diablo2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import com.jv.diablo2.render.Fb;

/**
 * Сцена с программным отрисовщиком: вся картинка собирается в Fb (640x480 в игре, 800x600 в меню) на чистой Java,
 * а здесь буфер лишь копируется в Bitmap и растягивается на экран без сглаживания с сохранением пропорций 4:3.
 * Касания приходят в координатах буфера.
 */
abstract class FbScene implements Scene {
    protected final Fb fb;
    private final Bitmap bmp;
    private final Paint paint = new Paint();
    private final Rect src, dst = new Rect();
    private float scale = 1, ox, oy;
    protected int tick;

    protected FbScene(int w, int h) {
        fb = new Fb(w, h);
        bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        src = new Rect(0, 0, w, h);
        paint.setFilterBitmap(false);
    }

    /** Один тик логики (25 в секунду). */
    protected void onTick() {
    }

    /** Рисует кадр в fb. */
    protected abstract void render(Fb fb);

    /** action — 0 вниз, 1 вверх, 2 движение (как в MotionEvent); x, y — в пикселях буфера. */
    protected void onPointer(int action, int x, int y) {
    }

    @Override
    public final synchronized void update() {
        tick++;
        onTick();
    }

    @Override
    public final synchronized void draw(Canvas c, int w, int h) {
        render(fb);
        bmp.setPixels(fb.px, 0, fb.w, 0, 0, fb.w, fb.h);
        scale = Math.min(w / (float) fb.w, h / (float) fb.h);
        float dw = fb.w * scale, dh = fb.h * scale;
        ox = (w - dw) / 2f;
        oy = (h - dh) / 2f;
        c.drawColor(0xFF000000);
        dst.set(Math.round(ox), Math.round(oy), Math.round(ox + dw), Math.round(oy + dh));
        c.drawBitmap(bmp, src, dst, paint);
    }

    @Override
    public final synchronized void onTouch(int action, float x, float y) {
        int lx = Math.round((x - ox) / scale), ly = Math.round((y - oy) / scale);
        onPointer(action, Math.max(-1000, Math.min(fb.w + 1000, lx)), Math.max(-1000, Math.min(fb.h + 1000, ly)));
    }
}
