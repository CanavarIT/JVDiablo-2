package com.jv.diablo2.game.item;

import com.jv.diablo2.data.GameData;
import com.jv.diablo2.data.TxtTable;
import com.jv.diablo2.game.D2Random;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Все базовые предметы игры + выбор базы по коду из классов сокровищ. */
public final class ItemDb {
    public final ItemTypeDb types;
    private final Map<String, ItemBase> byCode = new HashMap<>();
    private final List<ItemBase> all = new ArrayList<>();

    private ItemDb(ItemTypeDb types) {
        this.types = types;
    }

    public static ItemDb load(GameData data) throws IOException {
        ItemDb db = new ItemDb(ItemTypeDb.load(data.table("ItemTypes")));
        db.read(data.table("Armor"), ItemBase.Kind.ARMOR);
        db.read(data.table("Weapons"), ItemBase.Kind.WEAPON);
        db.read(data.table("Misc"), ItemBase.Kind.MISC);
        return db;
    }

    public static ItemDb fromTables(TxtTable types, TxtTable armor, TxtTable weapons, TxtTable misc) {
        ItemDb db = new ItemDb(ItemTypeDb.load(types));
        db.read(armor, ItemBase.Kind.ARMOR);
        db.read(weapons, ItemBase.Kind.WEAPON);
        db.read(misc, ItemBase.Kind.MISC);
        return db;
    }

    private void read(TxtTable t, ItemBase.Kind kind) {
        for (TxtTable.Row r : t.all()) {
            String code = ItemTypeDb.low(r.get("code"));
            if (code.isEmpty()) continue;
            ItemBase b = new ItemBase();
            b.kind = kind;
            b.code = code;
            b.name = r.get("name");
            b.type = ItemTypeDb.low(r.get("type"));
            b.type2 = ItemTypeDb.low(r.get("type2"));
            b.level = r.getInt("level", 0);
            b.levelReq = r.getInt("levelreq", 0);
            b.reqStr = r.getInt("reqstr", 0);
            b.reqDex = r.getInt("reqdex", 0);
            b.durability = r.getInt("durability", 0);
            b.invW = Math.max(1, r.getInt("invwidth", 1));
            b.invH = Math.max(1, r.getInt("invheight", 1));
            b.sockets = r.getInt("gemsockets", 0);
            b.speed = r.getInt("speed", 0);
            b.rarity = r.getInt("rarity", 1);
            b.cost = r.getInt("cost", 0);
            b.spawnable = r.getInt("spawnable", 1) != 0;
            b.minAc = r.getInt("minac", 0);
            b.maxAc = Math.max(b.minAc, r.getInt("maxac", 0));
            b.block = r.getInt("block", 0);
            b.minDmg = r.getInt("mindam", 0);
            b.maxDmg = Math.max(b.minDmg, r.getInt("maxdam", 0));
            b.twoHanded = r.getInt("2handed", 0) == 1;
            b.min2h = r.getInt("2handmindam", 0);
            b.max2h = Math.max(b.min2h, r.getInt("2handmaxdam", 0));
            b.minMis = r.getInt("minmisdam", 0);
            b.maxMis = Math.max(b.minMis, r.getInt("maxmisdam", 0));
            b.strBonus = r.getInt("StrBonus", 0);
            b.dexBonus = r.getInt("DexBonus", 0);
            b.stackable = r.getInt("stackable", 0) == 1;
            b.normCode = ItemTypeDb.low(r.get("normcode"));
            b.uberCode = ItemTypeDb.low(r.get("ubercode"));
            b.ultraCode = ItemTypeDb.low(r.get("ultracode"));
            b.invFile = r.get("invfile");
            byCode.put(code, b);
            all.add(b);
        }
    }

    public ItemBase get(String code) {
        return code == null ? null : byCode.get(code.toLowerCase(Locale.ROOT));
    }

    public int size() {
        return all.size();
    }

    /**
     * Код из сокровищницы: точный код базы ("cap", "r01") либо тип+уровень ("weap6", "armo33" —
     * любая база этого типа с уровнем не выше числа, вероятность по Rarity).
     */
    public ItemBase pick(String tcCode, int ilvl, D2Random rng) {
        String c = tcCode.toLowerCase(Locale.ROOT);
        int comma = c.indexOf(',');
        if (comma >= 0) c = c.substring(0, comma);
        c = c.trim();
        ItemBase b = byCode.get(c);
        if (b != null) return b;
        int i = c.length();
        while (i > 0 && Character.isDigit(c.charAt(i - 1))) i--;
        if (i == 0 || i == c.length() || types.get(c.substring(0, i)) == null) return null;
        String type = c.substring(0, i);
        int lvl = Integer.parseInt(c.substring(i));
        List<ItemBase> cand = new ArrayList<>();
        int total = 0;
        for (ItemBase x : all) {
            if (!x.spawnable || x.level > lvl) continue;
            if (!types.isA(x.type, type) && !types.isA(x.type2, type)) continue;
            cand.add(x);
            total += Math.max(1, x.rarity);
        }
        if (cand.isEmpty()) return null;
        int r = rng.nextInt(total);
        for (ItemBase x : cand) {
            r -= Math.max(1, x.rarity);
            if (r < 0) return x;
        }
        return cand.get(cand.size() - 1);
    }
}
