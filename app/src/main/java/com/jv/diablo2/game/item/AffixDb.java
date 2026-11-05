package com.jv.diablo2.game.item;

import com.jv.diablo2.data.TxtTable;
import com.jv.diablo2.game.D2Random;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** MagicPrefix/MagicSuffix (+ имена для редких из RarePrefix/RareSuffix). */
public final class AffixDb {
    public static final class Affix {
        public String name = "";
        public int level, maxLevel, levelReq, freq = 1, group;
        public boolean spawnable = true, classSpecific;
        public final List<String[]> mods = new ArrayList<>();   // {код, параметр, min, max}
        public final List<String> itypes = new ArrayList<>(), etypes = new ArrayList<>();
    }

    private final ItemTypeDb types;
    private final List<Affix> prefixes = new ArrayList<>(), suffixes = new ArrayList<>();
    private final List<Affix> rarePre = new ArrayList<>(), rareSuf = new ArrayList<>();

    public AffixDb(ItemTypeDb types, TxtTable prefix, TxtTable suffix, TxtTable rarePrefix, TxtTable rareSuffix) {
        this.types = types;
        read(prefix, prefixes, true);
        read(suffix, suffixes, true);
        if (rarePrefix != null) read(rarePrefix, rarePre, false);
        if (rareSuffix != null) read(rareSuffix, rareSuf, false);
    }

    private static void read(TxtTable t, List<Affix> out, boolean mods) {
        for (TxtTable.Row r : t.all()) {
            String name = r.get("Name");
            if (name.isEmpty() || name.equalsIgnoreCase("Expansion")) continue;
            Affix a = new Affix();
            a.name = name;
            a.spawnable = r.getInt("spawnable", 1) != 0;
            a.level = r.getInt("level", 0);
            a.maxLevel = r.getInt("maxlevel", 0);
            a.levelReq = r.getInt("levelreq", 0);
            a.classSpecific = !r.get("classspecific").isEmpty();
            a.freq = Math.max(1, r.getInt("frequency", 1));
            a.group = r.getInt("group", 0);
            if (mods) {
                for (int i = 1; i <= 3; i++) {
                    String code = r.get("mod" + i + "code");
                    if (code.isEmpty()) continue;
                    a.mods.add(new String[]{code, r.get("mod" + i + "param"),
                            String.valueOf(r.getInt("mod" + i + "min", 0)), String.valueOf(r.getInt("mod" + i + "max", 0))});
                }
            }
            for (int i = 1; i <= 7; i++) {
                String s = r.get("itype" + i).toLowerCase(Locale.ROOT);
                if (!s.isEmpty()) a.itypes.add(s);
            }
            for (int i = 1; i <= 5; i++) {
                String s = r.get("etype" + i).toLowerCase(Locale.ROOT);
                if (!s.isEmpty()) a.etypes.add(s);
            }
            out.add(a);
        }
    }

    private boolean matches(Affix a, ItemBase b) {
        boolean ok = false;
        for (String t : a.itypes) {
            if (types.isA(b.type, t) || types.isA(b.type2, t)) {
                ok = true;
                break;
            }
        }
        if (!ok) return false;
        for (String t : a.etypes) {
            if (types.isA(b.type, t) || types.isA(b.type2, t)) return false;
        }
        return true;
    }

    public Affix pickPrefix(ItemBase b, int mlvl, Set<Integer> usedGroups, D2Random rng) {
        return pick(prefixes, b, mlvl, usedGroups, rng);
    }

    public Affix pickSuffix(ItemBase b, int mlvl, Set<Integer> usedGroups, D2Random rng) {
        return pick(suffixes, b, mlvl, usedGroups, rng);
    }

    private Affix pick(List<Affix> list, ItemBase b, int mlvl, Set<Integer> used, D2Random rng) {
        List<Affix> cand = new ArrayList<>();
        int total = 0;
        for (Affix a : list) {
            if (!a.spawnable || a.classSpecific || a.level > mlvl) continue;
            if (a.maxLevel > 0 && mlvl > a.maxLevel) continue;
            if (a.group != 0 && used.contains(a.group)) continue;
            if (!matches(a, b)) continue;
            cand.add(a);
            total += a.freq;
        }
        if (cand.isEmpty()) return null;
        int r = rng.nextInt(total);
        for (Affix a : cand) {
            r -= a.freq;
            if (r < 0) {
                if (a.group != 0) used.add(a.group);
                return a;
            }
        }
        return null;
    }

    public String rareName(boolean prefix, ItemBase b, D2Random rng) {
        List<Affix> list = prefix ? rarePre : rareSuf;
        if (list.isEmpty()) return prefix ? "Grim" : "Bane";
        List<Affix> cand = new ArrayList<>();
        for (Affix a : list) if (a.itypes.isEmpty() || matches(a, b)) cand.add(a);
        if (cand.isEmpty()) cand = list;
        return cand.get(rng.nextInt(cand.size())).name;
    }
}
