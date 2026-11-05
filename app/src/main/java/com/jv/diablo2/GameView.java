package com.jv.diablo2;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** Поверхность с игровым циклом: фиксированные 25 тиков/с + отрисовка. */
public final class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final long TICK_NANOS = 1_000_000_000L / 25;

    private volatile Scene scene;
    private volatile boolean running;
    private Thread thread;

    public GameView(Context ctx) {
        super(ctx);
        getHolder().addCallback(this);
    }

    public void setScene(Scene s) {
        scene = s;
    }

    public Scene scene() {
        return scene;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        thread = new Thread(this, "jvd2-loop");
        thread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        try {
            if (thread != null) thread.join(500);
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        long acc = 0;
        while (running) {
            long now = System.nanoTime();
            acc += now - last;
            last = now;
            Scene s = scene;
            if (s != null) {
                int guard = 0;
                while (acc >= TICK_NANOS && guard++ < 5) {
                    try {
                        s.update();
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                    }
                    acc -= TICK_NANOS;
                }
                if (acc > TICK_NANOS * 5) acc = 0;
                Canvas c = null;
                try {
                    c = getHolder().lockCanvas();
                    if (c != null) s.draw(c, c.getWidth(), c.getHeight());
                } catch (RuntimeException e) {
                    e.printStackTrace();
                } finally {
                    if (c != null) getHolder().unlockCanvasAndPost(c);
                }
            }
            try {
                Thread.sleep(8);
            } catch (InterruptedException ignored) {
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        Scene s = scene;
        if (s != null) s.onTouch(e.getActionMasked(), e.getX(), e.getY());
        return true;
    }
}
