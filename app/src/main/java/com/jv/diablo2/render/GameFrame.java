package com.jv.diablo2.render;

import com.jv.diablo2.data.GameData;
import com.jv.diablo2.data.TxtTable;
import com.jv.diablo2.game.CharClass;
import com.jv.diablo2.game.Experience;
import com.jv.diablo2.game.GroundItem;
import com.jv.diablo2.game.Hero;
import com.jv.diablo2.game.Monster;
import com.jv.diablo2.game.MonsterTemplate;
import com.jv.diablo2.game.Player;
import com.jv.diablo2.game.TileMap;
import com.jv.diablo2.game.World;
import com.jv.diablo2.game.item.Item;
import com.jv.diablo2.gfx.AnimData;
import com.jv.diablo2.gfx.Cof;
import com.jv.diablo2.gfx.Dc6;
import com.jv.diablo2.gfx.Ds1;
import com.jv.diablo2.gfx.Dt1;
import com.jv.diablo2.mpq.MpqFileSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Игровой кадр 640x480: уровень из DS1/DT1, герой (составной спрайт), NPC и объекты из DS1, камера, управление и панель.
 * Чистая Java: на устройстве тот же код вызывается из GameScene, на JVM — из тестов, которые сохраняют кадр в PNG.
 * update() — один тик (25 в секунду), render() — рисует кадр, pointer() — касания в координатах 640x480.
 */
public final class GameFrame {
    public static final int ACTION_DOWN = 0, ACTION_UP = 1, ACTION_MOVE = 2;
    private static final String MONSTERS = "data\\global\\monsters\\", OBJECTS = "data\\global\\objects\\";

    public final Assets as;
    public final Hero hero;
    public final World world;
    public final Player player;
    public final Hud hud;
    public final String levelName;
    public final List<String> log = new ArrayList<>();
    /** Прогресс загрузки 0..1 для экрана «Loading» (читается из другого потока). */
    public static volatile float progress;

    private final TileMap map;
    private final LevelRenderer lr;
    private final int[] pal;          // палитра плиток уровня
    private final int[] unitPal;       // палитра юнитов (герой, NPC, объекты)
    /** Яркость мира: эталон светлее «сырой» палитры (медианная яркость 37.8 против 29.7). */
    public static final double WORLD_GAMMA = 0.90;
    private final Paperdoll doll;
    private final Paperdoll.Look look;
    private final List<Prop> props = new ArrayList<>();
    private final float walkSpeed, runSpeed;

    // ---- монстры, бой, дроп (этап 2)
    private final List<MonsterActor> mobs = new ArrayList<>();
    private final Map<String, MobType> mobTypes = new HashMap<>();
    private final Map<String, Dc6> groundSprites = new HashMap<>();
    private AnimData animData = AnimData.empty();
    private GameData data;
    private Monster focus;
    private int focusTicks, deathTick, noticeTicks, holdMode;
    private String notice = "";
    private float startX, startY;
    private GroundItem pickupTarget;
    private List<MonsterTemplate> debugPool;
    /** Кнопка отладки (вызов стаи монстров рядом с героем, чтобы проверять бой в городе, где монстров нет). */
    public boolean debugTools = true;
    /** В оригинале число урона над монстром не показывается; включать только для отладки. */
    public boolean showDamage = false;
    private static final int[] SPAWN_BTN = {4, 4, 92, 16};

    private float camX, camY;
    private int animFrame, tick, holdTick;
    private String lastMode = "tn";
    private int fileDir = 0;
    private boolean holding;
    private int ptrX, ptrY;
    private int downMini = -1;
    private boolean downRun, downToggle;
    /** Клик по значку мини-панели: вызывается с именем кнопки (char, inventory, skills, quests, chat, automap, menu). */
    public Listener listener;

    public interface Listener {
        void onMini(String name);
    }

    /** Любой юнит между стенами: NPC, объект. */
    private final class Prop implements LevelRenderer.Actor {
        final String name;
        final float x, y;
        final Paperdoll d;
        final Paperdoll.Look lk;
        final String mode;
        final int ticksPerFrame;
        final boolean npc;

        Prop(String name, float x, float y, Paperdoll d, Paperdoll.Look lk, String mode, int ticksPerFrame, boolean npc) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.d = d;
            this.lk = lk;
            this.mode = mode;
            this.ticksPerFrame = ticksPerFrame;
            this.npc = npc;
        }

        @Override
        public float x() {
            return x;
        }

        @Override
        public float y() {
            return y;
        }

