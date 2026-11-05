package com.jv.diablo2.game;

/** Характеристики героя и производные величины (жизнь, мана, защита, рейтинг атаки). */
public final class PlayerStats {
    public final CharClass cls;
    public int clvl = 1;
    public long exp;
    public int str, dex, vit, ene;                 // базовые (вложенные) значения
    public int statPoints, skillPoints;
    public int life, mana, stamina;

    // Бонусы от экипировки (Inventory.recompute заполняет их заново)
    public int bonusStr, bonusDex, bonusVit, bonusEne;
    public int weaponMin = 2, weaponMax = 7;
    public int weaponStrBonus = 100, weaponDexBonus = 0;   // % за очко (как в Weapons.txt)
    public int edPercent, flatDamage;
    public int bonusDefense, bonusAttackRating, arPercent;
    public int shieldBlock;
    public int deadlyStrike, crushingBlow;
    public int bonusLife, bonusMana;
    public int magicFind, goldFind;

    public PlayerStats(CharClass cls) {
        this.cls = cls;
        this.str = cls.str;
        this.dex = cls.dex;
        this.vit = cls.vit;
        this.ene = cls.ene;
        this.life = maxLife();
        this.mana = maxMana();
        this.stamina = maxStamina();
    }

    public int totalStr() {
        return str + bonusStr;
    }

    public int totalDex() {
        return dex + bonusDex;
    }

    public int totalVit() {
        return vit + bonusVit;
    }

    public int totalEne() {
        return ene + bonusEne;
    }

    public int maxLife() {
        return (int) Math.floor(cls.startLife + (totalVit() - cls.vit) * cls.lifePerVit + (clvl - 1) * cls.lifePerLevel) + bonusLife;
    }

    public int maxMana() {
        return (int) Math.floor(cls.startMana + (totalEne() - cls.ene) * cls.manaPerEne + (clvl - 1) * cls.manaPerLevel) + bonusMana;
    }

    public int maxStamina() {
        return (int) Math.floor(cls.startStamina + (totalVit() - cls.vit) * cls.staminaPerVit + (clvl - 1) * cls.staminaPerLevel);
    }

    public int defense() {
        return totalDex() / 4 + bonusDefense;
    }

    public int attackRating() {
        int ar = totalDex() * 5 - 35 + bonusAttackRating;
        return Math.max(0, ar * (100 + arPercent) / 100);
    }

    /** Начисляет опыт, повышает уровень(и). Возвращает число полученных уровней. */
    public int gainExp(long amount, Experience table) {
        exp += amount;
        int gained = 0;
        while (clvl < table.maxLevel(cls) && exp >= table.totalToReach(cls, clvl + 1)) {
            clvl++;
            statPoints += 5;
            skillPoints += 1;
            gained++;
            life = maxLife();
            mana = maxMana();
            stamina = maxStamina();
        }
        return gained;
    }

    public boolean spendStat(char which) {
        if (statPoints <= 0) return false;
        switch (which) {
            case 's': str++; break;
            case 'd': dex++; break;
            case 'v': vit++; break;
            case 'e': ene++; break;
            default: return false;
        }
        statPoints--;
        return true;
    }
}
