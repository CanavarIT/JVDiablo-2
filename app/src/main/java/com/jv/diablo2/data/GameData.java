package com.jv.diablo2.data;

import com.jv.diablo2.mpq.MpqFileSystem;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Доступ к таблицам игры с кешем. Всё читается из смонтированных MPQ. */
public final class GameData {
    public static final String EXCEL = "data\\global\\excel\\";

    private final MpqFileSystem fs;
    private final Map<String, TxtTable> cache = new HashMap<>();

    public GameData(MpqFileSystem fs) {
        this.fs = fs;
    }

    public MpqFileSystem fs() {
        return fs;
    }

    public boolean hasTable(String name) {
        return fs.exists(EXCEL + name + ".txt");
    }

    public synchronized TxtTable table(String name) throws IOException {
        String key = name.toLowerCase(Locale.ROOT);
        TxtTable t = cache.get(key);
        if (t == null) {
            t = TxtTable.parse(fs.read(EXCEL + name + ".txt"));
            cache.put(key, t);
        }
        return t;
    }
}
