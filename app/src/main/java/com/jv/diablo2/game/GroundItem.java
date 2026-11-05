package com.jv.diablo2.game;

import com.jv.diablo2.game.item.Item;

/** Предмет или золото на земле. */
public final class GroundItem {
    public final float x, y;
    public final String code;
    public Item item;     // null для золота и для кодов без таблиц
    public int gold;

    public GroundItem(float x, float y, String code) {
        this.x = x;
        this.y = y;
        this.code = code;
    }

    public String label() {
        if (gold > 0) return gold + " з.";
        return item != null ? item.displayName() : code;
    }
}
