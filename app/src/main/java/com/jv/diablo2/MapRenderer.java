package com.jv.diablo2;

import android.graphics.Canvas;

/** Рисует фон карты в изометрических экранных координатах (до камеры); границы — видимая область. */
interface MapRenderer {
    void drawFloor(Canvas c, float l, float t, float r, float b);

    /** inFront=false: стены позади героя (depth <= depthSplit), true: стены перед героем. */
    void drawWalls(Canvas c, float l, float t, float r, float b, float depthSplit, boolean inFront);
}
