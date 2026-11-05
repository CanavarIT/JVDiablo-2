package com.jv.diablo2.game;

import com.jv.diablo2.gfx.Ds1;
import com.jv.diablo2.gfx.Dt1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Карта из DS1 + набор DT1: плитки пола (до двух слоёв), тени, стены/объекты и крыши, плюс сетка проходимости
 * по субтайлам (5x5 на плитку). Флаги субтайлов в DT1 хранятся строками снизу вверх: индекс = (4 - y) * 5 + x
 * (проверено на заборе города: левая стена занимает столбец x=0, правая — ряд y=0).
 */
public final class TileMap {
    /** Стена, колонна, дерево или крыша: рисуются в порядке глубины вместе с юнитами. */
    public static final class Piece {
        public final int tx, ty, orient;
        public final Dt1.Tile tile;
        /** Глубина в субтайлах (x + y) центра «следа» плитки; юнит с большей глубиной рисуется поверх. */
        public final float depth;

        Piece(int tx, int ty, int orient, Dt1.Tile tile, float depth) {
            this.tx = tx;
            this.ty = ty;
            this.orient = orient;
            this.tile = tile;
            this.depth = depth;
        }
    }

    public final int w, h;
    public final Dt1.Tile[][] floors;
    public final Dt1.Tile[] shadows;
    public final List<Piece> pieces = new ArrayList<>();
    public final List<Piece> roofs = new ArrayList<>();
    public final boolean[] blocked;     // (w*5) x (h*5)
    public final boolean[] hasFloor;    // по плиткам
    public int floorCells, floorHits, wallHits, wallMiss, roofHits;

    private TileMap(int w, int h, int floorLayers) {
        this.w = w;
        this.h = h;
        this.floors = new Dt1.Tile[floorLayers][w * h];
        this.shadows = new Dt1.Tile[w * h];
        this.blocked = new boolean[w * 5 * h * 5];
        this.hasFloor = new boolean[w * h];
    }

    private static int key(int main, int sub, int orient) {
        return (orient << 16) | (main << 8) | sub;
    }

    private static Dt1.Tile pick(List<Dt1.Tile> l, int x, int y) {
        if (l == null || l.isEmpty()) return null;
        if (l.size() == 1) return l.get(0);
        int total = 0;
        for (Dt1.Tile t : l) total += Math.max(1, t.rarity);
        int r = (((x * 73856093) ^ (y * 19349663)) & 0x7FFFFFFF) % total;
        for (Dt1.Tile t : l) {
            r -= Math.max(1, t.rarity);
            if (r < 0) return t;
        }
        return l.get(0);
    }

    /** Флаг субтайла (sx, sy) плитки: sy растёт к левому нижнему краю ромба. */
    public static int flag(Dt1.Tile t, int sx, int sy) {
        return t.subtileFlags[(4 - sy) * 5 + sx] & 0xFF;
    }

    private void mark(int tx, int ty, Dt1.Tile t) {
        int bw = w * 5;
        for (int sy = 0; sy < 5; sy++) {
            for (int sx = 0; sx < 5; sx++) {
                if ((flag(t, sx, sy) & 0x09) != 0) blocked[(ty * 5 + sy) * bw + tx * 5 + sx] = true;
            }
        }
    }

    /** Глубина «следа» плитки: среднее (x + y) заблокированных субтайлов, иначе — по виду ориентации. */
    private static float footprintDepth(Dt1.Tile t, int tx, int ty, int orient) {
        float sum = 0;
        int n = 0;
        for (int sy = 0; sy < 5; sy++)
            for (int sx = 0; sx < 5; sx++)
                if ((flag(t, sx, sy) & 0x09) != 0) {
                    sum += sx + sy + 1f;
                    n++;
                }
        float base = 5f * (tx + ty);
        if (n > 0) return base + sum / n;
        return base + (orient >= 12 && orient <= 14 ? 5f : 2.5f);
    }

