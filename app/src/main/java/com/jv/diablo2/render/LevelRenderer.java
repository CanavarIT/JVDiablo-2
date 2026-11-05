package com.jv.diablo2.render;

import com.jv.diablo2.game.TileMap;
import com.jv.diablo2.gfx.Dt1;

import java.util.List;

/**
 * Отрисовка уровня как в оригинале: полы, тени, затем стены/колонны/деревья вперемешку с юнитами в порядке
 * глубины (x + y в субтайлах), в конце — крыши. Тайл 5x5 субтайлов = ромб 160x80; субтайл = 32x16.
 * Стены привязаны к нижней вершине ромба (+80), полы — к верхней.
 */
public final class LevelRenderer {
    /** Всё, что рисуется между стенами: герой, NPC, монстры, объекты, предметы на земле. */
    public interface Actor {
        /** Мировые координаты юнита в субтайлах; глубина = x + y. */
        float x();

        float y();

        /** sx, sy — экранная точка «под ногами» юнита. */
        void draw(Fb fb, int sx, int sy);
    }

    public static final int CENTER_X = 320, CENTER_Y = 230;

    private final TileMap map;
    private final int[] pal;
    /** Дополнительный сдвиг крыш (по умолчанию 0: крыша поднимается на roofHeight плитки — проверено на шатрах города). */
    public int roofShift = 0;

    public LevelRenderer(TileMap map, int[] pal) {
        this.map = map;
        this.pal = pal;
    }

    public static float screenX(float wx, float wy, float camX, float camY) {
        return CENTER_X + (wx - wy) * 16f - (camX - camY) * 16f;
    }

    public static float screenY(float wx, float wy, float camX, float camY) {
        return CENTER_Y + (wx + wy) * 8f - (camX + camY) * 8f;
    }

    /** Мировые субтайловые координаты точки экрана (для кликов). */
    public static float worldX(float sx, float sy, float camX, float camY) {
        float dx = (sx - CENTER_X) / 16f + (camX - camY), dy = (sy - CENTER_Y) / 8f + (camX + camY);
        return (dx + dy) / 2f;
    }

    public static float worldY(float sx, float sy, float camX, float camY) {
        float dx = (sx - CENTER_X) / 16f + (camX - camY), dy = (sy - CENTER_Y) / 8f + (camX + camY);
        return (dy - dx) / 2f;
    }

    private void drawTile(Fb fb, Dt1.Tile t, int sx, int sy, int mode) {
        if (t.imgW <= 0 || t.imgH <= 0 || t.pixels == null) return;
        fb.blit(t.pixels, t.imgW, t.imgH, sx, sy, pal, null, mode, 255);
    }

    /** Рисует окно 640x480 с центром камеры в мировой точке (camX, camY) (субтайлы). */
    public void render(Fb fb, float camX, float camY, List<Actor> actors) {
        // видимые плитки: обратное преобразование углов экрана с запасом на высокие стены
        float[] xs = {-200, fb.w + 200, -200, fb.w + 200}, ys = {-380, -380, fb.h + 120, fb.h + 120};
        float minTx = 1e9f, maxTx = -1e9f, minTy = 1e9f, maxTy = -1e9f;
        for (int i = 0; i < 4; i++) {
            float wx = worldX(xs[i], ys[i], camX, camY) / 5f, wy = worldY(xs[i], ys[i], camX, camY) / 5f;
            minTx = Math.min(minTx, wx);
            maxTx = Math.max(maxTx, wx);
            minTy = Math.min(minTy, wy);
            maxTy = Math.max(maxTy, wy);
        }
        int x0 = Math.max(0, (int) Math.floor(minTx) - 1), x1 = Math.min(map.w - 1, (int) Math.ceil(maxTx) + 1);
        int y0 = Math.max(0, (int) Math.floor(minTy) - 1), y1 = Math.min(map.h - 1, (int) Math.ceil(maxTy) + 1);

        float ox = CENTER_X - (camX - camY) * 16f, oy = CENTER_Y - (camX + camY) * 8f;   // экранная точка мирового (0,0)

        for (int layer = 0; layer < map.floors.length; layer++) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    Dt1.Tile t = map.floors[layer][y * map.w + x];
                    if (t == null) continue;
                    drawTile(fb, t, Math.round(ox + (x - y) * 80f - 80f + t.imgX), Math.round(oy + (x + y) * 40f + t.imgY), Fb.NORMAL);
                }
            }
        }
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                Dt1.Tile t = map.shadows[y * map.w + x];
                if (t == null) continue;
                drawTile(fb, t, Math.round(ox + (x - y) * 80f - 80f + t.imgX), Math.round(oy + (x + y) * 40f + 80f + t.imgY), Fb.SHADOW);
            }
        }

        // стены и юниты в порядке глубины
        java.util.List<Actor> sorted = new java.util.ArrayList<>();
        if (actors != null) sorted.addAll(actors);
        java.util.Collections.sort(sorted, new java.util.Comparator<Actor>() {
            @Override
            public int compare(Actor a, Actor b) {
                return Float.compare(a.x() + a.y(), b.x() + b.y());
            }
        });
        actors = sorted;
        int ai = 0, na = actors.size();
        for (TileMap.Piece p : map.pieces) {
            while (ai < na && actors.get(ai).x() + actors.get(ai).y() < p.depth) drawActor(fb, actors.get(ai++), camX, camY);
            if (p.tx < x0 || p.tx > x1 || p.ty < y0 || p.ty > y1) continue;
            Dt1.Tile t = p.tile;
            int sx = Math.round(ox + (p.tx - p.ty) * 80f - 80f + t.imgX), sy = Math.round(oy + (p.tx + p.ty) * 40f + 80f + t.imgY);
            if (sx > fb.w || sx + t.imgW < 0 || sy > fb.h || sy + t.imgH < 0) continue;
            drawTile(fb, t, sx, sy, Fb.NORMAL);
        }
        while (ai < na) drawActor(fb, actors.get(ai++), camX, camY);

        for (TileMap.Piece p : map.roofs) {
            if (p.tx < x0 || p.tx > x1 || p.ty < y0 || p.ty > y1) continue;
            Dt1.Tile t = p.tile;
            int sx = Math.round(ox + (p.tx - p.ty) * 80f - 80f + t.imgX);
            int sy = Math.round(oy + (p.tx + p.ty) * 40f + t.imgY - t.roofHeight + roofShift);
            drawTile(fb, t, sx, sy, Fb.NORMAL);
        }
    }

    private void drawActor(Fb fb, Actor a, float camX, float camY) {
        a.draw(fb, Math.round(screenX(a.x(), a.y(), camX, camY)), Math.round(screenY(a.x(), a.y(), camX, camY)));
    }
}
