package com.jv.diablo2.game;

import com.jv.diablo2.game.item.Inventory;
import com.jv.diablo2.game.skill.SkillTree;

/** Всё, что относится к персонажу и сохраняется: статы, инвентарь, навыки, квесты. */
public final class Hero {
    public String name = "Hero";
    public final PlayerStats stats;
    public final Inventory inv = new Inventory();
    public final SkillTree skills = new SkillTree();
    public final QuestLog quests = new QuestLog();

    public Hero(CharClass cls) {
        this.stats = new PlayerStats(cls);
    }
}
