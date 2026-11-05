package com.jv.diablo2.game.item;

/** Базовый предмет из Armor.txt / Weapons.txt / Misc.txt. */
public final class ItemBase {
    public enum Kind { ARMOR, WEAPON, MISC }

    public Kind kind;
    public String code = "", name = "", type = "", type2 = "";
    public int level, levelReq, reqStr, reqDex, durability, invW = 1, invH = 1, sockets, speed, rarity = 1, cost;
    public int minAc, maxAc, block;
    public int minDmg, maxDmg, min2h, max2h, minMis, maxMis, strBonus, dexBonus;
    public boolean twoHanded, stackable, spawnable = true;
    public String normCode = "", uberCode = "", ultraCode = "", invFile = "";
}
