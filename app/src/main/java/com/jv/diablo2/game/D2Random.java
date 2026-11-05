package com.jv.diablo2.game;

/**
 * Генератор в духе игрового: 64-битное состояние (lo, hi), множитель 0x6AC690C5, hi стартует с 666.
 * Детерминирован по seed — нужно для воспроизводимых дропов и подземелий.
 */
public final class D2Random {
    private int lo;
    private int hi = 666;

    public D2Random(long seed) {
        this.lo = (int) seed;
    }

    public int nextRaw() {
        long v = (lo & 0xFFFFFFFFL) * 0x6AC690C5L + (hi & 0xFFFFFFFFL);
        lo = (int) v;
        hi = (int) (v >>> 32);
        return lo;
    }

    /** Число в диапазоне [0, n). */
    public int nextInt(int n) {
        if (n <= 0) return 0;
        return (int) ((nextRaw() & 0xFFFFFFFFL) % n);
    }

    /** Число в диапазоне [min, max] включительно. */
    public int range(int min, int max) {
        return max <= min ? min : min + nextInt(max - min + 1);
    }

    /** true с вероятностью percent (0..100). */
    public boolean chance(double percent) {
        if (percent <= 0) return false;
        if (percent >= 100) return true;
        return nextInt(100000) < percent * 1000.0;
    }
}
