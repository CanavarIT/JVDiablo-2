package com.jv.diablo2.gfx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * animdata.d2: скорость анимации каждого COF. Файл — 256 корзин; в каждой: int count, затем count записей по 160 байт:
 * имя COF (8 байт, например "FAA1HTH"), кадров на направление (int), скорость (int, 256 = один кадр за тик при 25 тиках в секунду)
 * и 144 байта флагов кадров. Если разбор не сошёлся с размером файла, таблица остаётся пустой и берутся запасные скорости.
 */
public final class AnimData {
    private final Map<String, int[]> byName = new HashMap<>();

    public static AnimData parse(byte[] d) {
        AnimData a = new AnimData();
        try {
            ByteBuffer b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
            Map<String, int[]> tmp = new HashMap<>();
            for (int bucket = 0; bucket < 256; bucket++) {
                int count = b.getInt();
                if (count < 0 || count > 5000) return a;
                for (int i = 0; i < count; i++) {
                    int p = b.position();
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < 8; k++) {
                        int c = d[p + k] & 0xFF;
                        if (c == 0) break;
                        sb.append((char) c);
                    }
                    int frames = b.getInt(p + 8), speed = b.getInt(p + 12);
                    tmp.put(sb.toString().toUpperCase(Locale.ROOT), new int[]{frames, speed});
                    b.position(p + 160);
                }
            }
            if (b.position() == d.length) a.byName.putAll(tmp);
        } catch (RuntimeException e) {
            a.byName.clear();
        }
        return a;
    }

    public static AnimData empty() {
        return new AnimData();
    }

    /** Скорость COF (256 = кадр за тик) или -1, если имени нет в таблице. */
    public int speed(String cofName) {
        int[] v = byName.get(cofName.toUpperCase(Locale.ROOT));
        return v == null || v[1] <= 0 ? -1 : v[1];
    }

    public boolean isEmpty() {
        return byName.isEmpty();
    }
}
