package com.jv.diablo2.game;

/**
 * Семь классов с базовыми характеристиками и приростом (общеизвестные значения игры).
 * Этап 2: читать то же самое из CharStats.txt, чтобы поддерживать моды.
 */
public enum CharClass {
    AMAZON("Amazon", 20, 25, 20, 15, 50, 84, 15, 2.0, 1.0, 1.5, 3.0, 1.0, 1.5),
    SORCERESS("Sorceress", 10, 25, 10, 35, 40, 74, 35, 1.0, 1.0, 2.0, 2.0, 1.0, 2.0),
    NECROMANCER("Necromancer", 15, 25, 15, 25, 45, 79, 25, 1.5, 1.0, 2.0, 2.0, 1.0, 2.0),
    PALADIN("Paladin", 25, 20, 25, 15, 55, 89, 15, 2.0, 1.0, 1.5, 3.0, 1.0, 1.5),
    BARBARIAN("Barbarian", 30, 20, 25, 10, 55, 92, 10, 2.0, 1.0, 1.0, 4.0, 1.0, 1.0),
    DRUID("Druid", 15, 20, 25, 20, 55, 84, 20, 2.0, 1.0, 2.0, 2.0, 1.0, 2.0),
    ASSASSIN("Assassin", 20, 20, 20, 25, 50, 95, 25, 2.0, 1.25, 1.5, 3.0, 1.25, 1.75);

    public final String title;
    public final int str, dex, vit, ene;
    public final int startLife, startStamina, startMana;
    public final double lifePerLevel, staminaPerLevel, manaPerLevel;
    public final double lifePerVit, staminaPerVit, manaPerEne;

    CharClass(String title, int str, int dex, int vit, int ene, int life, int stamina, int mana,
              double lifeLvl, double stamLvl, double manaLvl,
              double lifeVit, double stamVit, double manaEne) {
        this.title = title;
        this.str = str;
        this.dex = dex;
        this.vit = vit;
        this.ene = ene;
        this.startLife = life;
        this.startStamina = stamina;
        this.startMana = mana;
        this.lifePerLevel = lifeLvl;
        this.staminaPerLevel = stamLvl;
        this.manaPerLevel = manaLvl;
        this.lifePerVit = lifeVit;
        this.staminaPerVit = stamVit;
        this.manaPerEne = manaEne;
    }
}
