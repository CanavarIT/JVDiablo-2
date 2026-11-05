package com.jv.diablo2.game;

import com.jv.diablo2.data.TxtTable;

/** Параметры монстра из MonStats.txt (сложность Normal; масштабирование MonLvl — этап 2). */
public final class MonsterTemplate {
    public String id = "fallen1";
    public int level = 1;
    public int minHp = 6, maxHp = 9;
    public int ac = 5;
    public int exp = 6;
    public int minDmg = 1, maxDmg = 3;
    public int attackRating = 10;
    public float walk = 6f;
    public float run = 6f;
    public String treasureClass = "";
    /** Ключ строки имени (NameStr), размер группы (MinGrp..MaxGrp). */
    public String nameStr = "";
    public int minGrp = 1, maxGrp = 1;

    public static MonsterTemplate fallback() {
        return new MonsterTemplate();
    }

    public static MonsterTemplate fromRow(TxtTable.Row r) {
        MonsterTemplate m = new MonsterTemplate();
        m.id = r.get("Id");
        m.level = Math.max(1, r.getInt("Level", 1));
        m.minHp = Math.max(1, r.getInt("minHP", 6));
        m.maxHp = Math.max(m.minHp, r.getInt("maxHP", 9));
        m.ac = r.getInt("AC", 5);
        m.exp = r.getInt("Exp", 6);
        m.minDmg = r.getInt("A1MinD", 1);
        m.maxDmg = Math.max(m.minDmg, r.getInt("A1MaxD", 3));
        m.attackRating = r.getInt("A1TH", 10);
        m.walk = Math.max(1, r.getInt("Velocity", 6));
        m.run = Math.max(m.walk, r.getInt("Run", (int) m.walk));
        m.treasureClass = r.get("TreasureClass1");
        m.nameStr = r.get("NameStr");
        m.minGrp = Math.max(1, r.getInt("MinGrp", 1));
        m.maxGrp = Math.max(m.minGrp, r.getInt("MaxGrp", m.minGrp));
        return m;
    }
}
