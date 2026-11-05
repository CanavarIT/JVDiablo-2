package com.jv.diablo2.game.item;

import com.jv.diablo2.data.TxtTable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** ItemTypes.txt: дерево типов предметов (weap -> mele -> swor ...) и места на теле. */
public final class ItemTypeDb {
    public static final class Type {
        public String name = "", code = "", equiv1 = "", equiv2 = "", bodyLoc1 = "", bodyLoc2 = "";
        public boolean body;
    }

    private final Map<String, Type> byCode = new HashMap<>();

    public static ItemTypeDb load(TxtTable t) {
        ItemTypeDb db = new ItemTypeDb();
        for (TxtTable.Row r : t.all()) {
            String code = low(r.get("Code"));
            if (code.isEmpty()) continue;
            Type ty = new Type();
            ty.name = r.get("ItemType");
            ty.code = code;
            ty.equiv1 = low(r.get("Equiv1"));
            ty.equiv2 = low(r.get("Equiv2"));
            ty.body = r.getInt("Body", 0) == 1;
            ty.bodyLoc1 = low(r.get("BodyLoc1"));
            ty.bodyLoc2 = low(r.get("BodyLoc2"));
            db.byCode.put(code, ty);
        }
        return db;
    }

    static String low(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    public Type get(String code) {
        return byCode.get(low(code));
    }

    public boolean isA(String code, String ancestor) {
        return isA(low(code), low(ancestor), 0);
    }

    private boolean isA(String code, String anc, int depth) {
        if (code.isEmpty() || anc.isEmpty() || depth > 12) return false;
        if (code.equals(anc)) return true;
        Type t = byCode.get(code);
        return t != null && (isA(t.equiv1, anc, depth + 1) || isA(t.equiv2, anc, depth + 1));
    }

    /** Ближайший тип (сам или предок), у которого задано место на теле. */
    public Type bodyType(String code) {
        return bodyType(low(code), 0);
    }

    private Type bodyType(String code, int depth) {
        if (code.isEmpty() || depth > 12) return null;
        Type t = byCode.get(code);
        if (t == null) return null;
        if (t.body && !t.bodyLoc1.isEmpty()) return t;
        Type r = bodyType(t.equiv1, depth + 1);
        return r != null ? r : bodyType(t.equiv2, depth + 1);
    }
}
