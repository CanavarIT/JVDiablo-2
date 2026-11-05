package com.jv.diablo2.tools;

import com.jv.diablo2.mpq.MpqFileSystem;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Собирает из MPQ небольшой zip с образцами файлов, нужных для сверки форматов
 * (таблицы, шрифты, спрайты героев и монстров, плитки города, интерфейс, звуки), вместо отправки целых архивов.
 */
public final class SamplePack {
    private SamplePack() {
    }

    private static final class Rule {
        final String name, prefix;
        final String[] suffixes;
        final long cap, fileCap;
        final int maxFiles;

        Rule(String name, String prefix, long cap, int maxFiles, long fileCap, String... suffixes) {
            this.name = name;
            this.prefix = prefix;
            this.cap = cap;
            this.maxFiles = maxFiles;
            this.fileCap = fileCap;
            this.suffixes = suffixes;
        }

        boolean matches(String f) {
            if (!f.startsWith(prefix)) return false;
            if (suffixes.length == 0) return true;
            for (String s : suffixes) if (f.endsWith(s)) return true;
            return false;
        }
    }

    private static final long MB = 1024 * 1024;
    private static final Rule[] RULES = {
            new Rule("таблицы", "data\\global\\excel\\", 10 * MB, 400, 3 * MB, ".txt"),
            new Rule("шрифты", "data\\local\\font\\latin\\", 6 * MB, 100, MB),
            new Rule("строки", "data\\local\\lng\\eng\\", 3 * MB, 20, 3 * MB, ".tbl"),
            new Rule("палитры", "data\\global\\palette\\", MB, 100, 100000, "pal.dat"),
            new Rule("город акта 1", "data\\global\\tiles\\act1\\town\\", 8 * MB, 100, 4 * MB),
            new Rule("варвар", "data\\global\\chars\\ba\\", 5 * MB, 120, 600000, "tnhth.dcc", "nuhth.dcc", "wlhth.dcc", "tnhth.cof", "nuhth.cof", "wlhth.cof"),
            new Rule("волшебница", "data\\global\\chars\\so\\", 5 * MB, 120, 600000, "tnhth.dcc", "nuhth.dcc", "wlhth.dcc", "tnhth.cof", "nuhth.cof", "wlhth.cof"),
            new Rule("падший", "data\\global\\monsters\\fa\\", 4 * MB, 60, MB),
            new Rule("панель интерфейса", "data\\global\\ui\\panel\\", 5 * MB, 60, 1500000, ".dc6"),
            new Rule("значки предметов", "data\\global\\items\\", MB, 25, 40000, ".dc6"),
    };

    /** Пишет zip в out. Возвращает текст сводки (что и сколько попало, какие файлы не читаются). */
    public static String build(MpqFileSystem fs, OutputStream out, long totalCap) throws IOException {
        StringBuilder log = new StringBuilder("Сводка набора образцов\n");
        List<String> errors = new ArrayList<>();
        long total = 0;
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.setLevel(6);
            for (Rule r : RULES) {
                long used = 0;
                int cnt = 0, skipped = 0, failed = 0, matched = 0;
                for (String f : fs.files()) {
                    if (!r.matches(f)) continue;
                    matched++;
                    if (cnt >= r.maxFiles || used >= r.cap || total >= totalCap) continue;
                    byte[] d;
                    try {
                        d = fs.read(f);
                    } catch (Throwable e) {
                        failed++;
                        if (errors.size() < 300) errors.add(f + ": " + e.getMessage());
                        continue;
                    }
                    if (d.length > r.fileCap) {
                        skipped++;
                        continue;
                    }
                    add(z, f.replace('\\', '/'), d);
                    used += d.length;
                    total += d.length;
                    cnt++;
                }
                log.append(String.format(Locale.ROOT, "%-18s подходит %d, добавлено %d (%d КБ), слишком большие %d, не читаются %d%n",
                        r.name, matched, cnt, used / 1024, skipped, failed));
            }
            // проба звуков: какие WAV читаются и какие нет (сжатие Huffman/ADPCM)
            int ok = 0, bad = 0, included = 0;
            long wavBytes = 0;
            List<String> wavLog = new ArrayList<>();
            for (String f : fs.files()) {
                if (!f.endsWith(".wav") || ok + bad >= 80) continue;
                try {
                    byte[] d = fs.read(f);
                    ok++;
                    wavLog.add("OK   " + f + " " + d.length);
                    if (included < 8 && d.length <= 150000 && total + wavBytes < totalCap) {
                        add(z, f.replace('\\', '/'), d);
                        included++;
                        wavBytes += d.length;
                    }
                } catch (Throwable e) {
                    bad++;
                    wavLog.add("FAIL " + f + ": " + e.getMessage());
                }
            }
            log.append(String.format(Locale.ROOT, "%-18s проверено %d: читается %d, не читается %d, добавлено %d%n", "звуки (WAV)", ok + bad, ok, bad, included));
            log.append("\nОшибки чтения файлов:\n");
            for (String e : errors) log.append("  ").append(e).append('\n');
            log.append("\nПроба WAV:\n");
            for (String w : wavLog) log.append("  ").append(w).append('\n');
            add(z, "pack_summary.txt", log.toString().getBytes("UTF-8"));
        }
        return log.toString();
    }

    private static void add(ZipOutputStream z, String name, byte[] data) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(data);
        z.closeEntry();
    }
}
