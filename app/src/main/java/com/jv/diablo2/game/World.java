package com.jv.diablo2.game;

import com.jv.diablo2.game.item.Item;
import com.jv.diablo2.game.item.ItemGenerator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Игровой мир (этап 1: процедурная карта на сетке субтайлов; загрузка DS1/DT1 — этап 2). */
public final class World {
    public final int w, h;
    public final D2Random rng;
    public final boolean[] blocked;
    public Player player;
    public final List<Monster> monsters = new ArrayList<>();
    public final List<FloatingText> texts = new ArrayList<>();
    public final List<GroundItem> drops = new ArrayList<>();
    public final ArrayDeque<String> log = new ArrayDeque<>();
    public Experience experience = Experience.fallback();
    public TreasureClasses treasure;     // может быть null, если данных игры нет
    public ItemGenerator itemGen;        // может быть null
    public long tick;

    public World(int w, int h, long seed) {
        this.w = w;
        this.h = h;
        this.rng = new D2Random(seed);
        this.blocked = new boolean[w * h];
    }

    public boolean walkable(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && !blocked[y * w + x];
    }

    public int[] nearestWalkable(int x, int y, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (walkable(x + dx, y + dy)) return new int[]{x + dx, y + dy};
                }
            }
        }
        return null;
    }

    /** true, если рядом с точкой (px, py) стоит другой живой монстр (ближе 0.9 субтайла). */
    public boolean crowded(Monster self, float px, float py) {
        for (Monster m : monsters) {
            if (m != self && !m.dead && Math.hypot(m.x - px, m.y - py) < 0.9) return true;
        }
        return false;
    }

    public void say(String s) {
        log.addLast(s);
        while (log.size() > 6) log.removeFirst();
    }

    public void floatText(float x, float y, String s, int color) {
        texts.add(new FloatingText(x, y, s, color));
    }

    public Monster monsterAt(float x, float y, float radius) {
        Monster best = null;
        float bd = radius;
        for (Monster m : monsters) {
            if (m.dead) continue;
            float d = m.distTo(x, y);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    public void onMonsterKilled(Monster m) {
        Player p = player;
        if (p == null) return;
        int lv = p.stats.gainExp(m.t.exp, experience);
        say(m.t.id + " повержен (+" + m.t.exp + " опыта)");
        QuestLog.Quest done = p.hero.quests.onKill(m.t.id);
        if (done != null) say("Задание выполнено: " + done.name);
        if (lv > 0) {
            say("Новый уровень: " + p.stats.clvl + "!");
            floatText(p.x, p.y, "LEVEL UP", 0xFF60FF60);
        }
        if (treasure != null && !m.t.treasureClass.isEmpty()) {
            for (String code : treasure.roll(m.t.treasureClass, m.t.level, 1, rng)) {
                float ox = m.x + (rng.nextInt(7) - 3) * 0.4f;
                float oy = m.y + (rng.nextInt(7) - 3) * 0.4f;
                GroundItem g = new GroundItem(ox, oy, code);
                if (code.toLowerCase().startsWith("gld")) {
                    g.gold = rng.range(m.t.level * 3, m.t.level * 10 + 5);   // приближённая формула
                    say("Золото: " + g.gold);
                } else {
                    g.item = itemGen == null ? null : itemGen.generate(code, m.t.level, p.stats.magicFind, null, rng);
                    say("Выпало: " + (g.item != null ? g.item.displayName() : code));
                }
                drops.add(g);
            }
        }
    }

    public void update() {
        tick++;
        if (player != null) player.update(this);
        for (Monster m : monsters) m.update(this);
        for (Iterator<Monster> it = monsters.iterator(); it.hasNext(); ) {
            if (it.next().corpseTimer > Monster.CORPSE_TICKS) it.remove();
        }
        for (Iterator<FloatingText> it = texts.iterator(); it.hasNext(); ) {
            FloatingText t = it.next();
            t.y -= 0.03f;
            if (--t.ttl <= 0) it.remove();
        }
    }

    /** Случайная карта: препятствия-глыбы, свободная зона вокруг старта, группы монстров. */
    public static World generate(long seed, int w, int h, List<MonsterTemplate> pool, CharClass cls, int groups) {
        return generate(seed, w, h, pool, new Hero(cls), groups);
    }

    public static World generate(long seed, int w, int h, List<MonsterTemplate> pool, Hero hero, int groups) {
        World wd = new World(w, h, seed);
        D2Random r = wd.rng;
        for (int i = 0; i < w * h / 80; i++) {
            int bx = r.nextInt(w), by = r.nextInt(h), bw = 1 + r.nextInt(5), bh = 1 + r.nextInt(5);
            for (int y = by; y < Math.min(h, by + bh); y++)
                for (int x = bx; x < Math.min(w, bx + bw); x++) wd.blocked[y * w + x] = true;
        }
        for (int x = 0; x < w; x++) {
            wd.blocked[x] = true;
            wd.blocked[(h - 1) * w + x] = true;
        }
        for (int y = 0; y < h; y++) {
            wd.blocked[y * w] = true;
            wd.blocked[y * w + w - 1] = true;
        }
        int cx = w / 2, cy = h / 2;
        for (int y = cy - 8; y <= cy + 8; y++)
            for (int x = cx - 8; x <= cx + 8; x++) wd.blocked[y * w + x] = false;

        wd.player = new Player(hero, cx + 0.5f, cy + 0.5f);

        if (pool == null || pool.isEmpty()) {
            pool = new ArrayList<>();
            pool.add(MonsterTemplate.fallback());
        }
        for (int g = 0; g < groups; g++) {
            MonsterTemplate t = pool.get(r.nextInt(pool.size()));
            int gx, gy, tries = 0;
            do {
                gx = 3 + r.nextInt(w - 6);
                gy = 3 + r.nextInt(h - 6);
                tries++;
            } while ((!wd.walkable(gx, gy) || Math.hypot(gx - cx, gy - cy) < 16) && tries < 200);
            int n = 3 + r.nextInt(3);
            for (int k = 0; k < n; k++) {
                int mx = gx + r.nextInt(5) - 2, my = gy + r.nextInt(5) - 2;
                if (wd.walkable(mx, my)) wd.monsters.add(new Monster(t, mx + 0.5f, my + 0.5f, r));
            }
        }
        wd.say("Добро пожаловать в песочницу JVDiablo2");
        return wd;
    }
}
