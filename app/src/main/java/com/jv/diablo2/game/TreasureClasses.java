package com.jv.diablo2.game;

import com.jv.diablo2.data.TxtTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Классы сокровищ (TreasureClass.txt / TreasureClassEx.txt): выбор по весам, NoDrop,
 * отрицательные Picks, вложенные классы и автоповышение по уровню монстра внутри группы.
 * Качество предметов (unique/set/rare/magic, MF) и выбор конкретного предмета по типу — этап 2.
 */
public final class TreasureClasses {
    private static final class Tc {
        String name;
        int group, level, picks, noDrop;
        final List<String> items = new ArrayList<>();
        final List<Integer> probs = new ArrayList<>();
    }

    private final Map<String, Tc> byName = new HashMap<>();
    private final Map<Integer, TreeMap<Integer, Tc>> groups = new HashMap<>();

    public static TreasureClasses load(TxtTable t) {
        TreasureClasses r = new TreasureClasses();
        for (TxtTable.Row row : t.all()) {
            String name = row.get("Treasure Class");
            if (name.isEmpty()) name = row.first();
            if (name.isEmpty() || name.equalsIgnoreCase("Expansion")) continue;
            Tc tc = new Tc();
            tc.name = name;
            tc.group = row.getInt("group", 0);
            tc.level = row.getInt("level", 0);
            tc.picks = row.getInt("Picks", 1);
            tc.noDrop = row.getInt("NoDrop", 0);
            for (int i = 1; i <= 10; i++) {
                String item = row.get("Item" + i);
                int prob = row.getInt("Prob" + i, 0);
                if (item.isEmpty()) continue;
                tc.items.add(item);
                tc.probs.add(prob);
            }
            r.byName.put(name.toLowerCase(Locale.ROOT), tc);
            if (tc.group > 0) r.groups.computeIfAbsent(tc.group, k -> new TreeMap<>()).put(tc.level, tc);
        }
        return r;
    }

    public boolean has(String name) {
        return byName.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public int size() {
        return byName.size();
    }

    /** Возвращает коды выпавших предметов ("gld", "r01", "weap3", "armo6", ...). */
    public List<String> roll(String tcName, int monsterLevel, int players, D2Random rng) {
        List<String> out = new ArrayList<>();
        Tc tc = byName.get(tcName.toLowerCase(Locale.ROOT));
        if (tc != null) rollInto(tc, monsterLevel, Math.max(1, players), rng, out, 0);
        return out;
    }

    private Tc upgrade(Tc tc, int mlvl) {
        if (tc.group <= 0) return tc;
        TreeMap<Integer, Tc> g = groups.get(tc.group);
        if (g == null) return tc;
        Map.Entry<Integer, Tc> e = g.floorEntry(mlvl);
        return (e != null && e.getKey() > tc.level) ? e.getValue() : tc;
    }

    private void rollInto(Tc tc, int mlvl, int players, D2Random rng, List<String> out, int depth) {
        if (depth > 16) return;
        tc = upgrade(tc, mlvl);
        if (tc.picks < 0) {
            int remaining = -tc.picks;
            for (int i = 0; i < tc.items.size() && remaining > 0; i++) {
                int count = Math.min(Math.max(1, tc.probs.get(i)), remaining);
                for (int k = 0; k < count; k++) emit(tc.items.get(i), mlvl, players, rng, out, depth);
                remaining -= count;
            }
            return;
        }
        int sum = 0;
        for (int p : tc.probs) sum += p;
        if (sum <= 0) return;
        double nd = tc.noDrop;
        if (nd > 0 && players > 1) {
            double ratio = (sum + nd) / nd;
            nd = sum / (Math.pow(ratio, players) - 1.0);
        }
        int noDrop = (int) Math.round(nd);
        for (int pick = 0; pick < tc.picks; pick++) {
            int r = rng.nextInt(sum + noDrop);
            if (r < noDrop) continue;
            r -= noDrop;
            for (int i = 0; i < tc.items.size(); i++) {
                r -= tc.probs.get(i);
                if (r < 0) {
                    emit(tc.items.get(i), mlvl, players, rng, out, depth);
                    break;
                }
            }
        }
    }

    private void emit(String item, int mlvl, int players, D2Random rng, List<String> out, int depth) {
        Tc nested = byName.get(item.toLowerCase(Locale.ROOT));
        if (nested != null) rollInto(nested, mlvl, players, rng, out, depth + 1);
        else out.add(item);
    }
}
