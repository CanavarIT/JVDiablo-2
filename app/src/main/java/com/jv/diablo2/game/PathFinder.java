package com.jv.diablo2.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/** A* по сетке субтайлов, 8 направлений, без срезания углов. При неудаче возвращает путь к ближайшей достижимой точке. */
public final class PathFinder {
    private PathFinder() {
    }

    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DY = {0, 0, 1, -1, 1, -1, 1, -1};

    private static float h(int x, int y, int tx, int ty) {
        int dx = Math.abs(x - tx), dy = Math.abs(y - ty);
        return (dx + dy) - 0.5858f * Math.min(dx, dy);
    }

    /** Путь от старта (не включая) до цели; пустой список — идти некуда; null — цель недостижима совсем. */
    public static List<int[]> find(World w, int sx, int sy, int tx, int ty, int maxNodes) {
        if (!w.walkable(tx, ty)) {
            int[] n = w.nearestWalkable(tx, ty, 4);
            if (n == null) return null;
            tx = n[0];
            ty = n[1];
        }
        int W = w.w, N = w.w * w.h;
        float[] g = new float[N];
        Arrays.fill(g, Float.MAX_VALUE);
        int[] parent = new int[N];
        Arrays.fill(parent, -1);
        boolean[] closed = new boolean[N];
        PriorityQueue<float[]> open = new PriorityQueue<>((a, b) -> Float.compare(a[0], b[0]));
        int start = sy * W + sx, goal = ty * W + tx;
        g[start] = 0;
        open.add(new float[]{h(sx, sy, tx, ty), start});
        int best = start;
        float bestH = h(sx, sy, tx, ty);
        int expanded = 0;
        while (!open.isEmpty() && expanded < maxNodes) {
            int ci = (int) open.poll()[1];
            if (closed[ci]) continue;
            closed[ci] = true;
            expanded++;
            if (ci == goal) {
                best = goal;
                break;
            }
            int cx = ci % W, cy = ci / W;
            float hh = h(cx, cy, tx, ty);
            if (hh < bestH) {
                bestH = hh;
                best = ci;
            }
            for (int d = 0; d < 8; d++) {
                int nx = cx + DX[d], ny = cy + DY[d];
                if (!w.walkable(nx, ny)) continue;
                boolean diag = d >= 4;
                if (diag && (!w.walkable(cx + DX[d], cy) || !w.walkable(cx, cy + DY[d]))) continue;
                int ni = ny * W + nx;
                if (closed[ni]) continue;
                float ng = g[ci] + (diag ? 1.4142f : 1f);
                if (ng < g[ni]) {
                    g[ni] = ng;
                    parent[ni] = ci;
                    open.add(new float[]{ng + h(nx, ny, tx, ty), ni});
                }
            }
        }
        List<int[]> path = new ArrayList<>();
        for (int c = best; c != start && c >= 0; c = parent[c]) path.add(new int[]{c % W, c / W});
        Collections.reverse(path);
        return path;
    }
}
