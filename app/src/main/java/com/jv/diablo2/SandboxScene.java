package com.jv.diablo2;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;

import com.jv.diablo2.game.CharClass;
import com.jv.diablo2.game.Entity;
import com.jv.diablo2.game.FloatingText;
import com.jv.diablo2.game.GroundItem;
import com.jv.diablo2.game.Hero;
import com.jv.diablo2.game.Iso;
import com.jv.diablo2.game.Monster;
import com.jv.diablo2.game.Player;
import com.jv.diablo2.game.PlayerStats;
import com.jv.diablo2.game.SaveGame;
import com.jv.diablo2.game.World;
import com.jv.diablo2.game.item.Inventory;
import com.jv.diablo2.game.item.Item;
import com.jv.diablo2.game.skill.SkillDb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Песочница: бой, опыт, дроп, подбор предметов, инвентарь с экипировкой, дерево навыков, сохранения.
 * Спрайты (DCC/COF) и карты из DS1 подключаются на следующем этапе.
 */
public final class SandboxScene implements Scene {
    private enum Mode { NONE, INV, SKILLS }

    private static final int[][] EQ = {{2, 0, 2, 2}, {4, 0, 1, 1}, {2, 2, 2, 3}, {0, 2, 2, 4}, {4, 2, 2, 4},
            {2, 6, 1, 1}, {3, 6, 1, 1}, {2, 5, 2, 1}, {4, 6, 2, 2}, {0, 6, 2, 2}};
    private static final String[] SLOT_NAMES = {"Голова", "Шея", "Тело", "Оружие", "Щит", "Кольцо", "Кольцо", "Пояс", "Ноги", "Руки"};

    private final World world;
    private final Hero hero;
    private final ConcurrentLinkedQueue<float[]> touches = new ConcurrentLinkedQueue<>();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF btnInv = new RectF(), btnSkl = new RectF(), panel = new RectF(), closeRect = new RectF();
    private final RectF[] plus = {new RectF(), new RectF(), new RectF(), new RectF()};
    private int lastW = 1280, lastH = 720;
    private float zoom = 2f, camX, camY;
    private int holdTimer;
    private float u, eqX, eqY, cell, packX, packY, listTop, rowH;

    private volatile Mode mode = Mode.NONE;
    private Item selected;
    private String message = "";
    private float scroll, downY, lastY;
    private boolean dragging;
    private GroundItem pending;

    private final MapRenderer renderer;
    private CharSprite heroSprite;
    private boolean spriteTried;

    /** Тестовая арена с новым героем. */
    public SandboxScene(CharClass cls) {
        this(new Hero(cls), true);
    }

    /** Тестовая арена с существующим героем. */
    public SandboxScene(Hero hero) {
        this(hero, false);
    }

    private SandboxScene(Hero hero, boolean fresh) {
        this(hero, World.generate(System.nanoTime(), 120, 120, Session.monsters, hero, 22), null, fresh);
    }

    /** Игра на готовой карте (например, из DS1/DT1). */
    public SandboxScene(Hero hero, World world, MapRenderer renderer) {
        this(hero, world, renderer, false);
    }

    private SandboxScene(Hero hero, World world, MapRenderer renderer, boolean fresh) {
        this.hero = hero;
        this.world = world;
        this.renderer = renderer;
        if (Session.experience != null) world.experience = Session.experience;
        world.treasure = Session.treasure;
        world.itemGen = Session.itemGen;
        if (fresh && Session.itemGen != null) starterKit(hero, world.rng);
    }

    public Hero hero() {
        return hero;
    }

    @Override
    public boolean onBack() {
        if (mode != Mode.NONE) {
            mode = Mode.NONE;
            return true;
        }
        Saves.save(hero);
        Nav.go(new MainMenuScene());
        return true;
    }

    /** Стартовый набор: короткий меч, баклер, шапка, стёганка, сапоги, перчатки, пояс (если есть в таблицах). */
    public static void starterKit(Hero hero, com.jv.diablo2.game.D2Random rng) {
        if (Session.itemGen == null || Session.items == null) return;
        String[] codes = {"ssd", "buc", "cap", "qui", "lbt", "lgl", "lbl"};
        for (String code : codes) {
            Item it = Session.itemGen.generate(code, 1, 0, Item.Quality.NORMAL, rng);
            if (it == null) continue;
            hero.inv.pack.add(it);
            hero.inv.equip(it, hero.stats, Session.items.types);
        }
    }

