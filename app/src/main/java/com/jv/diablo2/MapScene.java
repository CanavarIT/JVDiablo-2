package com.jv.diablo2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;

import com.jv.diablo2.gfx.Ds1;
import com.jv.diablo2.gfx.Dt1;
import com.jv.diablo2.gfx.Palette;
import com.jv.diablo2.mpq.MpqFileSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Просмотр карт: список DS1 -> пол из DT1 в изометрии. Стены, объекты и NPC — следующий этап.
 * ЭКСПЕРИМЕНТАЛЬНО: форматы DS1/DT1 не сверены с реальными файлами; ошибки выводятся на экран.
 */
public final class MapScene implements Scene {
    private final List<String> names = new ArrayList<>();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean mapMode;
    private float scroll, downX, downY, lastX, lastY;
    private boolean dragging;
    private int rowH = 40;

    private Ds1 ds;
    private Palette pal = Palette.grayscale();
    private final Map<Integer, List<Dt1.Tile>> floorIndex = new HashMap<>();
    private final Map<Dt1.Tile, Bitmap> cache = new HashMap<>();
    private float camX, camY;
    private String info = "Выберите карту (.ds1)";

    public MapScene() {
        MpqFileSystem fs = Session.fs;
        if (fs == null) {
            info = "Данные игры не подключены";
            return;
        }
        for (String f : fs.files()) if (f.endsWith(".ds1")) names.add(f);
        if (names.isEmpty()) info = "В (listfile) нет .ds1";
    }

    @Override
    public synchronized void update() {
    }

    @Override
    public boolean onBack() {
        Nav.go(new ToolsScene());
        return true;
    }

    private void open(int i) {
        try {
            MpqFileSystem fs = Session.fs;
            ds = Ds1.parse(fs.read(names.get(i)));
            floorIndex.clear();
            cache.clear();
            int missing = 0, tiles = 0;
            for (String f : ds.files) {
                try {
                    Dt1 dt = Dt1.parse(fs.read(Ds1.dt1Path(f)));
                    for (Dt1.Tile t : dt.tiles) {
                        if (t.orientation != 0) continue;
                        List<Dt1.Tile> l = floorIndex.get((t.mainIndex << 8) | t.subIndex);
                        if (l == null) {
                            l = new ArrayList<>();
                            floorIndex.put((t.mainIndex << 8) | t.subIndex, l);
                        }
                        l.add(t);
                        tiles++;
                    }
                } catch (Exception e) {
                    missing++;
                }
            }
            try {
                pal = Palette.fromDat(fs.read("data\\global\\palette\\act" + (ds.act + 1) + "\\pal.dat"));
            } catch (Exception e) {
                pal = Palette.grayscale();
            }
            camX = (ds.width / 2f - ds.height / 2f) * 80f;
            camY = (ds.width / 2f + ds.height / 2f) * 40f;
            mapMode = true;
            info = names.get(i) + "  v" + ds.version + "  " + ds.width + "x" + ds.height + "  DT1: " + ds.files.size()
                    + " (не прочитано " + missing + ")  плиток пола: " + tiles;
        } catch (Exception e) {
            info = "Ошибка: " + e.getMessage();
        }
    }

    @Override
    public synchronized void onTouch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) {
            downX = lastX = x;
            downY = lastY = y;
            dragging = false;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (Math.abs(y - downY) > 12 || Math.abs(x - downX) > 12) dragging = true;
            if (dragging) {
                if (mapMode) {
                    camX -= (x - lastX) / 0.6f;
                    camY -= (y - lastY) / 0.6f;
                } else {
                    scroll = Math.max(0, Math.min(Math.max(0, names.size() * rowH - 200), scroll - (y - lastY)));
                }
            }
            lastX = x;
            lastY = y;
        } else if (action == MotionEvent.ACTION_UP && !dragging) {
            if (mapMode) {
                if (x < 220 && y < 100) mapMode = false;
            } else {
                int i = (int) ((y + scroll) / rowH);
                if (i >= 0 && i < names.size()) open(i);
            }
        }
    }

    private Bitmap bitmap(Dt1.Tile t) {
        Bitmap b = cache.get(t);
        if (b == null && t.imgW > 0 && t.imgH > 0) {
            int[] argb = new int[t.imgW * t.imgH];
            for (int i = 0; i < argb.length; i++) argb[i] = pal.argb[t.pixels[i] & 0xFF];
            b = Bitmap.createBitmap(argb, t.imgW, t.imgH, Bitmap.Config.ARGB_8888);
            cache.put(t, b);
        }
        return b;
    }

    @Override
    public synchronized void draw(Canvas c, int w, int h) {
        c.drawColor(0xFF000000);
        rowH = Math.max(36, h / 14);
        p.setStyle(Paint.Style.FILL);
        if (!mapMode) {
            p.setTextSize(rowH * 0.45f);
            p.setTextAlign(Paint.Align.LEFT);
            int first = (int) (scroll / rowH);
            for (int i = first; i < names.size() && i * rowH - scroll < h; i++) {
                p.setColor(0xFFD8C8A0);
                c.drawText(names.get(i), 10, i * rowH - scroll + rowH * 0.68f, p);
            }
            p.setColor(0xFF101010);
            c.drawRect(0, h - 50, w, h, p);
            p.setColor(0xFFB8B098);
            p.setTextSize(h * 0.034f);
            c.drawText(info, 10, h - 14, p);
            return;
        }
        float s = 0.6f;
        c.save();
        c.translate(w / 2f, h / 2f);
        c.scale(s, s);
        c.translate(-camX, -camY);
        float halfW = w / 2f / s + 200, halfH = h / 2f / s + 120;
        int[] floor = ds.floor.length > 0 ? ds.floor[0] : null;
        if (floor != null) {
            for (int y = 0; y < ds.height; y++) {
                for (int x = 0; x < ds.width; x++) {
                    float sx = (x - y) * 80f, sy = (x + y) * 40f;
                    if (Math.abs(sx - camX) > halfW || Math.abs(sy - camY) > halfH) continue;
                    int cell = floor[y * ds.width + x];
                    List<Dt1.Tile> l = floorIndex.get((Ds1.mainIndex(cell) << 8) | Ds1.subIndex(cell));
                    if (l == null || l.isEmpty()) continue;
                    Dt1.Tile t = l.get((((x * 73856093) ^ (y * 19349663)) & 0x7FFFFFFF) % l.size());
                    Bitmap b = bitmap(t);
                    if (b != null) c.drawBitmap(b, sx - 80f + t.imgX, sy + t.imgY, null);
                }
            }
        }
        c.restore();
        p.setColor(0xCC000000);
        c.drawRect(0, 0, 220, 100, p);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(h * 0.05f);
        c.drawText("◀ Список", 14, 62, p);
        p.setTextSize(h * 0.032f);
        p.setColor(0xFFB8B098);
        c.drawText(info, 240, 40, p);
    }
}
