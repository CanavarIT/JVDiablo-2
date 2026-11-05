package com.jv.diablo2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;

import com.jv.diablo2.gfx.Dc6;
import com.jv.diablo2.gfx.Palette;
import com.jv.diablo2.mpq.MpqFileSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Просмотр ресурсов из MPQ: DC6-картинки (тап по превью листает кадры) и WAV-звуки (тап — играть). */
public final class ViewerScene implements Scene {
    private final List<String> names = new ArrayList<>();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Palette palette = Palette.grayscale();
    private float scroll;
    private float downX, downY, lastY;
    private boolean dragging;
    private int selected = -1;
    private Dc6 dc6;
    private int frameIdx;
    private Bitmap bmp;
    private volatile String info = "Выберите файл слева";
    private int rowH = 40, listW = 400, lastWidth = 1280;

    public ViewerScene() {
        MpqFileSystem fs = Session.fs;
        if (fs == null) {
            info = "Данные игры не подключены";
            return;
        }
        for (String f : fs.files()) if (f.endsWith(".dc6") || f.endsWith(".wav")) names.add(f);
        String[] pals = {"data\\global\\palette\\act1\\pal.dat", "data\\global\\palette\\units\\pal.dat"};
        for (String pp : pals) {
            try {
                palette = Palette.fromDat(fs.read(pp));
                break;
            } catch (Exception ignored) {
            }
        }
        if (names.isEmpty()) info = "(listfile) не найден или в нём нет DC6/WAV";
    }

    @Override
    public synchronized void update() {
    }

    @Override
    public boolean onBack() {
        Nav.go(new ToolsScene());
        return true;
    }

    @Override
    public synchronized void onTouch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) {
            downX = x;
            downY = y;
            lastY = y;
            dragging = false;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (Math.abs(y - downY) > 12) dragging = true;
            if (dragging && downX < listW) {
                scroll -= (y - lastY);
                float max = Math.max(0, names.size() * rowH - 200);
                scroll = Math.max(0, Math.min(max, scroll));
            }
            lastY = y;
        } else if (action == MotionEvent.ACTION_UP && !dragging) {
            if (x < listW) {
                int i = (int) ((y + scroll) / rowH);
                if (i >= 0 && i < names.size()) select(i);
            } else if (dc6 != null) {
                int n = dc6.frames.length;
                frameIdx = (x > listW + (lastWidth - listW) / 2f) ? (frameIdx + 1) % n : (frameIdx + n - 1) % n;
                buildBitmap();
            }
        }
    }

    private void select(int i) {
        selected = i;
        final String name = names.get(i);
        if (name.endsWith(".wav")) {
            dc6 = null;
            bmp = null;
            info = "Загрузка " + name + " …";
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        AudioPlayer.play(Session.fs.read(name));
                        info = "▶ " + name;
                    } catch (Exception e) {
                        info = name + ": " + e.getMessage();
                    }
                }
            }, "jvd2-wav").start();
            return;
        }
        AudioPlayer.stop();
        try {
            dc6 = Dc6.parse(Session.fs.read(name));
            frameIdx = 0;
            buildBitmap();
        } catch (Exception e) {
            dc6 = null;
            bmp = null;
            info = "Ошибка: " + e.getMessage();
        }
    }

    private void buildBitmap() {
        Dc6.Frame f = dc6.frames[frameIdx];
        bmp = (f.width <= 0 || f.height <= 0) ? null : Bitmap.createBitmap(f.toArgb(palette), f.width, f.height, Bitmap.Config.ARGB_8888);
        info = names.get(selected) + String.format(Locale.ROOT, "  кадр %d/%d  %dx%d  (напр. %d, кадров/напр. %d)",
                frameIdx + 1, dc6.frames.length, f.width, f.height, dc6.directions, dc6.framesPerDirection);
    }

    @Override
    public synchronized void draw(Canvas c, int w, int h) {
        lastWidth = w;
        rowH = Math.max(36, h / 14);
        listW = Math.max(300, w * 2 / 5);
        c.drawColor(0xFF101010);
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(rowH * 0.45f);
        p.setTextAlign(Paint.Align.LEFT);
        int first = (int) (scroll / rowH);
        for (int i = first; i < names.size() && i * rowH - scroll < h; i++) {
            float y = i * rowH - scroll;
            if (i == selected) {
                p.setColor(0xFF3A3020);
                c.drawRect(0, y, listW, y + rowH, p);
            }
            String n = names.get(i);
            p.setColor(n.endsWith(".wav") ? 0xFF90C0E0 : 0xFFD8C8A0);
            int cut = Math.max(0, n.length() - 34);
            c.drawText((cut > 0 ? "…" : "") + n.substring(cut), 8, y + rowH * 0.68f, p);
        }
        p.setColor(0xFF444444);
        c.drawRect(listW - 2, 0, listW, h, p);
        float ax = listW + 8, ay = 70, aw = w - ax - 8, ah = h - ay - 50;
        p.setColor(0xFF1C1C1C);
        c.drawRect(ax, ay, ax + aw, ay + ah, p);
        if (bmp != null) {
            float k = Math.min(Math.min(aw / bmp.getWidth(), ah / bmp.getHeight()), 4f);
            float dw = bmp.getWidth() * k, dh = bmp.getHeight() * k;
            float dx = ax + (aw - dw) / 2, dy = ay + (ah - dh) / 2;
            p.setFilterBitmap(false);
            c.drawBitmap(bmp, null, new Rect((int) dx, (int) dy, (int) (dx + dw), (int) (dy + dh)), p);
        }
        p.setColor(0xFFB8B098);
        p.setTextSize(h * 0.034f);
        c.drawText(info, ax, h - 16, p);
    }
}
