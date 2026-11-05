package com.jv.diablo2.mpq;

import com.jv.diablo2.io.RandomAccessSource;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Виртуальная файловая система поверх всех MPQ игры.
 * Приоритет как в оригинале: патч > дополнение (LoD) > базовые архивы.
 */
public final class MpqFileSystem implements Closeable {
    public static final String[] PRIORITY = {
            "patch_d2.mpq", "d2exp.mpq", "d2xmusic.mpq", "d2xvideo.mpq", "d2xtalk.mpq",
            "d2data.mpq", "d2char.mpq", "d2sfx.mpq", "d2music.mpq", "d2speech.mpq", "d2video.mpq"};

    private final List<MpqArchive> archives = new ArrayList<>();
    private Set<String> fileCache;
    /** Папка с «россыпью» файлов (как data\\ рядом с игрой): имеет приоритет над архивами. Используется и для тестов. */
    private File looseRoot;

    public synchronized void mountLoose(File root) {
        looseRoot = root;
        fileCache = null;
    }

    private File loose(String path) {
        if (looseRoot == null) return null;
        File f = new File(looseRoot, path.toLowerCase(Locale.ROOT).replace('\\', '/'));
        return f.isFile() ? f : null;
    }

    private static int priority(String n) {
        for (int i = 0; i < PRIORITY.length; i++) if (PRIORITY[i].equals(n)) return i;
        return PRIORITY.length;
    }

    public synchronized void mount(String fileName, RandomAccessSource src) throws IOException {
        String n = fileName.toLowerCase(Locale.ROOT);
        archives.add(new MpqArchive(n, src));
        archives.sort((a, b) -> Integer.compare(priority(a.getName()), priority(b.getName())));
        fileCache = null;
    }

    public synchronized int archiveCount() {
        return archives.size() + (looseRoot != null ? 1 : 0);
    }

    public synchronized List<String> archiveNames() {
        List<String> r = new ArrayList<>();
        for (MpqArchive a : archives) r.add(a.getName());
        return r;
    }

    public synchronized boolean exists(String path) {
        if (loose(path) != null) return true;
        for (MpqArchive a : archives) if (a.contains(path)) return true;
        return false;
    }

    public byte[] read(String path) throws IOException {
        List<MpqArchive> snapshot;
        File lf;
        synchronized (this) {
            snapshot = new ArrayList<>(archives);
            lf = loose(path);
        }
        if (lf != null) return Files.readAllBytes(lf.toPath());
        for (MpqArchive a : snapshot) {
            if (a.contains(path)) return a.read(path);
        }
        throw new FileNotFoundException(path);
    }

    /** Объединённый список файлов из (listfile) всех архивов, в нижнем регистре. */
    public synchronized Set<String> files() {
        if (fileCache == null) {
            Set<String> all = new TreeSet<>();
            if (looseRoot != null) addLoose(all, looseRoot, "");
            for (MpqArchive a : archives) {
                for (String f : a.listFile()) all.add(f.toLowerCase(Locale.ROOT).replace('/', '\\'));
            }
            fileCache = all;
        }
        return fileCache;
    }

    private static void addLoose(Set<String> out, File dir, String prefix) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            String n = prefix + f.getName().toLowerCase(Locale.ROOT);
            if (f.isDirectory()) addLoose(out, f, n + "\\");
            else out.add(n);
        }
    }

    /** Файлы из (listfile), в пути которых встречаются все перечисленные подстроки (без учёта регистра). */
    public List<String> search(String... parts) {
        List<String> r = new ArrayList<>();
        outer:
        for (String f : files()) {
            for (String p : parts) if (!f.contains(p.toLowerCase(Locale.ROOT))) continue outer;
            r.add(f);
        }
        return r;
    }

    /** Первый существующий путь из списка кандидатов или null. */
    public String firstExisting(String... paths) {
        for (String p : paths) if (p != null && exists(p)) return p;
        return null;
    }

    @Override
    public synchronized void close() throws IOException {
        for (MpqArchive a : archives) {
            try {
                a.close();
            } catch (IOException ignored) {
            }
        }
        archives.clear();
    }
}
