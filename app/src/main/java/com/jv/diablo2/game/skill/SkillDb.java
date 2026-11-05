package com.jv.diablo2.game.skill;

import com.jv.diablo2.data.TxtTable;
import com.jv.diablo2.game.CharClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Skills.txt (+ SkillDesc.txt для раскладки по вкладкам): навыки, требования, стоимость маны. */
public final class SkillDb {
    public static final class Skill {
        public String name = "", cls = "", desc = "";
        public int id, reqLevel, maxLvl = 20, lvlMana, manaShift, minMana, page, row, col, order;
        public double mana;
        public final String[] req = {"", "", ""};
        public final String[] calc = new String[6];
        public final String[] par = new String[8];
    }

    private final List<Skill> all = new ArrayList<>();
    private final Map<String, Skill> byName = new HashMap<>();

    public static String classCode(CharClass c) {
        switch (c) {
            case AMAZON: return "ama";
            case SORCERESS: return "sor";
            case NECROMANCER: return "nec";
            case PALADIN: return "pal";
            case BARBARIAN: return "bar";
            case DRUID: return "dru";
            default: return "ass";
        }
    }

    public static SkillDb load(TxtTable skills, TxtTable skillDesc) {
        SkillDb db = new SkillDb();
        Map<String, int[]> layout = new HashMap<>();
        if (skillDesc != null) {
            for (TxtTable.Row r : skillDesc.all()) {
                String k = r.get("skilldesc").toLowerCase(Locale.ROOT);
                if (!k.isEmpty()) layout.put(k, new int[]{r.getInt("SkillPage", 0), r.getInt("SkillRow", 0), r.getInt("SkillColumn", 0)});
            }
        }
        int order = 0;
        for (TxtTable.Row r : skills.all()) {
            String name = r.get("skill");
            if (name.isEmpty() || name.equalsIgnoreCase("Expansion")) continue;
            Skill s = new Skill();
            s.name = name;
            s.id = r.getInt("*Id", r.getInt("Id", order));
            s.cls = r.get("charclass").toLowerCase(Locale.ROOT);
            s.desc = r.get("skilldesc").toLowerCase(Locale.ROOT);
            s.reqLevel = r.getInt("reqlevel", 0);
            s.maxLvl = r.getInt("maxlvl", 20);
            if (s.maxLvl <= 0) s.maxLvl = 20;
            for (int k = 0; k < 3; k++) s.req[k] = r.get("reqskill" + (k + 1));
            try {
                s.mana = Double.parseDouble(r.get("mana").isEmpty() ? "0" : r.get("mana"));
            } catch (NumberFormatException e) {
                s.mana = 0;
            }
            s.lvlMana = r.getInt("lvlmana", 0);
            s.manaShift = r.getInt("manashift", 0);
            s.minMana = r.getInt("minmana", 0);
            for (int k = 0; k < 6; k++) s.calc[k] = r.get("calc" + (k + 1));
            for (int k = 0; k < 8; k++) s.par[k] = r.get("Param" + (k + 1));
            int[] l = layout.get(s.desc);
            if (l != null) {
                s.page = l[0];
                s.row = l[1];
                s.col = l[2];
            }
            s.order = order++;
            db.all.add(s);
            db.byName.put(name.toLowerCase(Locale.ROOT), s);
        }
        return db;
    }

    public Skill get(String name) {
        return name == null ? null : byName.get(name.toLowerCase(Locale.ROOT));
    }

    public List<Skill> forClass(CharClass c) {
        String code = classCode(c);
        List<Skill> r = new ArrayList<>();
        for (Skill s : all) if (s.cls.equals(code)) r.add(s);
        r.sort((a, b) -> a.page != b.page ? Integer.compare(a.page, b.page)
                : a.row != b.row ? Integer.compare(a.row, b.row)
                : a.col != b.col ? Integer.compare(a.col, b.col) : Integer.compare(a.order, b.order));
        return r;
    }

    /** Мана = (mana + lvlmana*(lvl-1)) * 2^manashift / 256, не меньше minmana. */
    public double manaCost(Skill s, int level) {
        double v = (s.mana + (double) s.lvlMana * Math.max(0, level - 1)) * Math.pow(2, s.manaShift) / 256.0;
        return Math.max(s.minMana, v);
    }
}
