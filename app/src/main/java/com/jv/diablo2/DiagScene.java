package com.jv.diablo2;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.jv.diablo2.game.CharClass;
import com.jv.diablo2.game.Hero;

import java.util.ArrayList;
import java.util.List;

/** Диагностика: какие ресурсы найдены и где. Отчёт и список файлов MPQ отправляются одной кнопкой. */
final class DiagScene extends Screen800 {
    private final Paint mono = new Paint(Paint.ANTI_ALIAS_FLAG);
    private volatile List<String> lines = new ArrayList<>();
    private volatile boolean ready;
    private float scroll;

    DiagScene() {
        mono.setTypeface(Typeface.MONOSPACE);
        mono.setTextSize(11);
        lines.add("Проверка ресурсов…");
        buttons.add(new Ui.Btn("SEND REPORT", 33, 555, 200, 35, 16, new Runnable() {
            @Override
            public void run() {
                if (ready) Nav.act.shareReport(Res.fullReport(), new ArrayList<>(Session.fs.files()));
                else Nav.toast("Проверка ещё идёт");
            }
        }));
        buttons.add(new Ui.Btn("SEND SAMPLES (zip)", 250, 555, 330, 35, 16, new Runnable() {
            @Override
            public void run() {
                Nav.act.shareSamples();
            }
        }));
        buttons.add(new Ui.Btn("BACK", 600, 555, 168, 35, 16, new Runnable() {
            @Override
            public void run() {
                Nav.go(new ToolsScene());
            }
        }));
        new Thread(new Runnable() {
            @Override
            public void run() {
                probe();
                List<String> l = new ArrayList<>();
                for (String s : Res.fullReport().split("\n")) l.add(s);
                lines = l;
                ready = true;
            }
        }, "jvd2-probe").start();
    }

    private static void probe() {
        if (Session.fs == null) return;
        FrontEnd.trademark();
        FrontEnd.mainMenuBg();
        FrontEnd.charSelectBg();
        FrontEnd.charCreateBg();
        FrontEnd.loadingBg();
        FrontEnd.buttonWide();
        FrontEnd.buttonMedium();
        for (String p : new String[]{"FireLeft", "FireRight", "BlackLeft", "BlackRight"}) FrontEnd.logo(p);
        FrontEnd.sky();
        FrontEnd.units();
        FrontEnd.fechar();
        FrontEnd.fontPalette();
        for (CharClass c : CharClass.values()) FrontEnd.classIdle(c);
        for (int s : new int[]{42, 30, 24, 16}) Res.font("data\\local\\font\\latin\\font" + s);
        Res.find("music-intro", new String[]{"data\\global\\music\\introedit.wav"}, "music", "intro");
        StringBuilder town = new StringBuilder("ФАЙЛЫ ГОРОДА акта 1:");
        int k = 0;
        for (String f : Session.fs.search("tiles\\act1\\town\\")) {
            if (k++ < 60) town.append("\n   ").append(f);
        }
        Res.note(town.toString());
        // файлы, по которым строятся экраны меню и панель: список нужен, чтобы сверить имена с оригиналом
        String[] groups = {"ui\\frontend\\", "ui\\spells\\", "ui\\loading\\", "monsters\\ch\\cof\\", "monsters\\ps\\cof\\",
                "objects\\", "items\\invhp1", "items\\invmp1"};
        for (String g : groups) {
            StringBuilder sb = new StringBuilder("ФАЙЛЫ " + g + ":");
            int n = 0, total = 0;
            for (String f : Session.fs.search(g)) {
                total++;
                if (n++ < 70) sb.append("\n   ").append(f);
            }
            sb.append("\n   всего ").append(total);
            Res.note(sb.toString());
        }
        try {
            LevelLoader.load(1, new Hero(CharClass.BARBARIAN));
        } catch (Throwable e) {
            Res.note("УРОВЕНЬ 1: ОШИБКА " + e);
        }
    }

    @Override
    protected void render(Canvas c) {
        mono.setColor(0xFFE0D8B8);
        List<String> l = lines;
        float y = 20 - scroll;
        for (String s : l) {
            if (y > -12 && y < 545) c.drawText(s.length() > 118 ? s.substring(0, 118) : s, 8, y, mono);
            y += 13;
        }
    }

    @Override
    protected void drag(float dy) {
        scroll = Math.max(0, Math.min(Math.max(0, lines.size() * 13f - 500), scroll - dy));
    }

    @Override
    public boolean onBack() {
        Nav.go(new ToolsScene());
        return true;
    }
}
