package com.jv.diablo2.game;

/** Боевые формулы. */
public final class Combat {
    private Combat() {
    }

    /** Шанс попасть: 2*alvl/(alvl+dlvl) * AR/(AR+DEF), зажато в 5..95%. */
    public static double hitChance(int attackRating, int attackerLevel, int defense, int defenderLevel) {
        if (attackRating <= 0) return 5;
        double c = 200.0 * attackerLevel / (attackerLevel + Math.max(1, defenderLevel))
                * attackRating / (double) (attackRating + Math.max(0, defense));
        return Math.max(5, Math.min(95, c));
    }

    /** Блок щитом: щит% * (dex-15) / (2*clvl), максимум 75%. */
    public static double blockChance(int shieldBlockPercent, int dex, int clvl) {
        if (shieldBlockPercent <= 0) return 0;
        double c = shieldBlockPercent * (dex - 15) / (clvl * 2.0);
        return Math.max(0, Math.min(75, c));
    }

    /** Урон оружием: базовый бросок * (100 + ED% + str*strBonus/100 + dex*dexBonus/100)% + плоский. */
    public static int rollWeaponDamage(D2Random rng, PlayerStats p) {
        int base = rng.range(p.weaponMin, p.weaponMax);
        double mult = 100.0 + p.edPercent + p.totalStr() * p.weaponStrBonus / 100.0 + p.totalDex() * p.weaponDexBonus / 100.0;
        int dmg = (int) Math.floor(base * mult / 100.0) + p.flatDamage;
        if (rng.chance(p.deadlyStrike)) dmg *= 2;
        return Math.max(1, dmg);
    }

    /** Сопротивление уменьшает урон в процентах (максимум 75%). */
    public static int applyResist(int damage, int resistPercent) {
        int r = Math.max(-100, Math.min(75, resistPercent));
        return Math.max(0, damage * (100 - r) / 100);
    }
}
