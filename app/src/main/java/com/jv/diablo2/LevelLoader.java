package com.jv.diablo2;

import com.jv.diablo2.data.GameData;
import com.jv.diablo2.data.LevelDb;
import com.jv.diablo2.game.Hero;
import com.jv.diablo2.gfx.Ds1;
import com.jv.diablo2.mpq.MpqFileSystem;
import com.jv.diablo2.render.Assets;
import com.jv.diablo2.render.GameFrame;

import java.io.IOException;

/** Загрузка уровня-заготовки (Лагерь Охотниц = 1): DS1 + DT1 -> игровой кадр GameFrame (мир, герой, NPC, панель). */
final class LevelLoader {
    private LevelLoader() {
    }

    static final class Result {
        GameFrame frame;
        String info;
    }

    static Assets assets() {
        return new Assets(Session.fs);
    }

    static Result load(int levelId, Hero hero) throws Exception {
        MpqFileSystem fs = Session.fs;
        LevelDb db = Session.levelDb;
        if (fs == null || db == null) throw new IOException("Нет данных уровней (Levels.txt / LvlPrest.txt)");
        LevelDb.Preset pr = db.preset(levelId);
        if (pr == null || pr.files.isEmpty()) throw new IOException("В LvlPrest.txt нет заготовки для уровня " + levelId);
        String ds1Path = Ds1.tilePath(pr.files.get(0));
        LevelDb.Level lv = db.level(levelId);
        int act = (lv != null ? lv.act : 0) + 1;
        Assets as = assets();
        GameData data = Session.data != null ? Session.data : new GameData(fs);
        GameFrame g = GameFrame.load(as, data, hero, ds1Path, "act" + act, lv != null ? lv.name : "Level " + levelId, null, levelId);
        // таблицы опыта и дропа для боя: убийство монстра даёт опыт и выбрасывает предметы
        if (Session.experience != null) g.world.experience = Session.experience;
        g.world.treasure = Session.treasure;
        g.world.itemGen = Session.itemGen;
        Result r = new Result();
        r.frame = g;
        StringBuilder sb = new StringBuilder();
        for (String l : g.log) sb.append(l).append('\n');
        if (!as.missing.isEmpty()) sb.append("не найдено: ").append(as.missing).append('\n');
        r.info = sb.toString();
        for (String l : g.log) Res.note("УРОВЕНЬ " + l);
        for (String m : as.missing) Res.note("УРОВЕНЬ нет файла: " + m);
        return r;
    }
}
