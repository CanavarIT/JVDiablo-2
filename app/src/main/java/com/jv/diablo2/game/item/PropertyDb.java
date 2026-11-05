package com.jv.diablo2.game.item;

import com.jv.diablo2.data.TxtTable;
import com.jv.diablo2.game.D2Random;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Properties.txt: код свойства ("dmg%", "str", "res-fire") -> набор характеристик.
 * Поддержаны прямые свойства и урон (func 5/6/7); навыки, заряды и шанс умения (func 9+) сохраняются как "prop:код".
 */
public final class PropertyDb {
    private final Map<String, TxtTable.Row> rows = new HashMap<>();

    public static PropertyDb load(TxtTable t) {
        PropertyDb db = new PropertyDb();
        for (TxtTable.Row r : t.all()) {
            String code = r.get("code").toLowerCase(Locale.ROOT);
            if (!code.isEmpty()) db.rows.put(code, r);
        }
        return db;
    }

    public List<Item.Mod> resolve(String code, String param, int min, int max, D2Random rng) {
        List<Item.Mod> out = new ArrayList<>();
        int lo = Math.min(min, max), hi = Math.max(min, max);
        int rolled = rng.range(lo, hi);
        TxtTable.Row r = rows.get(code.toLowerCase(Locale.ROOT));
        if (r == null) {
            out.add(new Item.Mod("prop:" + code, param, rolled));
            return out;
        }
        for (int i = 1; i <= 7; i++) {
            int func = r.getInt("func" + i, 0);
            String stat = r.get("stat" + i).toLowerCase(Locale.ROOT);
            if (func == 0 && stat.isEmpty()) continue;
            int v = rolled;
            if (func == 5) {
                if (stat.isEmpty()) stat = "mindamage";
                v = lo;
            } else if (func == 6) {
                if (stat.isEmpty()) stat = "maxdamage";
                v = hi;
            } else if (func == 7 && stat.isEmpty()) {
                stat = "item_maxdamage_percent";
            }
            if (func >= 9 && func != 14) stat = "";          // сложные свойства — этап навыков
            if (stat.isEmpty()) stat = "prop:" + code;
            out.add(new Item.Mod(stat, param, v));
        }
        if (out.isEmpty()) out.add(new Item.Mod("prop:" + code, param, rolled));
        return out;
    }
}
