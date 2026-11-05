package com.jv.diablo2;

import android.graphics.Canvas;

/** Экран игры. update() вызывается 25 раз в секунду (как тики оригинала), draw() — по мере возможности. */
public interface Scene {
    void update();

    void draw(Canvas c, int w, int h);

    /** action — константа MotionEvent (ACTION_DOWN/MOVE/UP). */
    void onTouch(int action, float x, float y);

    /** Кнопка «назад». true — сцена обработала сама. */
    default boolean onBack() {
        return false;
    }
}
