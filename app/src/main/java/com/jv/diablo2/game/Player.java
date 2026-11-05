package com.jv.diablo2.game;

import java.util.ArrayList;
import java.util.List;

/** Герой: движение по пути A*, ближний бой, опыт. */
public final class Player extends Entity {
    public static final int ATTACK_FRAMES = 14;   // кадров анимации удара при 25 FPS
    public static final float REACH = 2.0f;
    /** Длительность удара и тик попадания (от начала замаха); отрисовщик выставляет их по анимации a1 и ключевому кадру. */
    public int attackTicks = ATTACK_FRAMES, attackHitAt = ATTACK_FRAMES / 2;
    public static final float RUN_SPEED = 9f;     // субтайлов в секунду

    public final Hero hero;
    public final PlayerStats stats;
    public Monster target;
    private List<int[]> path = new ArrayList<>();
    private int pathIdx;
    private int attackTimer;
    private int repathTimer;

    /** Куда смотрит герой (вектор в мировых координатах; по умолчанию — на зрителя). */
    public float faceDx = 1f, faceDy = 1f;
    /** Сдвинулся ли герой за последний тик. */
    public boolean moving;
    /** Скорость шага в субтайлах в секунду: ходьба 6, бег 9 (WalkVelocity/RunVelocity из CharStats.txt). */
    public float speed = RUN_SPEED;

    public boolean attacking() {
        return attackTimer > 0;
    }

    /** Прогресс удара 0..1 (или -1, если не бьёт). */
    public float attackProgress() {
        return attackTimer > 0 ? 1f - attackTimer / (float) attackTicks : -1f;
    }

    public Player(CharClass cls, float x, float y) {
        this(new Hero(cls), x, y);
    }

    public Player(Hero hero, float x, float y) {
        this.hero = hero;
        this.stats = hero.stats;
        this.x = x;
        this.y = y;
        this.level = stats.clvl;
        sync();
    }

    private void sync() {
        maxLife = stats.maxLife();
        life = stats.life;
        level = stats.clvl;
    }

    @Override
    public void takeDamage(int dmg) {
        super.takeDamage(dmg);
        stats.life = life;
    }

    public void moveTo(World w, float tx, float ty) {
        target = null;
        attackTimer = 0;
        List<int[]> p = PathFinder.find(w, (int) x, (int) y, (int) tx, (int) ty, 6000);
        path = p == null ? new ArrayList<int[]>() : p;
        pathIdx = 0;
    }

    public void attack(Monster m) {
        target = m;
        path = new ArrayList<>();
        pathIdx = 0;
    }

    public void revive(World w) {
        stats.life = stats.maxLife();
        dead = false;
        x = w.w / 2f;
        y = w.h / 2f;
        target = null;
        path = new ArrayList<>();
        sync();
    }

    @Override
    public void update(World w) {
        float px = x, py = y;
        tick(w);
        float dx = x - px, dy = y - py;
        moving = dx != 0 || dy != 0;
        if (moving) {
            faceDx = dx;
            faceDy = dy;
        } else if (target != null && !target.dead && attackTimer > 0) {
            faceDx = target.x - x;
            faceDy = target.y - y;
        }
    }

    private void tick(World w) {
        sync();
        if (dead) return;
        if (attackTimer > 0) {
            attackTimer--;
            if (attackTimer == attackTicks - attackHitAt && target != null && !target.dead) resolveHit(w);
            return;
        }
        float step = speed / 25f;
        if (target != null) {
            if (target.dead) {
                target = null;
            } else if (distTo(target) <= REACH) {
                attackTimer = attackTicks;
                faceDx = target.x - x;
                faceDy = target.y - y;
                return;
            } else if (--repathTimer <= 0 || pathIdx >= path.size()) {
                repathTimer = 8;
                List<int[]> p = PathFinder.find(w, (int) x, (int) y, (int) target.x, (int) target.y, 3000);
                path = p == null ? new ArrayList<int[]>() : p;
                pathIdx = 0;
            }
        }
        follow(step);
    }

    private void follow(float step) {
        if (pathIdx >= path.size()) return;
        int[] c = path.get(pathIdx);
        if (stepTo(c[0] + 0.5f, c[1] + 0.5f, step)) pathIdx++;
    }

    private void resolveHit(World w) {
        Monster m = target;
        double chance = Combat.hitChance(stats.attackRating(), stats.clvl, m.t.ac, m.t.level);
        if (!w.rng.chance(chance)) {
            w.floatText(m.x, m.y, "miss", 0xFFAAAAAA);
            return;
        }
        int dmg = Combat.rollWeaponDamage(w.rng, stats);
        if (w.rng.chance(stats.crushingBlow)) dmg += m.life / 4;
        m.hit(w, dmg);
    }
}
