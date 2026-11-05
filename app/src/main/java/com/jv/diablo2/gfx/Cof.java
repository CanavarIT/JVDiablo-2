package com.jv.diablo2.gfx;

import java.io.IOException;

/**
 * COF — описание анимации составного объекта (персонаж/монстр): какие слои (HD, TR, LG, RA, LA, RH, LH, SH, S1..S8),
 * сколько кадров и направлений, и порядок отрисовки слоёв для каждого кадра и направления.
 */
public final class Cof {
    public static final String[] COMP = {"HD", "TR", "LG", "RA", "LA", "RH", "LH", "SH", "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8"};

    public static final class Layer {
        public int component;      // индекс в COMP
        public boolean shadow, selectable;
        public int overrideTrans, newTrans;
        public String weaponClass; // HTH, 1HS, BOW ...
    }

    public final int layerCount, framesPerDir, directions, version;
    public final int xMin, xMax, yMin, yMax;
    public final Layer[] layers;
    public final byte[] keyframes;   // по кадрам: 0 нет, 1 атака, 2 снаряд, 3 звук, 4 навык
    /** priority[dir][frame][i] = индекс компонента (0..15), i-й в порядке отрисовки (снизу вверх). */
    public final byte[][][] priority;

    private Cof(byte[] d) throws IOException {
        if (d.length < 28) throw new IOException("COF: слишком короткий файл");
        layerCount = d[0] & 0xFF;
        framesPerDir = d[1] & 0xFF;
        directions = d[2] & 0xFF;
        version = d[3] & 0xFF;
        xMin = le32(d, 8); xMax = le32(d, 12); yMin = le32(d, 16); yMax = le32(d, 20);
        int p = 28;
        int need = p + layerCount * 9 + framesPerDir + directions * framesPerDir * layerCount;
        if (layerCount == 0 || framesPerDir == 0 || directions == 0 || need > d.length)
            throw new IOException("COF: битый заголовок (нужно " + need + ", есть " + d.length + ")");
        layers = new Layer[layerCount];
        for (int i = 0; i < layerCount; i++, p += 9) {
            Layer l = new Layer();
            l.component = d[p] & 0xFF;
            l.shadow = d[p + 1] != 0;
            l.selectable = d[p + 2] != 0;
            l.overrideTrans = d[p + 3] & 0xFF;
            l.newTrans = d[p + 4] & 0xFF;
            l.weaponClass = new String(d, p + 5, 3, java.nio.charset.StandardCharsets.US_ASCII);
            layers[i] = l;
        }
        keyframes = new byte[framesPerDir];
        for (int f = 0; f < framesPerDir; f++) keyframes[f] = d[p++];
        priority = new byte[directions][framesPerDir][layerCount];
        for (int dir = 0; dir < directions; dir++)
            for (int f = 0; f < framesPerDir; f++)
                for (int i = 0; i < layerCount; i++) priority[dir][f][i] = d[p++];
    }

    public static Cof parse(byte[] d) throws IOException { return new Cof(d); }

    public Layer layerOf(int component) {
        for (Layer l : layers) if (l.component == component) return l;
        return null;
    }

    private static int le32(byte[] d, int p) {
        return (d[p] & 0xFF) | (d[p + 1] & 0xFF) << 8 | (d[p + 2] & 0xFF) << 16 | (d[p + 3] & 0xFF) << 24;
    }
}
