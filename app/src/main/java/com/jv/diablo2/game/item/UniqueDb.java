package com.jv.diablo2.game.item;

import com.jv.diablo2.data.TxtTable;
import com.jv.diablo2.game.D2Random;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** UniqueItems.txt: уникальные предметы по коду базы. */
public final class UniqueDb {
    public static final class Unique {
        public String name = "", code = "";
        public int rarity = 1, level, levelReq;
        public final List<String[]> props = new ArrayList<>();   // {код, параметр, min, max}
    }

    private final List<Unique> list = new ArrayList<>();

    public static UniqueDb load(TxtTable t) {
        UniqueDb db = new UniqueDb();
        for (TxtTable.Row r : t.all()) {
            String name = r.get("index");
            String code = r.get("code").toLowerCase(Locale.ROOT);
            if (name.isEmpty() || code.isEmpty() || name.equalsIgnoreCase("Expansion")) continue;
            if (r.getInt("enabled", 1) == 0) continue;
            Unique u = new Unique();
            u.name = name;
            u.code = code;
            u.rarity = Math.max(1, r.getInt("rarity", 1));
            u.level = r.getInt("lvl", 0);
            u.levelReq = r.getInt("lvl req", 0);
            for (int i = 1; i <= 12; i++) {
                String p = r.get("prop" + i);
                if (p.isEmpty()) continue;
                u.props.add(new String[]{p, r.get("par" + i), String.valueOf(r.getInt("min" + i, 0)), String.valueOf(r.getInt("max" + i, 0))});
            }
            db.list.add(u);
        }
        return db;
    }

    public Unique pick(ItemBase base, int ilvl, D2Random rng) {
        List<Unique> cand = new ArrayList<>();
        int total = 0;
        for (Unique u : list) {
            if (u.code.equals(base.code) && u.level <= ilvl) {
                cand.add(u);
                total += u.rarity;
            }
        }
        if (cand.isEmpty()) return null;
        int r = rng.nextInt(total);
        for (Unique u : cand) {
            r -= u.rarity;
            if (r < 0) return u;
        }
        return cand.get(0);
    }
}
