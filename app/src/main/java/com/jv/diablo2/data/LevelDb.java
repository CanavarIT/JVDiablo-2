package com.jv.diablo2.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Levels.txt + LvlPrest.txt: названия уровней и файлы DS1 заготовок (Лагерь Охотниц = уровень 1). */
public final class LevelDb {
    public static final class Level {
        public int id, act, pal;
        public String name = "";
    }

    public static final class Preset {
        public String def = "";
        public int levelId;
        public final List<String> files = new ArrayList<>();
    }

    private final Map<Integer, Level> levels = new HashMap<>();
    private final Map<Integer, Preset> presets = new HashMap<>();

    public static LevelDb load(TxtTable levels, TxtTable presets) {
        LevelDb db = new LevelDb();
        if (levels != null) {
            for (TxtTable.Row r : levels.all()) {
                String idStr = r.get("Id");
                if (idStr.isEmpty()) continue;
                Level l = new Level();
                try {
                    l.id = Integer.parseInt(idStr);
                } catch (NumberFormatException e) {
                    continue;
                }
                l.name = r.get("LevelName").isEmpty() ? r.get("Name") : r.get("LevelName");
                l.act = r.getInt("Act", 0);
                l.pal = r.getInt("Pal", 0);
                db.levels.put(l.id, l);
            }
        }
        if (presets != null) {
            for (TxtTable.Row r : presets.all()) {
                int id = r.getInt("LevelId", -1);
                if (id < 0) continue;
                Preset p = new Preset();
                p.def = r.get("Def");
                p.levelId = id;
                for (int i = 1; i <= 6; i++) {
                    String f = r.get("File" + i);
                    if (!f.isEmpty() && !f.equals("0")) p.files.add(f.replace('/', '\\'));
                }
                if (!p.files.isEmpty() && !db.presets.containsKey(id)) db.presets.put(id, p);
            }
        }
        return db;
    }

    public Level level(int id) {
        return levels.get(id);
    }

    public Preset preset(int levelId) {
        return presets.get(levelId);
    }
}
