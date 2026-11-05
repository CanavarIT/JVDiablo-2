package com.jv.diablo2.gfx;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Таблица строк игры (string.tbl, expansionstring.tbl, patchstring.tbl): ключ -> текст.
 * Формат: заголовок 21 байт (crc u16, число узлов u16, размер хеш-таблицы u32, версия u8, начало строк u32,
 * максимум проб u32, размер файла u32), затем u16 на каждый узел (индекс в хеш-таблице), затем записи хеш-таблицы
 * по 17 байт: занято u8, индекс u16, хеш u32, смещение ключа u32, смещение текста u32, длина текста u16.
 * Тексты в кодировке Windows-1252 (латиница) или UTF-8; цветовые коды вида "ÿc0" остаются в строке как есть.
 */
public final class StringTbl {
    private final Map<String, String> map = new HashMap<>();

    public int size() {
        return map.size();
    }

    /** Все пары (ключ в нижнем регистре, текст) — для поиска и отладки. */
    public java.util.Set<Map.Entry<String, String>> entries() {
        return map.entrySet();
    }

    /** Текст по ключу; null — нет такой строки. Ключ без учёта регистра. */
    public String get(String key) {
        return map.get(key.toLowerCase(Locale.ROOT));
    }

    public String get(String key, String fallback) {
        String s = get(key);
        return s == null ? fallback : s;
    }

    /** Добавляет строки из файла; более поздний файл перекрывает более ранний (patchstring поверх string). */
    public void add(byte[] d) throws IOException {
        if (d.length < 21) throw new IOException("TBL: файл слишком мал");
        ByteBuffer b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        int count = b.getShort(2) & 0xFFFF;
        int hashSize = b.getInt(4);
        if (hashSize < 0 || hashSize > 1 << 20) throw new IOException("TBL: странный размер хеш-таблицы");
        int tableStart = 21 + count * 2;
        Charset cs = Charset.forName("windows-1252");
        for (int i = 0; i < hashSize; i++) {
            int p = tableStart + i * 17;
            if (p + 17 > d.length) break;
            if (b.get(p) == 0) continue;
            int keyOff = b.getInt(p + 7);
            int valOff = b.getInt(p + 11);
            int len = b.getShort(p + 15) & 0xFFFF;
            if (keyOff < 0 || valOff < 0 || keyOff >= d.length || valOff >= d.length) continue;
            String key = cstr(d, keyOff, d.length, cs);
            int end = Math.min(d.length, valOff + Math.max(0, len));
            String val = cstr(d, valOff, end, cs);
            map.put(key.toLowerCase(Locale.ROOT), val);
        }
    }

    private static String cstr(byte[] d, int from, int limit, Charset cs) {
        int e = from;
        while (e < limit && d[e] != 0) e++;
        return new String(d, from, e - from, cs);
    }
}
