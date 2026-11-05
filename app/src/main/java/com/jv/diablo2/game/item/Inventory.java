package com.jv.diablo2.game.item;

import com.jv.diablo2.game.CharClass;
import com.jv.diablo2.game.PlayerStats;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Рюкзак 10x4, тайник 6x8, куб 3x4, 10 слотов экипировки. Требования к предметам и пересчёт бонусов героя. */
public final class Inventory {
    public enum Slot {
        HEAD("head"), NECK("neck"), TORSO("tors"), RIGHT_HAND("rarm"), LEFT_HAND("larm"),
        RIGHT_RING("rrin"), LEFT_RING("lrin"), BELT("belt"), FEET("feet"), GLOVES("glov");

        public final String loc;

        Slot(String loc) {
            this.loc = loc;
        }

        static Slot of(String loc) {
            for (Slot s : values()) if (s.loc.equals(loc)) return s;
            return null;
        }
    }

    /** Сетка клеток; предмет занимает прямоугольник width x height. */
    public static final class Grid {
        public final int w, h;
        private final Item[] cells;
        private final Map<Item, int[]> pos = new IdentityHashMap<>();
        private final List<Item> order = new ArrayList<>();

        public Grid(int w, int h) {
            this.w = w;
            this.h = h;
            this.cells = new Item[w * h];
        }

        public boolean fits(Item it, int x, int y) {
            if (x < 0 || y < 0 || x + it.width() > w || y + it.height() > h) return false;
            for (int j = 0; j < it.height(); j++)
                for (int i = 0; i < it.width(); i++) if (cells[(y + j) * w + x + i] != null) return false;
            return true;
        }

        public boolean place(Item it, int x, int y) {
            if (!fits(it, x, y)) return false;
            for (int j = 0; j < it.height(); j++)
                for (int i = 0; i < it.width(); i++) cells[(y + j) * w + x + i] = it;
            pos.put(it, new int[]{x, y});
            order.add(it);
            return true;
        }

        /** Кладёт в первое свободное место (сверху вниз, слева направо). */
        public boolean add(Item it) {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) if (place(it, x, y)) return true;
            return false;
        }

        public void remove(Item it) {
            int[] p = pos.remove(it);
            if (p == null) return;
            order.remove(it);
            for (int j = 0; j < it.height(); j++)
                for (int i = 0; i < it.width(); i++) cells[(p[1] + j) * w + p[0] + i] = null;
        }

        public Item at(int x, int y) {
            return (x < 0 || y < 0 || x >= w || y >= h) ? null : cells[y * w + x];
        }

        public int[] posOf(Item it) {
            return pos.get(it);
        }

