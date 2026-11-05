package com.jv.diablo2.render;

import com.jv.diablo2.data.GameData;
import com.jv.diablo2.data.TxtTable;
import com.jv.diablo2.game.CharClass;
import com.jv.diablo2.game.Hero;
import com.jv.diablo2.game.item.Inventory;
import com.jv.diablo2.game.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Внешний вид героя из экипировки. Правила (сверены по armor.txt / weapons.txt):
 * броня задаёт слой по колонке component (0 = голова, 1 = торс, 7 = щит) кодом alternategfx; остальные части тела
 * (ноги, руки, наплечники) берут вариант lit/med/hvy из колонок Legs, rArm, lArm, rSPad, lSPad (0/1/2);
 * оружие в правой руке — слой RH с кодом alternategfx (или code) и классом анимации wclass / 2handedwclass.
 * Если экипировки нет (новый герой), берутся стартовые предметы класса из charstats.txt.
 */
public final class LookBuilder {
    private LookBuilder() {
    }

    private static final String[] TIER = {"lit", "med", "hvy"};

    /** Пара (код предмета, место) — экипированный или стартовый предмет. */
    private static final class Gear {
        final String code, loc;

        Gear(String code, String loc) {
            this.code = code.toLowerCase(Locale.ROOT);
            this.loc = loc == null ? "" : loc.toLowerCase(Locale.ROOT);
        }
    }

    public static Paperdoll.Look build(GameData data, Hero hero) {
        List<Gear> gear = new ArrayList<>();
        for (Map.Entry<Inventory.Slot, Item> e : hero.inv.equippedMap().entrySet()) {
            if (e.getValue() != null) gear.add(new Gear(e.getValue().base.code, e.getKey().loc));
        }
        if (gear.isEmpty()) gear = startGear(data, hero.stats.cls);
        Paperdoll.Look look = new Paperdoll.Look();
        TxtTable armor = table(data, "armor"), weapons = table(data, "weapons");
        for (Gear g : gear) {
            TxtTable.Row w = weapons == null ? null : weapons.find("code", g.code);
            if (w != null && !g.loc.equals("larm")) {
                String alt = w.get("alternateGfx");
                look.set("rh", alt.isEmpty() ? g.code : alt);
                String two = w.get("2handedwclass"), one = w.get("wclass");
                boolean twoHand = w.getInt("2handed", 0) == 1;
                look.weaponClass = (twoHand && !two.isEmpty() ? two : one.isEmpty() ? "hth" : one).toLowerCase(Locale.ROOT);
                continue;
            }
            TxtTable.Row a = armor == null ? null : armor.find("code", g.code);
            if (a == null) continue;
            String alt = a.get("alternategfx");
            if (alt.isEmpty()) alt = g.code;
            int comp = a.getInt("component", 16);
            if (comp == 0) {
                look.set("hd", alt);
            } else if (comp == 1) {
                look.set("tr", alt);
                look.set("lg", tier(a.getInt("Legs", 0)));
                look.set("ra", tier(a.getInt("rArm", 0)));
                look.set("la", tier(a.getInt("lArm", 0)));
                look.set("s1", tier(a.getInt("rSPad", 0)));
                look.set("s2", tier(a.getInt("lSPad", 0)));
            } else if (comp == 7) {
                look.set("sh", alt);
            }
        }
        return look;
    }

    private static String tier(int v) {
        return TIER[Math.max(0, Math.min(2, v))];
    }

    private static TxtTable table(GameData d, String name) {
        try {
            return d.hasTable(name) ? d.table(name) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Gear> startGear(GameData data, CharClass cls) {
        List<Gear> out = new ArrayList<>();
        TxtTable cs = table(data, "charstats");
        if (cs == null) return out;
        TxtTable.Row r = cs.find("class", cls.title);
        if (r == null) return out;
        for (int i = 1; i <= 10; i++) {
            String code = r.get("item" + i), loc = r.get("item" + i + "loc");
            if (!code.isEmpty() && !code.equals("0") && !loc.isEmpty()) out.add(new Gear(code, loc));
        }
        return out;
    }

    /** Скорости из charstats.txt: {ходьба, бег} в субтайлах в секунду (6 и 9 по умолчанию). */
    public static float[] speeds(GameData data, CharClass cls) {
        TxtTable cs = table(data, "charstats");
        float walk = 6f, run = 9f;
        if (cs != null) {
            TxtTable.Row r = cs.find("class", cls.title);
            if (r != null) {
                walk = r.getInt("WalkVelocity", 6);
                run = r.getInt("RunVelocity", 9);
            }
        }
        return new float[]{walk, run};
    }

    /** Токен класса в именах файлов: am, so, ne, pa, ba, dz, as. */
    public static String token(CharClass c) {
        switch (c) {
            case AMAZON:
                return "am";
            case SORCERESS:
                return "so";
            case NECROMANCER:
                return "ne";
            case PALADIN:
                return "pa";
            case BARBARIAN:
                return "ba";
            case DRUID:
                return "dz";
            default:
                return "as";
        }
    }
}
