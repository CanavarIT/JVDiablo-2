package com.jv.diablo2.game;

import com.jv.diablo2.data.TxtTable;

import java.util.ArrayList;
import java.util.List;

/**
 * Таблица опыта из Experience.txt. Значение в строке N — суммарный опыт, нужный для достижения уровня N+1.
 * Без данных игры используется грубая запасная кривая (только для песочницы).
 */
public final class Experience {
    private final long[][] need = new long[CharClass.values().length][];

    public static Experience fallback() {
        Experience e = new Experience();
        long[] first = {500, 1500, 3750, 7875, 14175, 22680, 32886, 44396, 57715, 72144};
        for (int c = 0; c < e.need.length; c++) {
            long[] t = new long[98];
            for (int i = 0; i < t.length; i++) {
                t[i] = i < first.length ? first[i] : (long) (t[i - 1] * 1.24);
            }
            e.need[c] = t;
        }
        return e;
    }

    public static Experience fromTable(TxtTable t) {
        Experience e = new Experience();
        CharClass[] classes = CharClass.values();
        for (int c = 0; c < classes.length; c++) {
            List<Long> list = new ArrayList<>();
            for (TxtTable.Row r : t.all()) {
                String lvl = r.first();
                if (lvl.equalsIgnoreCase("Expansion")) break;   // дальше идёт блок-разделитель
                int level;
                try {
                    level = Integer.parseInt(lvl);
                } catch (NumberFormatException ex) {
                    continue;                                   // строка MaxLvl и т.п.
                }
                String v = r.get(classes[c].title);
                if (v.isEmpty()) continue;
                while (list.size() < level - 1) list.add(Long.MAX_VALUE);
                list.add(Long.parseLong(v));
            }
            if (list.isEmpty()) return fallback();
            e.need[c] = new long[list.size()];
            for (int i = 0; i < list.size(); i++) e.need[c][i] = list.get(i);
        }
        return e;
    }

    /** Суммарный опыт, нужный чтобы достичь указанного уровня. */
    public long totalToReach(CharClass c, int level) {
        if (level <= 1) return 0;
        long[] t = need[c.ordinal()];
        int last = Math.min(level - 1, t.length);
        return t[last - 1];                       // значения в таблице накопительные
    }

    public int maxLevel(CharClass c) {
        return Math.min(99, need[c.ordinal()].length + 1);
    }
}
