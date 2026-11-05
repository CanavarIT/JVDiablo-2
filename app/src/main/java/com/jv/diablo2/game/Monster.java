package com.jv.diablo2.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Монстр с ИИ: ждёт, замечает героя, преследует по A*, бьёт в ближнем бою. Длительности удара, получения урона и смерти
 * (в тиках по 25 в секунду) задаёт отрисовщик из анимаций COF и animdata.d2, поэтому удар приходится на ключевой кадр.
 */
public final class Monster extends Entity {
    public static final float REACH = 2.0f;
    public static final float AGGRO_RADIUS = 14f;
    /** Сколько тиков труп лежит на земле до исчезновения. */
    public static final int CORPSE_TICKS = 300;

    public final MonsterTemplate t;
    public boolean aggro;
    /** Тиков с момента смерти (0 пока жив). */
    public int corpseTimer;

    /** Куда смотрит монстр (вектор в мире) и шагнул ли он за последний тик. */
    public float faceDx = 1f, faceDy = 1f;
    public boolean moving;

    /** Длительности анимаций в тиках; выставляет отрисовщик. attackHitAt — тик удара от начала замаха. */
    public int attackTicks = 16, attackHitAt = 8, hitTicks = 6, deathTicks = 20;

    private int hitRecovery;
    private int attackTimer;
    private int repathTimer;
    private List<int[]> path = new ArrayList<>();
    private int pathIdx;

    public Monster(MonsterTemplate t, float x, float y, D2Random rng) {
        this.t = t;
        this.x = x;
        this.y = y;
        this.level = t.level;
        this.maxLife = rng.range(t.minHp, t.maxHp);
        this.life = maxLife;
    }

    /** Прогресс замаха 0..1 или -1, если не бьёт. */
    public float attackProgress() {
        return attackTimer > 0 ? 1f - attackTimer / (float) attackTicks : -1f;
    }

    /** Прогресс анимации получения удара 0..1 или -1. */
    public float hitProgress() {
        return hitRecovery > 0 ? 1f - hitRecovery / (float) hitTicks : -1f;
    }

    public void hit(World w, int dmg) {
        takeDamage(dmg);
        aggro = true;
        hitRecovery = hitTicks;
        attackTimer = 0;
        path = new ArrayList<>();
        pathIdx = 0;
        w.floatText(x, y, String.valueOf(dmg), 0xFFFFD040);
        if (dead) {
            hitRecovery = 0;
            w.onMonsterKilled(this);
        }
    }

    private void face(float tx, float ty) {
        float dx = tx - x, dy = ty - y;
        if (dx != 0 || dy != 0) {
            faceDx = dx;
            faceDy = dy;
        }
    }

    @Override
    public void update(World w) {
        if (dead) {
            corpseTimer++;
            return;
        }
        moving = false;
        Player p = w.player;
        if (p == null || p.dead) return;
        if (hitRecovery > 0) {
            hitRecovery--;
            return;
        }
        float d = distTo(p);
        if (!aggro && d <= AGGRO_RADIUS) aggro = true;
        if (!aggro) return;

        if (attackTimer > 0) {
            face(p.x, p.y);
            attackTimer--;
            if (attackTimer == attackTicks - attackHitAt && d <= REACH + 0.7f) resolveHit(w, p);
            return;
        }
        if (d <= REACH) {
            face(p.x, p.y);
            attackTimer = attackTicks;
            return;
        }
        if (--repathTimer <= 0 || pathIdx >= path.size()) {
            repathTimer = 10;
            List<int[]> r = PathFinder.find(w, (int) x, (int) y, (int) p.x, (int) p.y, 1500);
            path = r == null ? new ArrayList<int[]>() : r;
            pathIdx = 0;
        }
        if (pathIdx < path.size()) {
            int[] c = path.get(pathIdx);
            float ox = x, oy = y;
            boolean reached = stepTo(c[0] + 0.5f, c[1] + 0.5f, Math.max(t.walk, 1f) / 25f);
            // не слипаться с соседями: шаг, который сближает с другим монстром, отменяется
            if (w.crowded(this, x, y) && !w.crowded(this, ox, oy)) {
                x = ox;
                y = oy;
                repathTimer = 0;
                return;
            }
            if (reached) pathIdx++;
            if (x != ox || y != oy) {
                moving = true;
                face(x + (x - ox), y + (y - oy));
            }
        }
    }

    private void resolveHit(World w, Player p) {
        PlayerStats s = p.stats;
        double chance = Combat.hitChance(t.attackRating, t.level, s.defense(), s.clvl);
        if (!w.rng.chance(chance)) {
            w.floatText(p.x, p.y, "miss", 0xFFAAAAAA);
            return;
        }
        if (w.rng.chance(Combat.blockChance(s.shieldBlock, s.totalDex(), s.clvl))) {
            w.floatText(p.x, p.y, "block", 0xFF80C0FF);
            return;
        }
        int dmg = w.rng.range(t.minDmg, t.maxDmg);
        p.takeDamage(dmg);
        w.floatText(p.x, p.y, "-" + dmg, 0xFFFF5050);
        if (p.dead) w.say("Вы погибли");
    }
}
