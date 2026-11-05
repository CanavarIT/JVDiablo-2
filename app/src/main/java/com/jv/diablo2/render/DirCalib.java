package com.jv.diablo2.render;

import com.jv.diablo2.gfx.Dcc;

import java.util.Locale;

/**
 * Калибровка 16 файловых направлений DCC героя по самим спрайтам.
 * <p>
 * Раньше соответствие «куда идёт герой -> какой файл рисовать» было зашито константами, и стоило ошибиться
 * в стороне вращения или в смещении круга, как герой начинал идти «задом наперёд». Теперь соответствие
 * измеряется по данным игры:
 * <ol>
 *   <li>для каждого из 16 файлов берётся центр масс правой руки (RA) и левой руки (LA) по всем кадрам;</li>
 *   <li>вектор «левая рука -> правая рука» на экране задаёт боковую ось тела; ось земли сжата 2:1 (изометрия),
 *       поэтому взгляд = (2·ly, −lx), угол θ = atan2(2·ly, −lx): 0 — на зрителя, +90° — к правому краю экрана;</li>
 *   <li>известный круговой порядок файлов (соседние — соседние повороты) подгоняется под измеренные углы
 *       по обеим сторонам вращения и всем 16 смещениям; выбирается лучший по взвешенной сумме косинусов.</li>
 * </ol>
 * Если данных нет или подгонка ненадёжна, берётся прежний порядок ({@link #DEFAULT}).
 */
public final class DirCalib {
    /** Порядок 16 файловых направлений DCC по кругу: соседние элементы — соседние повороты на 22.5°. */
    public static final int[] RING = {0, 9, 5, 10, 1, 11, 6, 12, 2, 13, 7, 14, 3, 15, 4, 8};

    /** Прежняя раскладка (на зрителя — позиция 13 круга, +1 — к правому краю экрана). Запасной вариант. */
    public static final DirCalib DEFAULT = new DirCalib(13, 1, 0, false, "по умолчанию");

    /** fileFor[k] — файл для взгляда k·22.5° от «на зрителя» (k = 0) в сторону правого края экрана. */
    public final int[] fileFor = new int[16];
    /** true, если раскладка измерена по спрайтам, а не взята по умолчанию. */
    public final boolean fitted;
    /** Средний косинус отклонения измеренных углов от модели: 1 — идеально, 0 — случайно. */
    public final double score;
    /** Короткое описание для журнала. */
    public final String info;
    /** Позиция «на зрителя» на круге и сторона вращения (+1 — к правому краю экрана). */
    public final int offset, sense;

    private DirCalib(int offset, int sense, double score, boolean fitted, String info) {
        this.offset = offset;
        this.sense = sense;
        this.score = score;
        this.fitted = fitted;
        this.info = info;
        for (int k = 0; k < 16; k++) fileFor[k] = RING[Math.floorMod(offset + sense * k, 16)];
    }

    /** Файл DCC для вектора взгляда на экране (sx вправо, sy вниз). */
    public int file(float sx, float sy) {
        double theta = Math.atan2(sx, sy);
        int k = (int) Math.round(theta / (Math.PI * 2) * 16);
        return fileFor[Math.floorMod(k, 16)];
    }

    /** Угол взгляда (радианы, 0 — на зрителя, + к правому краю экрана) для файла; NaN, если файла нет в раскладке. */
    public double headingOf(int file) {
        for (int k = 0; k < 16; k++) if (fileFor[k] == file) return k * Math.PI / 8;
        return Double.NaN;
    }

    // ------------------------------------------------------------------ измерение

    /**
     * Измеряет раскладку по слоям правой и левой руки (или правого и левого плеча) героя.
     * Возвращает {@link #DEFAULT}, если у слоёв не 16 направлений или данные не читаются.
     */
    public static DirCalib measure(Dcc right, Dcc left) {
        if (right == null || left == null || right.directions < 16 || left.directions < 16) return DEFAULT;
        double[] lx = new double[16], ly = new double[16];
        try {
            for (int d = 0; d < 16; d++) {
                double[] r = centroid(right.direction(d));
                double[] l = centroid(left.direction(d));
                if (r == null || l == null) {
                    lx[d] = ly[d] = Double.NaN;
                } else {
                    lx[d] = r[0] - l[0];
                    ly[d] = r[1] - l[1];
                }
            }
        } catch (Exception e) {
            return DEFAULT;
        }
        return fit(lx, ly);
    }

    /** Центр масс непрозрачных пикселей всех кадров направления в координатах точки привязки; null — пусто. */
    static double[] centroid(Dcc.Frame[] frames) {
        double sx = 0, sy = 0;
        long n = 0;
        for (Dcc.Frame f : frames) {
            if (f == null || f.pixels == null) continue;
            for (int y = 0; y < f.height; y++) {
                int row = y * f.width;
                for (int x = 0; x < f.width; x++) {
                    if (f.pixels[row + x] != 0) {
                        sx += f.offsetX + x;
                        sy += f.offsetY + y;
                        n++;
                    }
                }
            }
        }
        return n == 0 ? null : new double[]{sx / n, sy / n};
    }

    /**
     * Подгонка круга по боковым осям тела: lx[d], ly[d] — вектор «левая рука -> правая рука» на экране для файла d.
     * Открыта для тестов на синтетических данных.
     */
    public static DirCalib fit(double[] lx, double[] ly) {
        double[] theta = new double[16], w = new double[16];
        int valid = 0;
        for (int d = 0; d < 16; d++) {
            double x = lx[d], y = ly[d];
            if (Double.isNaN(x) || Double.isNaN(y)) continue;
            double len = Math.hypot(x, 2 * y);
            if (len < 1.5) continue;                      // боковая ось не читается — файл не участвует
            theta[d] = Math.atan2(2 * y, -x);
            w[d] = Math.min(len, 40.0);
            valid++;
        }
        if (valid < 10) return DEFAULT;

        double[] bestBySense = {-2, -2};          // [0] — к правому краю (+1), [1] — к левому (-1)
        int[] bestOffBySense = {13, 13};
        double totalW = 0;
        for (int d = 0; d < 16; d++) totalW += w[d];
        for (int si = 0; si < 2; si++) {
            int s = si == 0 ? 1 : -1;
            for (int off = 0; off < 16; off++) {
                double sum = 0;
                for (int k = 0; k < 16; k++) {
                    int file = RING[Math.floorMod(off + s * k, 16)];
                    if (w[file] == 0) continue;
                    sum += w[file] * Math.cos(theta[file] - k * Math.PI / 8);
                }
                double sc = sum / totalW;
                if (sc > bestBySense[si]) {
                    bestBySense[si] = sc;
                    bestOffBySense[si] = off;
                }
            }
        }
        int win = bestBySense[0] >= bestBySense[1] ? 0 : 1;
        double best = bestBySense[win], other = bestBySense[1 - win];
        int bestOff = bestOffBySense[win], bestSense = win == 0 ? 1 : -1;
        // подгонка должна быть уверенной: хороший средний косинус и заметный отрыв от зеркального варианта
        if (best < 0.5 || best - other < 0.25) {
            return new DirCalib(13, 1, best, false, String.format(Locale.ROOT, "подгонка ненадёжна (%.2f), по умолчанию", best));
        }
        String info = String.format(Locale.ROOT, "измерено: на зрителя = позиция %d круга, вращение %s, качество %.2f",
                bestOff, bestSense > 0 ? "к правому краю" : "к левому краю", best);
        return new DirCalib(bestOff, bestSense, best, true, info);
    }
}
