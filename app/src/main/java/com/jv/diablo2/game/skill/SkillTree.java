package com.jv.diablo2.game.skill;

import com.jv.diablo2.game.PlayerStats;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Вложенные очки навыков героя. */
public final class SkillTree {
    private final Map<String, Integer> levels = new LinkedHashMap<>();

    public int level(String name) {
        Integer v = levels.get(name.toLowerCase(Locale.ROOT));
        return v == null ? 0 : v;
    }

    public Map<String, Integer> all() {
        return new LinkedHashMap<>(levels);
    }

    public void set(String name, int lvl) {
        if (lvl <= 0) levels.remove(name.toLowerCase(Locale.ROOT));
        else levels.put(name.toLowerCase(Locale.ROOT), lvl);
    }

    /** Возвращает причину отказа или null, если очко вложить можно. */
    public String canAllocate(SkillDb.Skill s, PlayerStats p, SkillDb db) {
        if (p.skillPoints <= 0) return "Нет очков навыков";
        int cur = level(s.name);
        if (cur >= s.maxLvl) return "Максимальный уровень";
        if (cur == 0 && p.clvl < s.reqLevel) return "Нужен уровень " + s.reqLevel;
        for (String r : s.req) {
            if (!r.isEmpty() && level(r) <= 0) return "Сначала изучите: " + r;
        }
        return null;
    }

    public boolean allocate(SkillDb.Skill s, PlayerStats p, SkillDb db) {
        if (canAllocate(s, p, db) != null) return false;
        set(s.name, level(s.name) + 1);
        p.skillPoints--;
        return true;
    }

    /** Контекст для Calc: lvl навыка, par1..8 и уровни других навыков. */
    public Calc.Ctx ctxFor(final SkillDb.Skill s, final PlayerStats p) {
        return new Calc.Ctx() {
            @Override
            public double var(String n) {
                if (n.equals("lvl")) return level(s.name);
                if (n.equals("clvl") || n.equals("ulvl")) return p.clvl;
                if (n.startsWith("par") && n.length() == 4) {
                    int k = n.charAt(3) - '1';
                    if (k >= 0 && k < 8 && s.par[k] != null) {
                        try {
                            return Double.parseDouble(s.par[k]);
                        } catch (NumberFormatException e) {
                            return 0;
                        }
                    }
                }
                switch (n) {
                    case "stat:strength": return p.totalStr();
                    case "stat:dexterity": return p.totalDex();
                    case "stat:vitality": return p.totalVit();
                    case "stat:energy": return p.totalEne();
                    default: return 0;
                }
            }

            @Override
            public double skill(String name, String field) {
                return level(name);
            }
        };
    }
}