        @Override
        public void draw(Fb fb, int sx, int sy) {
            int frames = Math.max(1, d.frames(mode, lk));
            int f = ticksPerFrame <= 0 ? 0 : (tick / ticksPerFrame) % frames;
            d.draw(fb, unitPal, mode, lk, npc ? Paperdoll.fileDir(0f, 1f, d.directions(mode, lk)) : 0, f, sx, sy, npc);
        }
    }

    private final LevelRenderer.Actor heroActor = new LevelRenderer.Actor() {
        @Override
        public float x() {
            return player.x;
        }

        @Override
        public float y() {
            return player.y;
        }

        @Override
        public void draw(Fb fb, int sx, int sy) {
            String mode = player.moving ? (hud.running ? "rn" : "tw") : "tn";
            if (doll.frames(mode, look) == 0) mode = player.moving ? "wl" : "nu";
            int forced = -1;
            if (player.dead && doll.frames("dt", look) > 0) {
                mode = "dt";
                forced = Math.min(doll.frames(mode, look) - 1, deathTick / 2);
            } else if (player.attacking() && doll.frames("a1", look) > 0) {
                mode = "a1";
                int n = doll.frames(mode, look);
                forced = Math.max(0, Math.min(n - 1, (int) (player.attackProgress() * n)));
            }
            if (!mode.equals(lastMode)) {
                lastMode = mode;
                animFrame = 0;
            }
            int dirs = Math.max(1, doll.directions(mode, look));
            float fx = player.faceDx - player.faceDy, fy = (player.faceDx + player.faceDy) * 0.5f;
            int dir = dirs >= 16 ? doll.calib().file(fx, fy) : Paperdoll.fileDir(fx, fy, dirs);
            fileDir = dir;
            doll.draw(fb, unitPal, mode, look, dir, forced >= 0 ? forced : animFrame, sx, sy, true);
        }
    };

    private GameFrame(Assets as, Hero hero, World world, Player player, TileMap map, int[] pal, int[] unitPal, Paperdoll doll, Paperdoll.Look look,
                      String levelName, float walk, float run) {
        this.as = as;
        this.hero = hero;
        this.world = world;
        this.player = player;
        this.map = map;
        this.pal = pal;
        this.unitPal = unitPal;
        this.lr = new LevelRenderer(map, pal);
        this.doll = doll;
        this.look = look;
        this.levelName = levelName;
        this.walkSpeed = walk;
        this.runSpeed = run;
        this.hud = new Hud(as);
    }

    /**
     * Загружает уровень. ds1Path — путь DS1 заготовки ("data\\global\\tiles\\act1\\town\\townn1.ds1"), palName — "act1".
     * start — стартовая точка в субтайлах или null (тогда рядом с сундуком, а без него — центр карты).
     */
    public static GameFrame load(Assets as, GameData data, Hero hero, String ds1Path, String palName, String name, float[] start) throws Exception {
        return load(as, data, hero, ds1Path, palName, name, start, -1);
    }

    /** levelId >= 0 включает естественный спавн монстров по Levels.txt (город — NumMon = 0, там монстров нет). */
    public static GameFrame load(Assets as, GameData data, Hero hero, String ds1Path, String palName, String name, float[] start, int levelId) throws Exception {
        MpqFileSystem fs = as.fs;
        progress = 0.05f;
        Ds1 ds = Ds1.parse(fs.read(ds1Path));
        progress = 0.15f;
        List<Dt1> dts = new ArrayList<>();
        List<String> miss = new ArrayList<>();
        for (String f : ds.files) {
            String p = Ds1.dt1Path(f);
            try {
                if (fs.exists(p)) dts.add(Dt1.parse(fs.read(p)));
                else miss.add(p);
            } catch (Exception e) {
                miss.add(p + " (" + e.getMessage() + ")");
            }
        }
        progress = 0.5f;
        TileMap tm = TileMap.build(ds, dts);
        progress = 0.65f;
        World w = new World(ds.width * 5, ds.height * 5, System.nanoTime());
        System.arraycopy(tm.blocked, 0, w.blocked, 0, tm.blocked.length);

        float sx = w.w / 2f, sy = w.h / 2f;
        if (start != null) {
            sx = start[0];
            sy = start[1];
        } else {
            for (Ds1.Obj o : ds.objects) {
                if (o.type == 2 && o.id == 267) {          // сундук (Stash)
                    sx = o.x + 1.5f;
                    sy = o.y + 2.5f;
                }
            }
        }
        int[] sp = w.nearestWalkable((int) sx, (int) sy, 40);
        if (sp != null) {
            sx = sp[0] + 0.5f;
            sy = sp[1] + 0.5f;
        }
        Player pl = new Player(hero, sx, sy);
        w.player = pl;
        float[] sv = LookBuilder.speeds(data, hero.stats.cls);
        Paperdoll doll = new Paperdoll(fs, LookBuilder.token(hero.stats.cls));
        Paperdoll.Look look = LookBuilder.build(data, hero);
        GameFrame g = new GameFrame(as, hero, w, pl, tm, as.palGamma(palName, WORLD_GAMMA), as.palGamma("units", WORLD_GAMMA), doll, look, name, sv[0], sv[1]);
        g.log.add("DS1 " + ds1Path + " v" + ds.version + " " + ds.width + "x" + ds.height + ", DT1 " + dts.size() + "/" + ds.files.size()
                + ", пол " + tm.floorHits + "/" + tm.floorCells + ", стен " + tm.wallHits + ", крыш " + tm.roofHits + ", без плитки " + tm.wallMiss);
        for (String m : miss) g.log.add("нет DT1: " + m);
        g.log.add("Направления героя: " + doll.calib().info);
        progress = 0.75f;
        g.data = data;
        g.loadAnimData();
        g.fillBelt(as, data);
        g.placeProps(ds, data, w);
        int[] free = w.nearestWalkable((int) pl.x, (int) pl.y, 40);
        if (free != null) {
            pl.x = free[0] + 0.5f;
            pl.y = free[1] + 0.5f;
        }
        g.startX = pl.x;
        g.startY = pl.y;
        g.configureHeroAttack();
        if (levelId >= 0) g.populate(levelId);
        g.snapCamera();
        progress = 1f;
        return g;
    }

    /**
     * Пояс: зелья стартового набора класса (charstats.txt: предметы без места, например hp1 x4) раскладываются по ячейкам,
     * иконка берётся из invfile предмета (misc.txt). Полноценный пояс с инвентарём героя — следующий этап.
     */
    private void fillBelt(Assets as, GameData data) {
        TxtTable cs = table(data, "charstats"), misc = table(data, "misc");
        if (cs == null || misc == null) return;
        TxtTable.Row r = cs.find("class", hero.stats.cls.title);
        if (r == null) return;
        int slot = 0;
        for (int i = 1; i <= 10 && slot < 4; i++) {
            String code = r.get("item" + i);
            if (code.isEmpty() || code.equals("0") || !r.get("item" + i + "loc").isEmpty()) continue;
            TxtTable.Row m = misc.find("code", code);
            if (m == null) continue;
            String type = m.get("type");
            if (!type.equals("hpot") && !type.equals("mpot") && !type.equals("rpot") && !type.equals("spot") && !type.equals("apot")) continue;
            String inv = m.get("invfile");
            if (inv.isEmpty()) continue;
            com.jv.diablo2.gfx.Dc6 icon = as.dc6("data\\global\\items\\" + inv + ".dc6");
            for (int k = 0, n = Math.max(1, r.getInt("item" + i + "count", 1)); k < n && slot < 4; k++) hud.beltIcons[slot++] = icon;
        }
    }

    // ---------------------------------------------------------------- NPC и объекты

    private void placeProps(Ds1 ds, GameData data, World w) {
        TxtTable monpreset = table(data, "monpreset"), monstats = table(data, "monstats"), monstats2 = table(data, "monstats2"), objects = table(data, "objects");
        int npcCount = 0, objCount = 0, objTried = 0;
        for (Ds1.Obj o : ds.objects) {
            if (o.type == 1 && monpreset != null && monstats != null) {
                if (o.id < 0 || o.id >= monpreset.size()) continue;
                String place = monpreset.row(o.id).get("Place");
                TxtTable.Row ms = monstats.find("Id", place);
                if (ms == null) continue;
                String code = ms.get("Code").toLowerCase(Locale.ROOT);
                if (code.isEmpty()) continue;
                Paperdoll d = new Paperdoll(as.fs, code, MONSTERS);
                Paperdoll.Look lk = monsterLook(monstats2 == null ? null : monstats2.find("Id", place));
                if (d.frames("nu", lk) == 0) {
                    log.add("NPC " + place + " (" + code + "): нет анимации nu");
                    continue;
                }
                props.add(new Prop(place, o.x + 0.5f, o.y + 0.5f, d, lk, "nu", 2, true));
                int cx = o.x, cy = o.y;
                if (cx >= 0 && cy >= 0 && cx < w.w && cy < w.h) w.blocked[cy * w.w + cx] = true;
                npcCount++;
            } else if (o.type == 2 && objects != null && o.id >= 0 && o.id < objects.size()) {
                TxtTable.Row r = objects.row(o.id);
                blockObject(w, r, o);
                String token = r.get("Token").toLowerCase(Locale.ROOT);
                if (token.isEmpty()) continue;
                objTried++;
                Paperdoll d = new Paperdoll(as.fs, token, OBJECTS);
                Paperdoll.Look lk = new Paperdoll.Look();
                String mode = null;
                for (String m : new String[]{"nu", "on", "s1", "op"}) {
                    if (d.frames(m, lk) > 0) {
                        mode = m;
                        break;
                    }
                }
                if (mode == null) continue;
                int mi = Math.max(0, indexOf(new String[]{"NU", "OP", "ON", "S1"}, mode.toUpperCase(Locale.ROOT)));
                int delta = r.getInt("FrameDelta" + mi, 0);
                props.add(new Prop(r.get("Name"), o.x + 0.5f, o.y + 0.5f, d, lk, mode, delta <= 0 ? 0 : Math.max(1, Math.round(256f / delta)), false));
                objCount++;
            }
        }
        log.add("NPC: " + npcCount + ", объектов с анимацией: " + objCount + " из " + objTried);
    }

    /**
     * Коллизия объекта из Objects.txt: HasCollision0 (режим NU) и размер SizeX x SizeY в субтайлах.
     * Прямоугольник центрируется на позиции объекта из DS1. Если колонки нет — объект считается твёрдым.
     */
    private static void blockObject(World w, TxtTable.Row r, Ds1.Obj o) {
        String hc = r.get("HasCollision0");
        if (hc != null && hc.trim().equals("0")) return;
        int sizeX = Math.max(1, r.getInt("SizeX", 1)), sizeY = Math.max(1, r.getInt("SizeY", 1));
        int x0 = o.x - sizeX / 2, y0 = o.y - sizeY / 2;
        for (int yy = y0; yy < y0 + sizeY; yy++)
            for (int xx = x0; xx < x0 + sizeX; xx++)
                if (xx >= 0 && yy >= 0 && xx < w.w && yy < w.h) w.blocked[yy * w.w + xx] = true;
    }

    private static int indexOf(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i;
        return -1;
    }

    /** Слои монстра/NPC из monstats2.txt: колонка HD..S8 = 1 — слой есть, HDv.. — его вариант (lit/med/hvy и т. п.). */
    private static Paperdoll.Look monsterLook(TxtTable.Row r) {
        Paperdoll.Look lk = new Paperdoll.Look();
        if (r == null) return lk;
        for (int i = 0; i < Cof.COMP.length; i++) {
            String c = Cof.COMP[i];
            lk.code[i] = null;
            if (r.getInt(c, 0) == 1) {
                String v = r.get(c + "v");
                lk.code[i] = v.isEmpty() ? "lit" : v.toLowerCase(Locale.ROOT);
            }
        }
        return lk;
    }

    private static TxtTable table(GameData d, String n) {
        try {
            return d.hasTable(n) ? d.table(n) : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- тик

    private void snapCamera() {
        camX = player.x;
        camY = player.y;
    }

    public void update() {
        tick++;
        player.speed = hud.running ? runSpeed : walkSpeed;
        if (holding && ++holdTick % 3 == 0) {
            if (holdMode == 1) {
                if (player.target == null) holding = false;      // цель убита — удерживаемый клик больше не бьёт
            } else {
                walkToPointer();
            }
        }
        world.update();
        for (MonsterActor a : mobs) a.tick();
        for (Iterator<MonsterActor> it = mobs.iterator(); it.hasNext(); ) {
            if (!world.monsters.contains(it.next().m)) it.remove();
        }
        if (player.dead) {
            deathTick++;
            holding = false;
            pickupTarget = null;
        } else {
            deathTick = 0;
        }
        if (focusTicks > 0) focusTicks--;
        if (noticeTicks > 0) noticeTicks--;
        updatePickup();
        animFrame++;
        if (!player.moving && tick % 2 == 0) animFrame--;       // в покое анимация идёт вдвое медленнее
        camX = player.x;
        camY = player.y;
        float max = Math.max(1, hero.stats.maxLife());
        hud.life = hero.stats.life / max;
        hud.mana = hero.stats.maxMana() > 0 ? hero.stats.mana / (float) hero.stats.maxMana() : 0;
        hud.stamina = hero.stats.maxStamina() > 0 ? hero.stats.stamina / (float) hero.stats.maxStamina() : 1f;
        Experience ex = world.experience;
        CharClass cc = hero.stats.cls;
        if (ex == null || hero.stats.clvl >= ex.maxLevel(cc)) {
            hud.xp = ex == null ? 0f : 1f;
        } else {
            long lo = ex.totalToReach(cc, hero.stats.clvl), hi = ex.totalToReach(cc, hero.stats.clvl + 1);
            hud.xp = hi > lo ? Math.max(0f, Math.min(1f, (hero.stats.exp - lo) / (float) (hi - lo))) : 0f;
        }
    }

    // ---------------------------------------------------------------- рисование

    public void render(Fb fb) {
        fb.clear(0xFF000000);
        List<LevelRenderer.Actor> actors = new ArrayList<>(props.size() + mobs.size() + world.drops.size() + 1);
        actors.addAll(props);
        actors.addAll(mobs);
        for (GroundItem g : world.drops) actors.add(new DropActor(g));
        actors.add(heroActor);
        lr.render(fb, camX, camY, actors);
        // имя NPC под курсором (как в игре, при наведении) — для касания показываем у ближайшего к точке касания
        hud.draw(fb);
        drawNames(fb);
        drawFocus(fb);
        if (showDamage) drawTexts(fb);
        if (noticeTicks > 0) Text.drawShadow(fb, as, 16, notice, 320, 40, Text.WHITE, Text.CENTER);
        if (debugTools) {
            fb.fillRect(SPAWN_BTN[0], SPAWN_BTN[1], SPAWN_BTN[2], SPAWN_BTN[3], 0xAA000000);
            Text.draw(fb, as, 8, "+ MONSTERS", SPAWN_BTN[0] + 4, SPAWN_BTN[1] + 4, Text.YELLOW, Text.LEFT);
        }
        if (player.dead && deathTick > 40) {
            fb.darken(0, 0, 640, 376, 120);
            Text.drawShadow(fb, as, 30, "YOU HAVE DIED", 320, 150, Text.RED, Text.CENTER);
            if (deathTick > 60) Text.drawShadow(fb, as, 16, "Tap to respawn", 320, 200, Text.WHITE, Text.CENTER);
        }
    }

    private void drawNames(Fb fb) {
        if (!holding && downMini < 0) return;
        float wx = LevelRenderer.worldX(ptrX, ptrY, camX, camY), wy = LevelRenderer.worldY(ptrX, ptrY, camX, camY);
        Prop best = null;
        float bd = 4f;
        for (Prop p : props) {
            if (!p.npc) continue;
            float d = (float) Math.hypot(p.x - wx, p.y - wy);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        if (best != null) {
            String n = as.strings().get(best.name, best.name);
            int sx = Math.round(LevelRenderer.screenX(best.x, best.y, camX, camY)), sy = Math.round(LevelRenderer.screenY(best.x, best.y, camX, camY));
            Text.drawShadow(fb, as, 16, n, sx, sy - 90, Text.WHITE, Text.CENTER);
        }
    }

    // ---------------------------------------------------------------- монстры, бой, дроп

    /** Тип монстра: шаблон из MonStats.txt, токен файлов, общий Paperdoll и слои из MonStats2.txt. */
    private static final class MobType {
        MonsterTemplate t;
        String token;
        Paperdoll doll;
        Paperdoll.Look look;
        boolean ok;
    }

    private void loadAnimData() {
        for (String p : new String[]{"data\\global\\animdata.d2", "data\\global\\excel\\animdata.d2"}) {
            try {
                if (as.fs.exists(p)) {
                    AnimData a = AnimData.parse(as.fs.read(p));
                    if (!a.isEmpty()) {
                        animData = a;
                        log.add("animdata.d2: " + p);
                        return;
                    }
                }
            } catch (Exception e) {
                // остаются запасные скорости
            }
        }
        log.add("animdata.d2 не прочитан: запасные скорости анимации");
    }

    /** Длительность удара героя и тик попадания: число кадров a1, скорость из animdata, ключевой кадр атаки из COF. */
    private void configureHeroAttack() {
        int frames = doll.frames("a1", look);
        Paperdoll.Anim a = doll.load("a1", look);
        if (a == null || frames <= 0) return;
        String tk = LookBuilder.token(hero.stats.cls);
        String wc = look.weaponClass == null ? "hth" : look.weaponClass.toLowerCase(Locale.ROOT);
        int sp = animData.speed(tk + "a1" + wc);
        if (sp < 0) sp = animData.speed(tk + "a1hth");
        float per = (sp < 0 ? 256 : sp) / 256f;
        player.attackTicks = Math.max(4, (int) Math.ceil(frames / per));
        int hit = player.attackTicks / 2;
        for (int f = 0; f < a.cof.keyframes.length; f++) {
            if (a.cof.keyframes[f] == 1) {
                hit = Math.round(f / per);
                break;
            }
        }
        player.attackHitAt = Math.max(1, Math.min(player.attackTicks - 1, hit));
    }

    private MobType mobType(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        MobType mt = mobTypes.get(key);
        if (mt != null) return mt;
        mt = new MobType();
        mobTypes.put(key, mt);
        TxtTable monstats = table(data, "monstats"), monstats2 = table(data, "monstats2");
        if (monstats == null) return mt;
        TxtTable.Row ms = monstats.find("Id", id);
        if (ms == null) return mt;
        String code = ms.get("Code").toLowerCase(Locale.ROOT);
        if (code.isEmpty()) return mt;
        mt.t = MonsterTemplate.fromRow(ms);
        mt.token = code;
        mt.doll = new Paperdoll(as.fs, code, MONSTERS);
        TxtTable.Row m2 = monstats2 == null ? null : monstats2.find("Id", id);
        mt.look = monsterLook(m2);
        if (m2 != null) {
            String base = m2.get("BaseW");
            if (!base.isEmpty()) mt.look.weaponClass = base.toLowerCase(Locale.ROOT);
            mt.look.changed();
        }
        mt.ok = mt.doll.frames("nu", mt.look) > 0;
        if (!mt.ok) log.add("монстр " + id + " (" + code + "): нет анимации nu");
        return mt;
    }

    private MonsterActor spawnMonster(MobType mt, float x, float y) {
        Monster m = new Monster(mt.t, x, y, world.rng);
        String nm = mt.t.nameStr.isEmpty() ? mt.t.id : as.strings().get(mt.t.nameStr, mt.t.id);
        MonsterActor a = new MonsterActor(m, mt.doll, mt.look, unitPal, animData, mt.token, nm);
        world.monsters.add(m);
        mobs.add(a);
        return a;
    }

    /** Свободная клетка рядом с (cx, cy) для монстра или null. */
    private float[] freeSpot(int cx, int cy, int spread) {
        for (int tries = 0; tries < 12; tries++) {
            int x = cx + world.rng.nextInt(spread * 2 + 1) - spread, y = cy + world.rng.nextInt(spread * 2 + 1) - spread;
            if (world.walkable(x, y) && !world.crowded(null, x + 0.5f, y + 0.5f)) return new float[]{x + 0.5f, y + 0.5f};
        }
        return null;
    }

    /**
     * Естественный спавн по Levels.txt: типы из mon1..mon25 (случайные NumMon штук), группы MinGrp..MaxGrp из MonStats.txt.
     * Число монстров — приближение (проходимые субтайлы * MonDen / 1 000 000, от 6 до 150); не ближе 24 субтайлов от старта.
     */
    private void populate(int levelId) {
        TxtTable lv = table(data, "levels");
        if (lv == null) return;
        TxtTable.Row row = lv.find("Id", String.valueOf(levelId));
        if (row == null) return;
        int numMon = row.getInt("NumMon", 0), den = row.getInt("MonDen", 0);
        if (numMon <= 0 || den <= 0) return;
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            String id = row.get("mon" + i);
            if (!id.isEmpty() && !id.equals("0")) ids.add(id);
        }
        List<MobType> types = new ArrayList<>();
        while (!ids.isEmpty() && types.size() < numMon) {
            MobType mt = mobType(ids.remove(world.rng.nextInt(ids.size())));
            if (mt.ok) types.add(mt);
        }
        if (types.isEmpty()) return;
        long open = 0;
        for (boolean b : world.blocked) if (!b) open++;
        int total = (int) Math.max(6, Math.min(150, open * den / 1000000L));
        int placed = 0;
        for (int tries = 0; placed < total && tries < total * 40; tries++) {
            MobType mt = types.get(world.rng.nextInt(types.size()));
            int cx = world.rng.nextInt(world.w), cy = world.rng.nextInt(world.h);
            if (!world.walkable(cx, cy) || Math.hypot(cx - startX, cy - startY) < 24) continue;
            int n = world.rng.range(mt.t.minGrp, mt.t.maxGrp);
            for (int k = 0; k < n; k++) {
                float[] p = freeSpot(cx, cy, 3);
                if (p == null) continue;
                spawnMonster(mt, p[0], p[1]);
                placed++;
            }
        }
        log.add("монстров: " + placed + ", типов " + types.size());
    }

    /** Стая монстров из списка Кровавой Пустоши рядом с героем — чтобы проверять бой там, где монстров нет (город). */
    private void spawnDebugPack() {
        if (debugPool == null) {
            debugPool = new ArrayList<>();
            TxtTable lv = table(data, "levels");
            TxtTable.Row row = lv == null ? null : lv.find("Id", "2");
            List<String> ids = new ArrayList<>();
            if (row != null) {
                for (int i = 1; i <= 25; i++) {
                    String id = row.get("mon" + i);
                    if (!id.isEmpty() && !id.equals("0")) ids.add(id);
                }
            }
            if (ids.isEmpty()) {
                ids.add("zombie1");
                ids.add("fallen1");
                ids.add("skeleton1");
            }
            for (String id : ids) {
                MobType mt = mobType(id);
                if (mt.ok) debugPool.add(mt.t);
            }
        }
        if (debugPool.isEmpty()) {
            notice("No monster data");
            return;
        }
        MonsterTemplate t = debugPool.get(world.rng.nextInt(debugPool.size()));
        MobType mt = mobType(t.id);
        int n = Math.max(2, world.rng.range(t.minGrp, t.maxGrp));
        int count = 0;
        for (int k = 0; k < n; k++) {
            double ang = world.rng.nextInt(360) * Math.PI / 180.0;
            float[] p = freeSpot((int) (player.x + Math.cos(ang) * 9), (int) (player.y + Math.sin(ang) * 9), 2);
            if (p == null) continue;
            spawnMonster(mt, p[0], p[1]).m.aggro = true;
            count++;
        }
        notice(count > 0 ? "Spawned: " + (mt.t.nameStr.isEmpty() ? mt.t.id : as.strings().get(mt.t.nameStr, mt.t.id)) + " x" + count : "No room to spawn");
    }

    private void notice(String s) {
        notice = s;
        noticeTicks = 75;
    }

    /** Монстр под точкой касания (если несколько — тот, что ближе к зрителю). */
    private MonsterActor mobAt(int px, int py) {
        MonsterActor best = null;
        for (MonsterActor a : mobs) {
            int sx = Math.round(LevelRenderer.screenX(a.m.x, a.m.y, camX, camY)), sy = Math.round(LevelRenderer.screenY(a.m.x, a.m.y, camX, camY));
            if (a.hitTest(px, py, sx, sy) && (best == null || a.m.x + a.m.y > best.m.x + best.m.y)) best = a;
        }
        return best;
    }

    private GroundItem dropAt(int px, int py) {
        GroundItem best = null;
        float bd = 1e9f;
        for (GroundItem g : world.drops) {
            int sx = Math.round(LevelRenderer.screenX(g.x, g.y, camX, camY)), sy = Math.round(LevelRenderer.screenY(g.x, g.y, camX, camY));
            if (Math.abs(px - sx) <= 34 && py >= sy - 44 && py <= sy + 10) {
                float d = Math.abs(px - sx) + Math.abs(py - sy + 20);
                if (d < bd) {
                    bd = d;
                    best = g;
                }
            }
        }
        return best;
    }

    private void updatePickup() {
        GroundItem g = pickupTarget;
        if (g == null) return;
        if (!world.drops.contains(g)) {
            pickupTarget = null;
            return;
        }
        if (Math.hypot(player.x - g.x, player.y - g.y) > 1.8f) return;
        pickupTarget = null;
        if (g.gold > 0) {
            hero.inv.gold += g.gold;
            world.drops.remove(g);
        } else if (g.item != null) {
            if (hero.inv.pack.add(g.item)) world.drops.remove(g);
            else notice("My backpack is full");
        } else {
            world.drops.remove(g);
        }
    }

    /** Возрождение: герой встаёт на стартовой точке с полной жизнью, монстры рядом с ней исчезают (как в городе после смерти). */
    private void respawn() {
        for (Iterator<Monster> it = world.monsters.iterator(); it.hasNext(); ) {
            Monster m = it.next();
            if (Math.hypot(m.x - startX, m.y - startY) < 40) it.remove();
        }
        player.revive(world);
        player.x = startX;
        player.y = startY;
        focus = null;
        pickupTarget = null;
        deathTick = 0;
        snapCamera();
    }

    /** Спрайт предмета на земле: flippyfile из armor/weapons/misc, последний кадр — предмет уже лежит. */
    private Dc6 groundSprite(String code) {
        String key = code.toLowerCase(Locale.ROOT);
        if (groundSprites.containsKey(key)) return groundSprites.get(key);
        Dc6 d = null;
        for (String tn : new String[]{"armor", "weapons", "misc"}) {
            TxtTable t = table(data, tn);
            TxtTable.Row r = t == null ? null : t.find("code", code);
            if (r == null) continue;
            String f = r.get("flippyfile");
            if (!f.isEmpty()) d = as.dc6("data\\global\\items\\" + f + ".dc6");
            break;
        }
        groundSprites.put(key, d);
        return d;
    }

    /** Предмет или золото на земле: спрайт (если есть) и название над ним; касание — подойти и подобрать. */
    private final class DropActor implements LevelRenderer.Actor {
        final GroundItem g;

        DropActor(GroundItem g) {
            this.g = g;
        }

        @Override
        public float x() {
            return g.x;
        }

        @Override
        public float y() {
            return g.y;
        }

        @Override
        public void draw(Fb fb, int sx, int sy) {
            Dc6 spr = groundSprite(g.item != null ? g.item.base.code : g.code);
            if (spr != null && spr.frames.length > 0) {
                Dc6.Frame f = spr.frames[spr.frames.length - 1];
                fb.blit(f.pixels, f.width, f.height, sx - f.width / 2, sy - f.height / 2, unitPal);
            } else {
                fb.fillRect(sx - 4, sy - 3, 8, 5, g.gold > 0 ? 0xFFE0B030 : 0xFF909090);
            }
            String label = g.gold > 0 ? g.gold + " Gold" : g.item != null ? g.item.displayName() : g.code;
            int color = g.gold > 0 ? Text.GOLD : g.item != null ? g.item.color() : Text.WHITE;
            Text.drawShadow(fb, as, 16, label, sx, sy - 34, color, Text.CENTER);
        }
    }

    /** Имя и полоска жизни монстра, которого бьёт герой (вверху по центру, как в оригинале). */
    private void drawFocus(Fb fb) {
        if (focus == null || focus.dead || focusTicks <= 0) return;
        MonsterActor a = null;
        for (MonsterActor x : mobs) if (x.m == focus) a = x;
        if (a == null) return;
        Text.drawShadow(fb, as, 16, a.displayName, 320, 6, Text.WHITE, Text.CENTER);
        float f = Math.max(0f, Math.min(1f, focus.life / (float) Math.max(1, focus.maxLife)));
        fb.fillRect(267, 26, 106, 8, 0xFF000000);
        fb.fillRect(269, 28, Math.round(102 * f), 4, 0xFFB01818);
    }

    private void drawTexts(Fb fb) {
        for (com.jv.diablo2.game.FloatingText t : world.texts) {
            int sx = Math.round(LevelRenderer.screenX(t.x, t.y, camX, camY)), sy = Math.round(LevelRenderer.screenY(t.x, t.y, camX, camY));
            Text.drawShadow(fb, as, 16, t.text, sx, sy - 60, t.color & 0xFFFFFF | 0xFF000000, Text.CENTER);
        }
    }

    // ---------------------------------------------------------------- ввод

    private void walkToPointer() {
        float wx = LevelRenderer.worldX(ptrX, ptrY, camX, camY), wy = LevelRenderer.worldY(ptrX, ptrY, camX, camY);
        player.moveTo(world, wx, wy);
    }

    /** action — ACTION_DOWN/UP/MOVE, x/y — координаты в пикселях кадра 640x480. */
    public void pointer(int action, int x, int y) {
        ptrX = x;
        ptrY = y;
        if (action == ACTION_DOWN) {
            if (player.dead) {
                if (deathTick > 60) respawn();
                return;
            }
            if (debugTools && Hud.in(SPAWN_BTN, x, y)) {
                spawnDebugPack();
                return;
            }
            if (Hud.in(Hud.RUN, x, y)) {
                downRun = true;
                hud.pressedRun = true;
            } else if (Hud.in(Hud.MINI_TOGGLE, x, y)) {
                downToggle = true;
                hud.pressedToggle = true;
            } else if (hud.miniAt(x, y) >= 0) {
                downMini = hud.miniAt(x, y);
                hud.pressedMini = downMini;
            } else if (!hud.covers(x, y)) {
                MonsterActor mob = mobAt(x, y);
                GroundItem item = mob == null ? dropAt(x, y) : null;
                if (mob != null) {
                    pickupTarget = null;
                    focus = mob.m;
                    focusTicks = 150;
                    player.attack(mob.m);
                    holding = true;
                    holdMode = 1;
                    holdTick = 0;
                } else if (item != null) {
                    pickupTarget = item;
                    holding = false;
                    player.moveTo(world, item.x, item.y);
                } else {
                    pickupTarget = null;
                    holdMode = 0;
                    holding = true;
                    holdTick = 0;
                    walkToPointer();
                }
            }
        } else if (action == ACTION_MOVE) {
            if (downMini >= 0) hud.pressedMini = hud.miniAt(x, y) == downMini ? downMini : -1;
            if (downRun) hud.pressedRun = Hud.in(Hud.RUN, x, y);
            if (downToggle) hud.pressedToggle = Hud.in(Hud.MINI_TOGGLE, x, y);
        } else if (action == ACTION_UP || action == 3) {
            boolean cancel = action == 3;
            if (cancel) x = y = -1000;
            if (downRun && Hud.in(Hud.RUN, x, y)) hud.running = !hud.running;
            if (downToggle && Hud.in(Hud.MINI_TOGGLE, x, y)) hud.miniOpen = !hud.miniOpen;
            if (downMini >= 0 && hud.miniAt(x, y) == downMini && listener != null) listener.onMini(Hud.MINI_NAMES[downMini]);
            downRun = downToggle = false;
            downMini = -1;
            hud.pressedRun = hud.pressedToggle = false;
            hud.pressedMini = -1;
            holding = false;
        }
    }

    public int fileDir() {
        return fileDir;
    }

    public float camX() {
        return camX;
    }

    public float camY() {
        return camY;
    }
}