        public List<Item> items() {
            return new ArrayList<>(order);
        }
    }

    public final Grid pack = new Grid(10, 4);
    public final Grid stash = new Grid(6, 8);
    public final Grid cube = new Grid(3, 4);
    private final EnumMap<Slot, Item> equipped = new EnumMap<>(Slot.class);
    public int gold;

    public Item equipped(Slot s) {
        return equipped.get(s);
    }

    public Map<Slot, Item> equippedMap() {
        return new EnumMap<>(equipped);
    }

    public Slot slotOf(Item it) {
        for (Map.Entry<Slot, Item> e : equipped.entrySet()) if (e.getValue() == it) return e.getKey();
        return null;
    }

    /** Надевает предмет из рюкзака. Возвращает текст ошибки или null при успехе. */
    public String equip(Item it, PlayerStats s, ItemTypeDb types) {
        ItemBase b = it.base;
        int[] from = pack.posOf(it);
        if (from == null) return "Предмет не в рюкзаке";
        if (s.clvl < it.reqLevel) return "Нужен уровень " + it.reqLevel;
        if (s.totalStr() < b.reqStr) return "Нужна сила " + b.reqStr;
        if (s.totalDex() < b.reqDex) return "Нужна ловкость " + b.reqDex;
        ItemTypeDb.Type bt = types.bodyType(b.type);
        Slot s1 = bt == null ? null : Slot.of(bt.bodyLoc1);
        Slot s2 = bt == null ? null : Slot.of(bt.bodyLoc2);
        if (s1 == null) return "Это нельзя надеть";
        boolean weapon = b.kind == ItemBase.Kind.WEAPON;
        Slot target = s1;
        if (s2 != null && s2 != s1) {
            boolean canAlt = !weapon || (!b.twoHanded && (s.cls == CharClass.ASSASSIN || s.cls == CharClass.BARBARIAN));
            if (canAlt && equipped.get(s1) != null && equipped.get(s2) == null) target = s2;
        }
        Item rh = equipped.get(Slot.RIGHT_HAND);
        if (target == Slot.LEFT_HAND && !weapon && rh != null && rh.base.twoHanded) return "Мешает двуручное оружие";

        pack.remove(it);
        List<Item> displaced = new ArrayList<>();
        Item old = equipped.get(target);
        if (old != null) displaced.add(old);
        Item lh = equipped.get(Slot.LEFT_HAND);
        boolean dropLeft = weapon && b.twoHanded && target == Slot.RIGHT_HAND && lh != null && lh != old;
        if (dropLeft) displaced.add(lh);
        List<Item> placed = new ArrayList<>();
        for (Item d : displaced) {
            if (pack.add(d)) {
                placed.add(d);
            } else {
                for (Item x : placed) pack.remove(x);
                pack.place(it, from[0], from[1]);
                return "Нет места в рюкзаке";
            }
        }
        if (old != null) equipped.remove(target);
        if (dropLeft) equipped.remove(Slot.LEFT_HAND);
        equipped.put(target, it);
        recompute(s);
        return null;
    }

    public String unequip(Slot slot, PlayerStats s) {
        Item it = equipped.get(slot);
        if (it == null) return null;
        if (!pack.add(it)) return "Нет места в рюкзаке";
        equipped.remove(slot);
        recompute(s);
        return null;
    }

    /** Прямая установка при загрузке сохранения. */
    public void setEquipped(Slot slot, Item it) {
        if (it == null) equipped.remove(slot);
        else equipped.put(slot, it);
    }

    /** Пересобирает бонусы героя из надетых предметов. */
    public void recompute(PlayerStats s) {
        s.bonusStr = s.bonusDex = s.bonusVit = s.bonusEne = 0;
        s.bonusLife = s.bonusMana = 0;
        s.edPercent = s.deadlyStrike = s.crushingBlow = 0;
        s.bonusAttackRating = s.arPercent = 0;
        s.magicFind = s.goldFind = 0;
        int defense = 0, flatMin = 0, flatMax = 0, toBlock = 0;
        for (Item it : equipped.values()) {
            if (it.base.kind == ItemBase.Kind.ARMOR) defense += it.defense * (100 + it.modSum("item_armor_percent")) / 100;
            defense += it.modSum("armorclass");
            s.bonusStr += it.modSum("strength");
            s.bonusDex += it.modSum("dexterity");
            s.bonusVit += it.modSum("vitality");
            s.bonusEne += it.modSum("energy");
            s.bonusLife += it.modSum("maxhp");
            s.bonusMana += it.modSum("maxmana");
            s.bonusAttackRating += it.modSum("tohit");
            s.arPercent += it.modSum("item_tohit_percent");
            s.edPercent += it.modSum("item_maxdamage_percent");
            s.deadlyStrike += it.modSum("item_deadlystrike");
            s.crushingBlow += it.modSum("item_crushingblow");
            s.magicFind += it.modSum("item_magicbonus");
            s.goldFind += it.modSum("item_goldbonus");
            flatMin += it.modSum("mindamage");
            flatMax += it.modSum("maxdamage");
            toBlock += it.modSum("toblock");
        }
        s.bonusDefense = defense;
        s.flatDamage = (flatMin + flatMax) / 2;
        Item w = equipped.get(Slot.RIGHT_HAND);
        if (w != null && w.base.kind == ItemBase.Kind.WEAPON) {
            ItemBase b = w.base;
            int mn = b.twoHanded ? b.min2h : b.minDmg, mx = b.twoHanded ? b.max2h : b.maxDmg;
            if (mx == 0) {
                mn = b.minMis;
                mx = b.maxMis;
            }
            s.weaponMin = Math.max(1, mn);
            s.weaponMax = Math.max(s.weaponMin, mx);
            s.weaponStrBonus = b.strBonus;
            s.weaponDexBonus = b.dexBonus;
        } else {
            s.weaponMin = 1;
            s.weaponMax = 2;
            s.weaponStrBonus = 0;
            s.weaponDexBonus = 0;
        }
        Item sh = equipped.get(Slot.LEFT_HAND);
        s.shieldBlock = (sh != null && sh.base.block > 0) ? sh.base.block + toBlock : 0;
        s.life = Math.min(s.life, s.maxLife());
        s.mana = Math.min(s.mana, s.maxMana());
    }
}
