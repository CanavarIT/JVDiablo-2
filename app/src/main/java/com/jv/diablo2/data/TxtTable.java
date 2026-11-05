package com.jv.diablo2.data;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Табличные данные игры (data\global\excel\*.txt): TSV с заголовком. Имена колонок — без учёта регистра. */
public final class TxtTable {
    private final Map<String, Integer> cols = new HashMap<>();
    private final List<String[]> rows = new ArrayList<>();

    public static TxtTable parse(byte[] data) {
        TxtTable t = new TxtTable();
        String text = new String(data, StandardCharsets.ISO_8859_1);
        String[] lines = text.split("\n", -1);
        boolean header = true;
        for (String line : lines) {
            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            if (line.isEmpty()) continue;
            String[] f = line.split("\t", -1);
            if (header) {
                for (int i = 0; i < f.length; i++) t.cols.putIfAbsent(f[i].trim().toLowerCase(Locale.ROOT), i);
                header = false;
            } else {
                t.rows.add(f);
            }
        }
        return t;
    }

    public int size() {
        return rows.size();
    }

    public boolean hasColumn(String col) {
        return cols.containsKey(col.toLowerCase(Locale.ROOT));
    }

    public Row row(int i) {
        return new Row(rows.get(i));
    }

    public List<Row> all() {
        List<Row> r = new ArrayList<>(rows.size());
        for (String[] f : rows) r.add(new Row(f));
        return r;
    }

    public Row find(String col, String value) {
        Integer c = cols.get(col.toLowerCase(Locale.ROOT));
        if (c == null) return null;
        for (String[] f : rows) {
            if (c < f.length && f[c].trim().equalsIgnoreCase(value)) return new Row(f);
        }
        return null;
    }

    public final class Row {
        private final String[] f;

        Row(String[] f) {
            this.f = f;
        }

        public String get(String col) {
            Integer c = cols.get(col.toLowerCase(Locale.ROOT));
            if (c == null || c >= f.length) return "";
            return f[c].trim();
        }

        public int getInt(String col, int def) {
            String s = get(col);
            if (s.isEmpty()) return def;
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return def;
            }
        }

        public String first() {
            return f.length > 0 ? f[0].trim() : "";
        }
    }
}