    public static TileMap build(Ds1 ds, List<Dt1> dt1s) {
        Map<Integer, List<Dt1.Tile>> idx = new HashMap<>();
        for (Dt1 d : dt1s) {
            for (Dt1.Tile t : d.tiles) {
                List<Dt1.Tile> l = idx.get(key(t.mainIndex, t.subIndex, t.orientation));
                if (l == null) {
                    l = new ArrayList<>();
                    idx.put(key(t.mainIndex, t.subIndex, t.orientation), l);
                }
                l.add(t);
            }
        }
        TileMap m = new TileMap(ds.width, ds.height, Math.max(1, ds.floor.length));
        for (int layer = 0; layer < ds.floor.length; layer++) {
            for (int y = 0; y < ds.height; y++) {
                for (int x = 0; x < ds.width; x++) {
                    int cell = ds.floor[layer][y * ds.width + x];
                    if (Ds1.hidden(cell)) continue;
                    Dt1.Tile t = pick(idx.get(key(Ds1.mainIndex(cell), Ds1.subIndex(cell), 0)), x, y);
                    if (t == null) continue;
                    m.floors[layer][y * ds.width + x] = t;
                    m.hasFloor[y * ds.width + x] = true;
                    m.mark(x, y, t);
                }
            }
        }
        if (ds.shadow != null) {
            for (int y = 0; y < ds.height; y++) {
                for (int x = 0; x < ds.width; x++) {
                    int cell = ds.shadow[y * ds.width + x];
                    if (cell == 0 || Ds1.hidden(cell)) continue;
                    m.shadows[y * ds.width + x] = pick(idx.get(key(Ds1.mainIndex(cell), Ds1.subIndex(cell), 13)), x, y);
                }
            }
        }
        for (int layer = 0; layer < ds.wall.length; layer++) {
            for (int y = 0; y < ds.height; y++) {
                for (int x = 0; x < ds.width; x++) {
                    int cell = ds.wall[layer][y * ds.width + x];
                    int orient = ds.orient[layer][y * ds.width + x] & 0xFF;
                    if (orient == 0 || Ds1.hidden(cell)) continue;
                    if (orient == 10 || orient == 11) continue;           // служебные плитки (выходы), не рисуются
                    int main = Ds1.mainIndex(cell), sub = Ds1.subIndex(cell);
                    Dt1.Tile t = pick(idx.get(key(main, sub, orient)), x, y);
                    if (t == null) {
                        m.wallMiss++;
                        continue;
                    }
                    m.add(x, y, orient, t);
                    if (orient == 3) {                                     // северный угол: две плитки (3 и 4)
                        Dt1.Tile t4 = pick(idx.get(key(main, sub, 4)), x, y);
                        if (t4 != null) m.add(x, y, 4, t4);
                    }
                }
            }
        }
        Collections.sort(m.pieces, new Comparator<Piece>() {
            @Override
            public int compare(Piece a, Piece b) {
                int c = Float.compare(a.depth, b.depth);
                return c != 0 ? c : Integer.compare(a.tx, b.tx);
            }
        });
        m.floorCells = ds.width * ds.height;
        for (boolean f : m.hasFloor) if (f) m.floorHits++;
        // пустоты (нет плитки пола) непроходимы, но только если пол вообще найден — иначе карта останется открытой
        if (m.floorHits * 2 > m.floorCells) {
            for (int y = 0; y < ds.height; y++)
                for (int x = 0; x < ds.width; x++)
                    if (!m.hasFloor[y * ds.width + x])
                        for (int sy = 0; sy < 5; sy++)
                            for (int sx = 0; sx < 5; sx++) m.blocked[(y * 5 + sy) * ds.width * 5 + x * 5 + sx] = true;
        }
        return m;
    }

    private void add(int x, int y, int orient, Dt1.Tile t) {
        if (orient == 15) {
            roofs.add(new Piece(x, y, orient, t, 1e9f));
            roofHits++;
            return;
        }
        pieces.add(new Piece(x, y, orient, t, footprintDepth(t, x, y, orient)));
        mark(x, y, t);
        wallHits++;
    }
}
