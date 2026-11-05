package com.jv.diablo2.game.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Экземпляр предмета. */
public final class Item {
    public enum Quality { LOW, NORMAL, SUPERIOR, MAGIC, SET, RARE, UNIQUE }

    public static final class Mod {
        public final String stat, param;
        public final int value;

        public Mod(String stat, String param, int value) {
            this.stat = stat;
            this.param = param == null ? "" : param;
            this.value = value;
        }
    }

    public final ItemBase base;
    public Quality quality = Quality.NORMAL;
    public int ilvl, reqLevel, defense, durability, maxDurability, sockets, quantity = 1;
    public boolean ethereal;
    public String prefix = "", suffix = "", title = "";
    public final List<Mod> mods = new ArrayList<>();

    public Item(ItemBase base) {
        this.base = base;
    }

    public int width() {
        return base.invW;
    }

    public int height() {
        return base.invH;
    }

    public int modSum(String stat) {
        int s = 0;
        for (Mod m : mods) if (m.stat.equals(stat)) s += m.value;
        return s;
    }

    public String displayName() {
        String n = base.name;
        switch (quality) {
            case UNIQUE:
            case SET:
                n = (title.isEmpty() ? n : title + " (" + n + ")");
                break;
            case RARE:
                n = (title.isEmpty() ? n : title + " (" + n + ")");
                break;
            case MAGIC:
                n = (prefix.isEmpty() ? "" : prefix + " ") + n + (suffix.isEmpty() ? "" : " " + suffix);
                break;
            case SUPERIOR:
                n = "Superior " + n;
                break;
            case LOW:
                n = "Crude " + n;
                break;
            default:
                break;
        }
        return (ethereal ? "Ethereal " : "") + n;
    }

    public int color() {
        switch (quality) {
            case MAGIC: return 0xFF6969FF;
            case RARE: return 0xFFFFFF64;
            case UNIQUE: return 0xFFA59263;
            case SET: return 0xFF00FF00;
            case SUPERIOR: return 0xFFE0E0E0;
            case LOW: return 0xFF909090;
            default: return 0xFFFFFFFF;
        }
    }

    public List<String> tooltip() {
        List<String> t = new ArrayList<>();
        t.add(displayName());
        if (base.kind == ItemBase.Kind.ARMOR) t.add("Защита: " + defense);
        if (base.kind == ItemBase.Kind.WEAPON) {
            int mn = base.twoHanded ? base.min2h : base.minDmg, mx = base.twoHanded ? base.max2h : base.maxDmg;
            if (mx == 0) {
                mn = base.minMis;
                mx = base.maxMis;
            }
            t.add((base.twoHanded ? "Двуручный урон: " : "Урон: ") + mn + "-" + mx);
        }
        if (base.block > 0) t.add("Блок: " + base.block + "%");
        if (maxDurability > 0) t.add("Прочность: " + durability + "/" + maxDurability);
        if (base.reqStr > 0) t.add("Нужна сила: " + base.reqStr);
        if (base.reqDex > 0) t.add("Нужна ловкость: " + base.reqDex);
        if (reqLevel > 0) t.add("Нужен уровень: " + reqLevel);
        for (Mod m : mods) t.add(describe(m));
        return t;
    }

    public static String describe(Mod m) {
        int v = m.value;
        switch (m.stat) {
            case "strength": return "+" + v + " к силе";
            case "dexterity": return "+" + v + " к ловкости";
            case "vitality": return "+" + v + " к живучести";
            case "energy": return "+" + v + " к энергии";
            case "maxhp": return "+" + v + " к жизни";
            case "maxmana": return "+" + v + " к мане";
            case "armorclass": return "+" + v + " к защите";
            case "item_armor_percent": return "+" + v + "% к защите";
            case "item_maxdamage_percent": return "+" + v + "% к урону";
            case "item_mindamage_percent": return "";
            case "mindamage": return "+" + v + " к мин. урону";
            case "maxdamage": return "+" + v + " к макс. урону";
            case "tohit": return "+" + v + " к меткости";
            case "toblock": return "+" + v + "% к блоку";
            case "item_deadlystrike": return v + "% смертельный удар";
            case "item_crushingblow": return v + "% сокрушительный удар";
            case "fireresist": return "Сопротивление огню +" + v + "%";
            case "coldresist": return "Сопротивление холоду +" + v + "%";
            case "lightresist": return "Сопротивление молнии +" + v + "%";
            case "poisonresist": return "Сопротивление яду +" + v + "%";
            case "item_goldbonus": return v + "% больше золота";
            case "item_magicbonus": return v + "% к находке магических предметов";
            default:
                return m.stat.toLowerCase(Locale.ROOT) + (m.param.isEmpty() ? "" : " (" + m.param + ")") + ": " + v;
        }
    }
}
