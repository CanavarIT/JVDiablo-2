package com.jv.diablo2.game.item;

import com.jv.diablo2.data.TxtTable;
import com.jv.diablo2.game.D2Random;

import java.util.HashSet;
import java.util.Set;

/**
 * Генерация предмета: база -> качество (unique/rare/magic/superior по схеме ItemRatio с поправкой на MF) ->
 * аффиксы и свойства. Сеты, ремёсленные предметы, сокеты и класс-специфичные бонусы — следующий этап.
 */
public final class ItemGenerator {
    private final ItemDb items;
    private final AffixDb affixes;
    private final PropertyDb props;
    private final UniqueDb uniques;
    // уникальные / редкие / магические / улучшенные: {ratio, divisor, min}
    private int[] ru = {400, 1, 6}, rr = {100, 2, 4}, rm = {34, 1, 1}, rh = {12, 1, 0};

    public ItemGenerator(ItemDb items, AffixDb affixes, PropertyDb props, UniqueDb uniques, TxtTable itemRatio) {
        this.items = items;
        this.affixes = affixes;
        this.props = props;
        this.uniques = uniques;
        if (itemRatio != null) readRatio(itemRatio);
    }

    private void readRatio(TxtTable t) {
        for (TxtTable.Row r : t.all()) {
            if (r.getInt("Version", 0) != 1 || r.getInt("Uber", 0) != 0 || r.getInt("Class Specific", 0) != 0) continue;
            if (r.getInt("Unique", 0) > 0) ru = new int[]{r.getInt("Unique", 400), r.getInt("UniqueDivisor", 1), r.getInt("UniqueMin", 6)};
            if (r.getInt("Rare", 0) > 0) rr = new int[]{r.getInt("Rare", 100), r.getInt("RareDivisor", 2), r.getInt("RareMin", 4)};
            if (r.getInt("Magic", 0) > 0) rm = new int[]{r.getInt("Magic", 34), r.getInt("MagicDivisor", 1), r.getInt("MagicMin", 1)};
            if (r.getInt("HiQuality", 0) > 0) rh = new int[]{r.getInt("HiQuality", 12), r.getInt("HiQualityDivisor", 1), 0};
            return;
        }
    }

    public ItemDb items() {
        return items;
    }

    private static boolean roll(int[] ratio, int ilvl, int qlvl, int mf, int mfFactor, D2Random rng) {
        double c = (ratio[0] - (ilvl - qlvl) / (double) Math.max(1, ratio[1])) * 128.0;
        c = Math.max(c, ratio[2] * 128.0);
        double eff = mfFactor > 0 ? mf * (double) mfFactor / (mf + mfFactor) : mf;
        c = c * 100.0 / (100.0 + eff);
        return rng.nextInt((int) Math.max(1, c)) < 128;
    }

    private boolean eligible(ItemBase b) {
        if (b.kind != ItemBase.Kind.MISC) return true;
        ItemTypeDb t = items.types;
        return t.isA(b.type, "ring") || t.isA(b.type, "amul") || t.isA(b.type, "jewl") || t.isA(b.type, "char");
    }

    /** forced == null — обычный бросок качества; иначе качество задаётся принудительно (тесты, отладка). */
    public Item generate(String tcCode, int ilvl, int mf, Item.Quality forced, D2Random rng) {
        ItemBase base = items.pick(tcCode, ilvl, rng);
        if (base == null) return null;
        Item it = new Item(base);
        it.ilvl = ilvl;
        it.reqLevel = base.levelReq;
        it.durability = it.maxDurability = base.durability;
        if (base.kind == ItemBase.Kind.ARMOR) it.defense = rng.range(base.minAc, base.maxAc);
        if (!eligible(base)) return it;

        int qlvl = base.level;
        boolean done = false;
        if (forced != null) {
            switch (forced) {
                case UNIQUE: done = applyUnique(it, rng); if (!done) { applyRare(it, rng); } break;
                case RARE: applyRare(it, rng); break;
                case MAGIC: applyMagic(it, rng); break;
                case SUPERIOR: applySuperior(it, rng); break;
                default: break;
            }
            return it;
        }
        if (roll(ru, ilvl, qlvl, mf, 250, rng)) done = applyUnique(it, rng);
        if (!done && roll(rr, ilvl, qlvl, mf, 600, rng)) {
            applyRare(it, rng);
            done = true;
        }
        if (!done && roll(rm, ilvl, qlvl, mf, 0, rng)) {
            applyMagic(it, rng);
            done = it.quality == Item.Quality.MAGIC;
        }
        if (!done && roll(rh, ilvl, qlvl, 0, 0, rng)) applySuperior(it, rng);
        return it;
    }

    private void addMods(Item it, String[] m, D2Random rng) {
        it.mods.addAll(props.resolve(m[0], m[1], Integer.parseInt(m[2]), Integer.parseInt(m[3]), rng));
    }

    private void applyAffix(Item it, AffixDb.Affix a, D2Random rng) {
        for (String[] m : a.mods) addMods(it, m, rng);
        it.reqLevel = Math.max(it.reqLevel, a.levelReq);
    }

    private boolean applyUnique(Item it, D2Random rng) {
        UniqueDb.Unique u = uniques.pick(it.base, it.ilvl, rng);
        if (u == null) return false;
        it.quality = Item.Quality.UNIQUE;
        it.title = u.name;
        it.reqLevel = Math.max(it.base.levelReq, u.levelReq);
        for (String[] p : u.props) addMods(it, p, rng);
        return true;
    }

    private void applyMagic(Item it, D2Random rng) {
        Set<Integer> groups = new HashSet<>();
        boolean pre = rng.chance(50), suf = rng.chance(50);
        if (!pre && !suf) {
            if (rng.chance(50)) pre = true;
            else suf = true;
        }
        AffixDb.Affix p = pre ? affixes.pickPrefix(it.base, it.ilvl, groups, rng) : null;
        AffixDb.Affix s = suf ? affixes.pickSuffix(it.base, it.ilvl, groups, rng) : null;
        if (p == null && s == null) return;
        it.quality = Item.Quality.MAGIC;
        if (p != null) {
            it.prefix = p.name;
            applyAffix(it, p, rng);
        }
        if (s != null) {
            it.suffix = s.name;
            applyAffix(it, s, rng);
        }
    }

    private void applyRare(Item it, D2Random rng) {
        Set<Integer> groups = new HashSet<>();
        int want = rng.range(4, 6), have = 0, pre = 0, suf = 0, tries = 0;
        while (have < want && tries++ < 40) {
            boolean usePre = rng.chance(50);
            if (usePre && pre >= 3) usePre = false;
            if (!usePre && suf >= 3) usePre = true;
            AffixDb.Affix a = usePre ? affixes.pickPrefix(it.base, it.ilvl, groups, rng) : affixes.pickSuffix(it.base, it.ilvl, groups, rng);
            if (a == null) continue;
            applyAffix(it, a, rng);
            have++;
            if (usePre) pre++;
            else suf++;
        }
        if (have == 0) return;
        it.quality = Item.Quality.RARE;
        it.title = affixes.rareName(true, it.base, rng) + " " + affixes.rareName(false, it.base, rng);
    }

    private void applySuperior(Item it, D2Random rng) {
        int ed = rng.range(5, 15);
        it.quality = Item.Quality.SUPERIOR;
        if (it.base.kind == ItemBase.Kind.ARMOR) it.mods.add(new Item.Mod("item_armor_percent", "", ed));
        else if (it.base.kind == ItemBase.Kind.WEAPON) it.mods.add(new Item.Mod("item_maxdamage_percent", "", ed));
    }
}
