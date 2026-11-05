package com.jv.diablo2.game;

/** Всплывающая надпись над целью (урон, промах, блок). */
public final class FloatingText {
    public float x, y;
    public final String text;
    public final int color;
    public int ttl = 30;

    public FloatingText(float x, float y, String text, int color) {
        this.x = x;
        this.y = y;
        this.text = text;
        this.color = color;
    }
}
