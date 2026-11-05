package com.jv.diablo2.gfx;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Разбор DS1 (раскладка локации). Сверено на реальных файлах города акта 1 (v13 и v18):
 * слои стен/ориентаций/полов/теней, список объектов (type 1 = заготовка монстра/NPC из MonPreset.txt,
 * type 2 = объект из Objects.txt), маршруты NPC. Координаты объектов — в субтайлах (5 на плитку).
 */
public final class Ds1 {
    public static final class Obj {
        public int type, id, x, y, flags;
        /** Для type 1: маршрут NPC (x, y, действие), если есть. */
        public final List<int[]> path = new ArrayList<>();
    }

    public int version, width, height, act, substitutionType;
    public final List<String> files = new ArrayList<>();
    public int[][] wall, orient, floor;
    public int[] shadow, tag;
    public final List<Obj> objects = new ArrayList<>();

    public static int mainIndex(int cell) {
        return (cell >>> 20) & 0x3F;
    }

    public static int subIndex(int cell) {
        return (cell >>> 8) & 0x3F;
    }

    /** Бит 31: клетка скрыта (не рисуется). */
    public static boolean hidden(int cell) {
        return (cell & 0x80000000) != 0;
    }

    public static Ds1 parse(byte[] d) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        Ds1 s = new Ds1();
        try {
            s.version = b.getInt();
            s.width = b.getInt() + 1;
            s.height = b.getInt() + 1;
            if (s.version >= 8) s.act = Math.max(0, Math.min(4, b.getInt()));
            if (s.version >= 10) s.substitutionType = b.getInt();
            if (s.version >= 3) {
                int n = b.getInt();
                if (n < 0 || n > 200) throw new IOException("DS1: странный список файлов");
                for (int i = 0; i < n; i++) {
                    StringBuilder sb = new StringBuilder();
                    byte c;
                    while ((c = b.get()) != 0) sb.append((char) (c & 0xFF));
                    s.files.add(sb.toString());
                }
            }
            if (s.version >= 9 && s.version <= 13) b.position(b.position() + 8);
            int walls = 1, floors = 1;
            if (s.version >= 4) {
                walls = b.getInt();
                floors = s.version >= 16 ? b.getInt() : 1;
            }
            if (s.width <= 0 || s.height <= 0 || s.width > 2000 || s.height > 2000 || walls < 0 || walls > 8 || floors < 0 || floors > 4)
                throw new IOException("DS1: странные размеры");
            int cells = s.width * s.height;
            boolean hasTag = s.substitutionType == 1 || s.substitutionType == 2;
            s.wall = new int[walls][];
            s.orient = new int[walls][];
            s.floor = new int[floors][];
            if (s.version >= 4) {
                for (int w = 0; w < walls; w++) {
                    s.wall[w] = layer(b, cells);
                    s.orient[w] = layer(b, cells);
                }
                for (int f = 0; f < floors; f++) s.floor[f] = layer(b, cells);
                s.shadow = layer(b, cells);
                if (hasTag) s.tag = layer(b, cells);
            } else {
                s.wall[0] = layer(b, cells);
                s.floor[0] = layer(b, cells);
                s.orient[0] = layer(b, cells);
                if (hasTag) s.tag = layer(b, cells);
                s.shadow = layer(b, cells);
            }
            readObjects(b, s);
        } catch (java.nio.BufferUnderflowException e) {
            throw new IOException("DS1: файл оборван");
        }
        return s;
    }

    /** Объекты и маршруты. Ошибки здесь не фатальны: карта остаётся пригодной без объектов. */
    private static void readObjects(ByteBuffer b, Ds1 s) {
        try {
            if (s.version < 2) return;
            int n = b.getInt();
            if (n < 0 || n > 20000) return;
            for (int i = 0; i < n; i++) {
                Obj o = new Obj();
                o.type = b.getInt();
                o.id = b.getInt();
                o.x = b.getInt();
                o.y = b.getInt();
                if (s.version >= 5) o.flags = b.getInt();
                s.objects.add(o);
            }
            if (s.version >= 12 && (s.substitutionType == 1 || s.substitutionType == 2)) {
                if (s.version >= 18) b.getInt();
                int groups = b.getInt();
                if (groups < 0 || groups > 10000) return;
                b.position(b.position() + groups * 20);
            }
            if (s.version >= 14) {
                int npcs = b.getInt();
                if (npcs < 0 || npcs > 10000) return;
                for (int i = 0; i < npcs; i++) {
                    int np = b.getInt(), x = b.getInt(), y = b.getInt();
                    Obj owner = null;
                    for (Obj o : s.objects) if (o.type == 1 && o.x == x && o.y == y) owner = o;
                    for (int k = 0; k < np; k++) {
                        int px = b.getInt(), py = b.getInt();
                        int act = s.version >= 15 ? b.getInt() : 0;
                        if (owner != null) owner.path.add(new int[]{px, py, act});
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static int[] layer(ByteBuffer b, int cells) {
        int[] l = new int[cells];
        for (int i = 0; i < cells; i++) l[i] = b.getInt();
        return l;
    }

    /** Путь файла плиток: полный ("...data\\global\\tiles\\...") обрезается до "data\\global...", относительный получает префикс. */
    public static String tilePath(String name) {
        String s = name.toLowerCase().replace('/', '\\');
        while (s.startsWith("\\")) s = s.substring(1);
        int k = s.indexOf("data\\global");
        return k >= 0 ? s.substring(k) : "data\\global\\tiles\\" + s;
    }

    /** Имя DT1 из строки DS1: ".tg1" заменяется на ".dt1". */
    public static String dt1Path(String ds1Name) {
        String s = tilePath(ds1Name);
        if (s.endsWith(".tg1")) s = s.substring(0, s.length() - 4) + ".dt1";
        return s;
    }
}