    public void save(File f) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            SaveGame.write(hero, out);
        }
    }

    public static SandboxScene load(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            return new SandboxScene(SaveGame.read(in, Session.items));
        }
    }

    @Override
    public void onTouch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            touches.add(new float[]{action, x, y});
        }
    }

    // ---------- ввод ----------

    private float worldX(float sx, float sy) {
        return Iso.toWorldX((sx - lastW / 2f) / zoom + camX, (sy - lastH / 2f) / zoom + camY + 10f);
    }

    private float worldY(float sx, float sy) {
        return Iso.toWorldY((sx - lastW / 2f) / zoom + camX, (sy - lastH / 2f) / zoom + camY + 10f);
    }

    @Override
    public void update() {
        layout(lastW, lastH);
        float[] t;
        while ((t = touches.poll()) != null) handle((int) t[0], t[1], t[2]);
        Player pl = world.player;
        if (pending != null && pl.distTo(pending.x, pending.y) <= 3f) {
            pickup(pending);
            pending = null;
        }
        if (mode == Mode.NONE) world.update();
    }

    private void handle(int action, float x, float y) {
        if (mode != Mode.NONE) {
            overlayTouch(action, x, y);
            return;
        }
        Player pl = world.player;
        if (action == MotionEvent.ACTION_UP) return;
        if (action == MotionEvent.ACTION_DOWN && (btnInv.contains(x, y) || btnSkl.contains(x, y))) {
            mode = btnInv.contains(x, y) ? Mode.INV : Mode.SKILLS;
            selected = null;
            message = "";
            scroll = 0;
            return;
        }
        if (pl.dead) {
            if (action == MotionEvent.ACTION_DOWN) pl.revive(world);
            return;
        }
        boolean move = action == MotionEvent.ACTION_MOVE;
        if (move && holdTimer > 0) return;
        holdTimer = 5;
        float wx = worldX(x, y), wy = worldY(x, y);
        if (!move) {
            GroundItem g = nearestDrop(wx, wy, 1.8f);
            if (g != null) {
                pending = g;
                if (pl.distTo(g.x, g.y) > 3f) pl.moveTo(world, g.x, g.y);
                return;
            }
        }
        pending = null;
        Monster m = world.monsterAt(wx, wy, 2.5f);
        if (m != null) pl.attack(m);
        else pl.moveTo(world, wx, wy);
    }

    private GroundItem nearestDrop(float wx, float wy, float r) {
        GroundItem best = null;
        float bd = r;
        for (GroundItem g : world.drops) {
            float d = (float) Math.hypot(g.x - wx, g.y - wy);
            if (d < bd) {
                bd = d;
                best = g;
            }
        }
        return best;
    }

    private void pickup(GroundItem g) {
        if (!world.drops.contains(g)) return;
        if (g.gold > 0) {
            hero.inv.gold += g.gold;
            world.drops.remove(g);
            world.say("+" + g.gold + " золота");
        } else if (g.item != null) {
            if (hero.inv.pack.add(g.item)) {
                world.drops.remove(g);
                world.say("Подобрано: " + g.item.displayName());
            } else {
                world.say("Рюкзак полон");
            }
        } else {
            world.say("Это нельзя подобрать: " + g.code);
        }
    }

    private void overlayTouch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) {
            downY = lastY = y;
            dragging = false;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (Math.abs(y - downY) > 14) dragging = true;
            if (mode == Mode.SKILLS && dragging) scroll = Math.max(0, scroll - (y - lastY));
            lastY = y;
        } else if (!dragging) {
            if (closeRect.contains(x, y) || !panel.contains(x, y)) {
                mode = Mode.NONE;
                return;
            }
            if (mode == Mode.INV) tapInventory(x, y);
            else tapSkills(x, y);
        }
    }

    private RectF slotRect(int i) {
        return new RectF(eqX + EQ[i][0] * u, eqY + EQ[i][1] * u, eqX + (EQ[i][0] + EQ[i][2]) * u, eqY + (EQ[i][1] + EQ[i][3]) * u);
    }

    private void tapInventory(float x, float y) {
        PlayerStats s = hero.stats;
        for (int k = 0; k < 4; k++) {
            if (plus[k].contains(x, y)) {
                message = s.spendStat("sdve".charAt(k)) ? "" : "Нет свободных очков";
                return;
            }
        }
        if (Session.items == null) {
            message = "Нет данных предметов";
            return;
        }
        Inventory.Slot[] slots = Inventory.Slot.values();
        for (int i = 0; i < slots.length; i++) {
            if (!slotRect(i).contains(x, y)) continue;
            Item it = hero.inv.equipped(slots[i]);
            if (it == null) {
                selected = null;
            } else if (selected == it) {
                String err = hero.inv.unequip(slots[i], s);
                message = err == null ? "Снято: " + it.displayName() : err;
                selected = null;
            } else {
                selected = it;
                message = "";
            }
            return;
        }
        int cx = (int) Math.floor((x - packX) / cell), cy = (int) Math.floor((y - packY) / cell);
        Item it = hero.inv.pack.at(cx, cy);
        if (it == null) {
            selected = null;
        } else if (selected == it) {
            String err = hero.inv.equip(it, s, Session.items.types);
            message = err == null ? "Надето: " + it.displayName() : err;
            selected = null;
        } else {
            selected = it;
            message = "Коснитесь ещё раз, чтобы надеть";
        }
    }

    private void tapSkills(float x, float y) {
        SkillDb db = Session.skillDb;
        if (db == null) {
            message = "Нет данных Skills.txt";
            return;
        }
        List<SkillDb.Skill> list = db.forClass(hero.stats.cls);
        int i = (int) Math.floor((y - listTop + scroll) / rowH);
        if (y < listTop || i < 0 || i >= list.size()) return;
        SkillDb.Skill sk = list.get(i);
        String err = hero.skills.canAllocate(sk, hero.stats, db);
        if (err != null) {
            message = sk.name + ": " + err;
        } else {
            hero.skills.allocate(sk, hero.stats, db);
            message = sk.name + " -> ур. " + hero.skills.level(sk.name);
        }
    }

    // ---------- отрисовка ----------

    private void layout(int w, int h) {
        panel.set(w * 0.04f, h * 0.08f, w * 0.96f, h * 0.90f);
        closeRect.set(panel.right - h * 0.10f, panel.top, panel.right, panel.top + h * 0.10f);
        btnInv.set(w / 2f - h * 0.24f, h * 0.80f, w / 2f - h * 0.02f, h * 0.89f);
        btnSkl.set(w / 2f + h * 0.02f, h * 0.80f, w / 2f + h * 0.24f, h * 0.89f);
        float top = panel.top + h * 0.10f;
        u = Math.min(panel.width() * 0.40f / 6f, (panel.bottom - top - 10) / 8f);
        eqX = panel.left + 10;
        eqY = top;
        cell = Math.min(panel.width() * 0.52f / 10f, h * 0.40f / 4f);
        packX = panel.right - 10 - cell * 10;
        packY = top;
        listTop = top + h * 0.05f;
        rowH = h * 0.075f;
    }

    private void diamond(float cx, float cy) {
        path.reset();
        path.moveTo(Iso.toScreenX(cx, cy), Iso.toScreenY(cx, cy));
        path.lineTo(Iso.toScreenX(cx + 1, cy), Iso.toScreenY(cx + 1, cy));
        path.lineTo(Iso.toScreenX(cx + 1, cy + 1), Iso.toScreenY(cx + 1, cy + 1));
        path.lineTo(Iso.toScreenX(cx, cy + 1), Iso.toScreenY(cx, cy + 1));
        path.close();
    }

    @Override
    public void draw(Canvas c, int w, int h) {
        lastW = w;
        lastH = h;
        layout(w, h);
        zoom = Math.max(1.2f, h / 380f);
        Player pl = world.player;
        camX = Iso.toScreenX(pl.x, pl.y);
        camY = Iso.toScreenY(pl.x, pl.y);
        c.drawColor(0xFF0B0A09);
        c.save();
        c.translate(w / 2f, h / 2f);
        c.scale(zoom, zoom);
        c.translate(-camX, -camY);

        float hw = w / 2f / zoom, hh = h / 2f / zoom;
        float[] xs = {camX - hw, camX + hw, camX - hw, camX + hw};
        float[] ys = {camY - hh, camY - hh, camY + hh, camY + hh};
        float minX = 1e9f, maxX = -1e9f, minY = 1e9f, maxY = -1e9f;
        for (int i = 0; i < 4; i++) {
            float wx = Iso.toWorldX(xs[i], ys[i]), wy = Iso.toWorldY(xs[i], ys[i]);
            minX = Math.min(minX, wx);
            maxX = Math.max(maxX, wx);
            minY = Math.min(minY, wy);
            maxY = Math.max(maxY, wy);
        }
        if (renderer != null) {
            renderer.drawFloor(c, camX - hw, camY - hh, camX + hw, camY + hh);
            renderer.drawWalls(c, camX - hw, camY - hh, camX + hw, camY + hh, (pl.x + pl.y) / 5f, false);
        } else {
        int x0 = Math.max(0, (int) minX - 1), x1 = Math.min(world.w - 1, (int) maxX + 2);
        int y0 = Math.max(0, (int) minY - 1), y1 = Math.min(world.h - 1, (int) maxY + 2);
        p.setStyle(Paint.Style.FILL);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                diamond(x, y);
                if (world.blocked[y * world.w + x]) {
                    p.setColor(0xFF2A2622);
                    c.save();
                    c.translate(0, -7);
                    c.drawPath(path, p);
                    c.restore();
                    p.setColor(0xFF1A1714);
                    c.drawPath(path, p);
                } else {
                    p.setColor(((x + y) & 1) == 0 ? 0xFF3B352B : 0xFF352F26);
                    c.drawPath(path, p);
                }
            }
        }
        }
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(9);
        for (GroundItem it : world.drops) {
            float sx = Iso.toScreenX(it.x, it.y), sy = Iso.toScreenY(it.x, it.y);
            int col = it.gold > 0 ? 0xFFE8C040 : (it.item != null ? it.item.color() : 0xFFB0B0B0);
            p.setColor(col);
            c.drawRect(sx - 4, sy - 3, sx + 4, sy + 1, p);
            c.drawText(it.label(), sx, sy - 6, p);
        }

        List<Entity> order = new ArrayList<Entity>(world.monsters);
        order.add(pl);
        Collections.sort(order, new Comparator<Entity>() {
            @Override
            public int compare(Entity a, Entity b) {
                return Float.compare(a.x + a.y, b.x + b.y);
            }
        });
        for (Entity e : order) {
            float sx = Iso.toScreenX(e.x, e.y), sy = Iso.toScreenY(e.x, e.y);
            p.setColor(0x66000000);
            c.drawOval(new RectF(sx - 9, sy - 3, sx + 9, sy + 3), p);
            if (e.dead) {
                p.setColor(0xFF4A2A2A);
                c.drawOval(new RectF(sx - 8, sy - 2, sx + 8, sy + 2), p);
                continue;
            }
            if (e == pl) {
                if (!spriteTried) {
                    spriteTried = true;
                    heroSprite = CharSprite.tryLoad(hero.stats.cls);
                }
                if (heroSprite != null) heroSprite.draw(c, sx, sy, pl, System.currentTimeMillis());
            }
            if (e == pl && heroSprite != null) {
                // герой нарисован спрайтом
            } else {
                p.setColor(e == pl ? 0xFF3A6FD0 : (((Monster) e).aggro ? 0xFFB02828 : 0xFF7A4A2A));
                c.drawCircle(sx, sy - 11, 7, p);
            }
            if (e != pl) {
                float f = e.life / (float) e.maxLife;
                p.setColor(0xFF200000);
                c.drawRect(sx - 9, sy - 25, sx + 9, sy - 22, p);
                p.setColor(0xFFD02020);
                c.drawRect(sx - 9, sy - 25, sx - 9 + 18 * f, sy - 22, p);
            }
        }
        if (renderer != null) renderer.drawWalls(c, camX - hw, camY - hh, camX + hw, camY + hh, (pl.x + pl.y) / 5f, true);
        p.setTextSize(11);
        for (FloatingText ft : world.texts) {
            p.setColor(ft.color);
            c.drawText(ft.text, Iso.toScreenX(ft.x, ft.y), Iso.toScreenY(ft.x, ft.y) - 28, p);
        }
        c.restore();

        drawHud(c, w, h);
        if (mode == Mode.INV) drawInventory(c, w, h);
        else if (mode == Mode.SKILLS) drawSkills(c, w, h);
    }

    private void orb(Canvas c, float cx, float cy, float r, float frac, int color, String label) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF140808);
        c.drawCircle(cx, cy, r, p);
        c.save();
        path.reset();
        path.addCircle(cx, cy, r - 2, Path.Direction.CW);
        c.clipPath(path);
        p.setColor(color);
        c.drawRect(cx - r, cy + r - 2 * r * Math.max(0f, Math.min(1f, frac)), cx + r, cy + r, p);
        c.restore();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        p.setColor(0xFF7A6A4A);
        c.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(r * 0.34f);
        c.drawText(label, cx, cy + r * 0.12f, p);
    }

    private void button(Canvas c, RectF r, String text, boolean hot) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(hot ? 0xFF6A5A2A : 0xCC2A2418);
        c.drawRoundRect(r, 12, 12, p);
        p.setColor(0xFFE0D8C0);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(r.height() * 0.45f);
        c.drawText(text, r.centerX(), r.centerY() + r.height() * 0.16f, p);
    }

    private void drawHud(Canvas c, int w, int h) {
        PlayerStats s = hero.stats;
        float r = h * 0.14f;
        orb(c, r * 1.15f, h - r * 1.15f, r, s.life / (float) Math.max(1, s.maxLife()), 0xFFB01818, s.life + "/" + s.maxLife());
        orb(c, w - r * 1.15f, h - r * 1.15f, r, s.mana / (float) Math.max(1, s.maxMana()), 0xFF2040C0, s.mana + "/" + s.maxMana());
        if (mode == Mode.NONE) {
            button(c, btnInv, "Инвентарь" + (s.statPoints > 0 ? " •" : ""), s.statPoints > 0);
            button(c, btnSkl, "Навыки" + (s.skillPoints > 0 ? " •" : ""), s.skillPoints > 0);
        }
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(h * 0.04f);
        p.setColor(0xFFD8C8A0);
        c.drawText(s.cls.title + "  ур. " + s.clvl + "   опыт " + s.exp + "   золото " + hero.inv.gold, w / 2f, h - h * 0.03f, p);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(h * 0.036f);
        float y = h * 0.06f;
        for (String line : world.log) {
            p.setColor(0xFFE0D8C0);
            c.drawText(line, h * 0.02f, y, p);
            y += h * 0.042f;
        }
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(0xFF8A8270);
        p.setTextSize(h * 0.03f);
        c.drawText(Session.status, w - h * 0.02f, h * 0.17f, p);
        if (world.player.dead) {
            p.setColor(0xAA000000);
            c.drawRect(0, 0, w, h, p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0xFFD04040);
            p.setTextSize(h * 0.09f);
            c.drawText("ВЫ ПОГИБЛИ", w / 2f, h / 2f, p);
            p.setColor(0xFFE0D8C0);
            p.setTextSize(h * 0.04f);
            c.drawText("Коснитесь экрана, чтобы возродиться", w / 2f, h / 2f + h * 0.08f, p);
        }
    }

    private void panelBase(Canvas c, String title) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xEE14110C);
        c.drawRoundRect(panel, 16, 16, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        p.setColor(0xFF6A5A3A);
        c.drawRoundRect(panel, 16, 16, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(0xFFE0C888);
        p.setTextSize(lastH * 0.05f);
        c.drawText(title, panel.left + 14, panel.top + lastH * 0.07f, p);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText("✕", closeRect.centerX(), closeRect.centerY() + lastH * 0.018f, p);
    }

    private String fit(String s, float maxW) {
        while (s.length() > 1 && p.measureText(s) > maxW) s = s.substring(0, s.length() - 1);
        return s;
    }

    private void drawItemBox(Canvas c, RectF r, Item it, boolean sel) {
        p.setStyle(Paint.Style.FILL);
        p.setColor((it.color() & 0x00FFFFFF) | 0x55000000);
        c.drawRect(r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sel ? 4 : 2);
        p.setColor(sel ? 0xFFFFFFFF : it.color());
        c.drawRect(r, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(it.color());
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(Math.min(r.width() / 5f, lastH * 0.032f));
        c.drawText(fit(it.base.name, r.width() - 6), r.centerX(), r.centerY() + p.getTextSize() / 3f, p);
    }

    private void drawInventory(Canvas c, int w, int h) {
        PlayerStats s = hero.stats;
        panelBase(c, "Инвентарь   золото " + hero.inv.gold);
        Inventory.Slot[] slots = Inventory.Slot.values();
        for (int i = 0; i < slots.length; i++) {
            RectF r = slotRect(i);
            Item it = hero.inv.equipped(slots[i]);
            if (it == null) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2);
                p.setColor(0xFF4A4030);
                c.drawRect(r, p);
                p.setStyle(Paint.Style.FILL);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(h * 0.028f);
                p.setColor(0xFF6A604A);
                c.drawText(SLOT_NAMES[i], r.centerX(), r.centerY() + h * 0.01f, p);
            } else {
                drawItemBox(c, r, it, it == selected);
            }
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        p.setColor(0xFF3A3224);
        for (int i = 0; i <= 10; i++) c.drawLine(packX + i * cell, packY, packX + i * cell, packY + 4 * cell, p);
        for (int j = 0; j <= 4; j++) c.drawLine(packX, packY + j * cell, packX + 10 * cell, packY + j * cell, p);
        for (Item it : hero.inv.pack.items()) {
            int[] pos = hero.inv.pack.posOf(it);
            drawItemBox(c, new RectF(packX + pos[0] * cell, packY + pos[1] * cell,
                    packX + (pos[0] + it.width()) * cell, packY + (pos[1] + it.height()) * cell), it, it == selected);
        }
        // характеристики
        float sy = packY + 4 * cell + h * 0.06f, rh = h * 0.055f;
        String[] names = {"Сила", "Ловкость", "Живучесть", "Энергия"};
        int[] vals = {s.totalStr(), s.totalDex(), s.totalVit(), s.totalEne()};
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(h * 0.04f);
        for (int k = 0; k < 4; k++) {
            float y = sy + k * rh;
            p.setColor(0xFFD8C8A0);
            c.drawText(names[k] + ": " + vals[k], packX, y, p);
            plus[k].set(packX + cell * 3.6f, y - rh * 0.8f, packX + cell * 3.6f + rh, y + rh * 0.1f);
            if (s.statPoints > 0) button(c, plus[k], "+", true);
            else plus[k].set(-1, -1, -1, -1);
        }
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(h * 0.034f);
        p.setColor(0xFFB8B098);
        float y2 = sy + 4 * rh;
        c.drawText("Очков: " + s.statPoints + "   Защита " + s.defense() + "   Метк. " + s.attackRating(), packX, y2, p);
        c.drawText("Урон " + s.weaponMin + "-" + s.weaponMax + "   Блок " + s.shieldBlock + "%", packX, y2 + rh * 0.8f, p);
        // подсказка
        float tx = packX + cell * 5.0f, ty = sy;
        if (selected != null) {
            List<String> t = selected.tooltip();
            for (int i = 0; i < t.size() && i < 11; i++) {
                p.setColor(i == 0 ? selected.color() : 0xFFB8B8D8);
                p.setTextSize(h * (i == 0 ? 0.038f : 0.032f));
                c.drawText(fit(t.get(i), panel.right - tx - 10), tx, ty + i * h * 0.04f, p);
            }
        }
        p.setColor(0xFFE0A060);
        p.setTextSize(h * 0.034f);
        c.drawText(message, panel.left + 14, panel.bottom - 12, p);
    }

    private void drawSkills(Canvas c, int w, int h) {
        PlayerStats s = hero.stats;
        panelBase(c, "Навыки: " + s.cls.title + "   очков " + s.skillPoints);
        SkillDb db = Session.skillDb;
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.LEFT);
        if (db == null) {
            p.setColor(0xFFE0A060);
            p.setTextSize(h * 0.04f);
            c.drawText("Нет данных Skills.txt — подключите MPQ", panel.left + 14, listTop + rowH, p);
            return;
        }
        List<SkillDb.Skill> list = db.forClass(s.cls);
        c.save();
        c.clipRect(panel.left, listTop, panel.right, panel.bottom - h * 0.06f);
        p.setTextSize(h * 0.04f);
        for (int i = 0; i < list.size(); i++) {
            SkillDb.Skill sk = list.get(i);
            float y = listTop + i * rowH - scroll;
            if (y + rowH < listTop || y > panel.bottom) continue;
            int lvl = hero.skills.level(sk.name);
            boolean can = hero.skills.canAllocate(sk, s, db) == null;
            p.setColor(lvl > 0 ? 0xFFE0D8A0 : (can ? 0xFFA0D0A0 : 0xFF807868));
            c.drawText(fit(sk.name, panel.width() * 0.45f), panel.left + 14, y + rowH * 0.7f, p);
            double cost = db.manaCost(sk, Math.max(1, lvl));
            String right = "ур. " + lvl + "/" + sk.maxLvl + "   треб. " + sk.reqLevel + "   мана " + String.format(java.util.Locale.ROOT, "%.1f", cost);
            p.setTextAlign(Paint.Align.RIGHT);
            c.drawText(right, panel.right - 14, y + rowH * 0.7f, p);
            p.setTextAlign(Paint.Align.LEFT);
        }
        c.restore();
        p.setColor(0xFFE0A060);
        p.setTextSize(h * 0.034f);
        c.drawText(message, panel.left + 14, panel.bottom - 12, p);
    }
}
