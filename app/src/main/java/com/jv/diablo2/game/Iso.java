package com.jv.diablo2.game;

/** Изометрия Diablo 2: 1 субтайл = 32x16 пикселей на экране (тайл 5x5 субтайлов = 160x80). */
public final class Iso {
    private Iso() {
    }

    public static final float SUB_W = 32f;
    public static final float SUB_H = 16f;

    public static float toScreenX(float x, float y) {
        return (x - y) * (SUB_W / 2f);
    }

    public static float toScreenY(float x, float y) {
        return (x + y) * (SUB_H / 2f);
    }

    public static float toWorldX(float sx, float sy) {
        return (sx / (SUB_W / 2f) + sy / (SUB_H / 2f)) / 2f;
    }

    public static float toWorldY(float sx, float sy) {
        return (sy / (SUB_H / 2f) - sx / (SUB_W / 2f)) / 2f;
    }

    /** Вектор направления: из мира (dx, dy) в экранный (для выбора направления спрайта). */
    public static float dirX(float dx, float dy) {
        return (dx - dy) * (SUB_W / 2f);
    }

    public static float dirY(float dx, float dy) {
        return (dx + dy) * (SUB_H / 2f);
    }
}
