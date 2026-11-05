package com.jv.diablo2;

import com.jv.diablo2.data.GameData;
import com.jv.diablo2.data.LevelDb;
import com.jv.diablo2.data.TxtTable;
import com.jv.diablo2.game.Experience;
import com.jv.diablo2.game.MonsterTemplate;
import com.jv.diablo2.game.TreasureClasses;
import com.jv.diablo2.game.item.AffixDb;
import com.jv.diablo2.game.item.ItemDb;
import com.jv.diablo2.game.item.ItemGenerator;
import com.jv.diablo2.game.item.PropertyDb;
import com.jv.diablo2.game.item.UniqueDb;
import com.jv.diablo2.game.skill.SkillDb;
import com.jv.diablo2.mpq.MpqFileSystem;

import java.util.ArrayList;
import java.util.List;

/** Общее состояние приложения: подключённые данные игры и загруженные таблицы. */
public final class Session {
    private Session() {
    }

    public static volatile MpqFileSystem fs;
    public static volatile GameData data;
    public static volatile Experience experience;
    public static volatile TreasureClasses treasure;
    public static volatile List<MonsterTemplate> monsters;
    public static volatile ItemDb items;
    public static volatile ItemGenerator itemGen;
    public static volatile SkillDb skillDb;
    public static volatile LevelDb levelDb;
    public static volatile String status = "Данные игры не подключены (запасной режим)";

    private static TxtTable tryTable(String name) {
        try {
            return data.table(name);
        } catch (Exception e) {
            return null;
        }
    }

    /** Подключает MPQ и читает таблицы; ошибка одной таблицы не мешает остальным. */
    public static synchronized void attach(MpqFileSystem newFs) {
        fs = newFs;
        data = new GameData(newFs);
        Res.reset();
        StringBuilder sb = new StringBuilder("MPQ: " + newFs.archiveCount());
        try {
            experience = Experience.fromTable(data.table("Experience"));
            sb.append(" | опыт ✓");
        } catch (Exception e) {
            experience = null;
            sb.append(" | опыт ✗");
        }
        try {
            String name = data.hasTable("TreasureClassEx") ? "TreasureClassEx" : "TreasureClass";
            treasure = TreasureClasses.load(data.table(name));
            sb.append(" | дроп ✓");
        } catch (Exception e) {
            treasure = null;
            sb.append(" | дроп ✗");
        }
        try {
            List<MonsterTemplate> list = new ArrayList<>();
            for (TxtTable.Row r : data.table("MonStats").all()) {
                if (r.get("Id").isEmpty() || r.getInt("enabled", 1) != 1) continue;
                MonsterTemplate t = MonsterTemplate.fromRow(r);
                if (t.level <= 10 && t.maxDmg > 0 && !t.treasureClass.isEmpty()) list.add(t);
            }
            monsters = list;
            sb.append(" | монстры ").append(list.size());
        } catch (Exception e) {
            monsters = null;
            sb.append(" | монстры ✗");
        }
        try {
            ItemDb idb = ItemDb.load(data);
            AffixDb ad = new AffixDb(idb.types, data.table("MagicPrefix"), data.table("MagicSuffix"),
                    tryTable("RarePrefix"), tryTable("RareSuffix"));
            items = idb;
            itemGen = new ItemGenerator(idb, ad, PropertyDb.load(data.table("Properties")),
                    UniqueDb.load(data.table("UniqueItems")), tryTable("ItemRatio"));
            sb.append(" | предметы ").append(idb.size());
        } catch (Exception e) {
            items = null;
            itemGen = null;
            sb.append(" | предметы ✗");
        }
        try {
            skillDb = SkillDb.load(data.table("Skills"), tryTable("SkillDesc"));
            sb.append(" | навыки ✓");
        } catch (Exception e) {
            skillDb = null;
            sb.append(" | навыки ✗");
        }
        try {
            levelDb = LevelDb.load(data.table("Levels"), data.table("LvlPrest"));
            sb.append(" | уровни ✓");
        } catch (Exception e) {
            levelDb = null;
            sb.append(" | уровни ✗");
        }
        status = sb.toString();
    }
}
