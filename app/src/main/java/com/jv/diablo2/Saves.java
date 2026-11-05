package com.jv.diablo2;

import com.jv.diablo2.game.CharClass;
import com.jv.diablo2.game.Hero;
import com.jv.diablo2.game.SaveGame;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Сохранённые герои: files/save/<имя>.jvd2. */
final class Saves {
    private Saves() {
    }

    static final class Entry {
        final File file;
        final Hero hero;

        Entry(File file, Hero hero) {
            this.file = file;
            this.hero = hero;
        }
    }

    static String safeName(String n) {
        String s = n.trim().replaceAll("[^A-Za-z0-9_\\-]", "");
        return s.isEmpty() ? "Hero" : (s.length() > 15 ? s.substring(0, 15) : s);
    }

    static File fileFor(String name) {
        return new File(Nav.saveDir(), safeName(name).toLowerCase(Locale.ROOT) + ".jvd2");
    }

    static void save(Hero h) {
        try (FileOutputStream out = new FileOutputStream(fileFor(h.name))) {
            SaveGame.write(h, out);
        } catch (Exception e) {
            Nav.toast("Ошибка сохранения: " + e.getMessage());
        }
    }

    static List<Entry> list() {
        List<Entry> r = new ArrayList<>();
        File[] fs = Nav.saveDir().listFiles();
        if (fs == null) return r;
        Arrays.sort(fs, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        for (File f : fs) {
            if (!f.getName().endsWith(".jvd2")) continue;
            try (FileInputStream in = new FileInputStream(f)) {
                r.add(new Entry(f, SaveGame.read(in, Session.items)));
            } catch (Exception ignored) {
            }
        }
        return r;
    }

    static String classTitle(CharClass c) {
        return c.title;
    }
}
