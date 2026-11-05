package com.jv.diablo2.game;

/** Базовый объект мира. Координаты — в субтайлах (float), клетка = (int) координаты. */
public abstract class Entity {
    public float x, y;
    public int life = 1, maxLife = 1;
    public int level = 1;
    public boolean dead;

    public abstract void update(World w);

    public float distTo(float ox, float oy) {
        return (float) Math.hypot(ox - x, oy - y);
    }

    public float distTo(Entity o) {
        return distTo(o.x, o.y);
    }

    public void takeDamage(int dmg) {
        life -= dmg;
        if (life <= 0) {
            life = 0;
            dead = true;
        }
    }

    /** Шаг к точке длиной не более dist. Возвращает true, если точка достигнута. */
    protected boolean stepTo(float tx, float ty, float dist) {
        float dx = tx - x, dy = ty - y;
        float d = (float) Math.hypot(dx, dy);
        if (d <= dist) {
            x = tx;
            y = ty;
            return true;
        }
        x += dx / d * dist;
        y += dy / d * dist;
        return false;
    }
}
