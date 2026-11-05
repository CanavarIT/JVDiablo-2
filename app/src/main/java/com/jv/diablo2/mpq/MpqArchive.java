package com.jv.diablo2.mpq;

import com.jv.diablo2.io.RandomAccessSource;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Один MPQ-архив (формат v0, как у Diablo 2). Файлы читаются по требованию. */
public final class MpqArchive implements Closeable {
    public static final int FLAG_IMPLODE = 0x00000100;
    public static final int FLAG_COMPRESS = 0x00000200;
    public static final int FLAG_ENCRYPTED = 0x00010000;
    public static final int FLAG_FIX_KEY = 0x00020000;
    public static final int FLAG_PATCH = 0x00100000;
    public static final int FLAG_SINGLE_UNIT = 0x01000000;
    public static final int FLAG_DELETE = 0x02000000;
    public static final int FLAG_EXISTS = 0x80000000;

    private final String name;
    private final RandomAccessSource src;
    private final int sectorSize;
    private final int hashSize;
    private final int[] hash;   // по 4 int на запись: nameA, nameB, locale/platform, blockIndex
    private final int[] block;  // по 4 int на запись: pos, compressedSize, size, flags
    private final int blockCount;

    public MpqArchive(String name, RandomAccessSource src) throws IOException {
        this.name = name;
        this.src = src;
        byte[] h = new byte[32];
        src.readFully(0, h, 0, 32);
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt(0) != 0x1A51504D) throw new IOException(name + ": не MPQ (нет сигнатуры)");
        int blockShift = b.getShort(14) & 0xFFFF;
        long hashPos = b.getInt(16) & 0xFFFFFFFFL;
        long blockPos = b.getInt(20) & 0xFFFFFFFFL;
        hashSize = b.getInt(24);
        blockCount = b.getInt(28);
        if (hashSize <= 0 || (hashSize & (hashSize - 1)) != 0) throw new IOException(name + ": размер hash-таблицы не степень двойки");
        sectorSize = 512 << blockShift;

        hash = readTable(hashPos, hashSize, MpqCrypto.hash("(hash table)", MpqCrypto.HASH_FILE_KEY));
        block = readTable(blockPos, blockCount, MpqCrypto.hash("(block table)", MpqCrypto.HASH_FILE_KEY));
    }

    private int[] readTable(long pos, int entries, int key) throws IOException {
        byte[] raw = new byte[entries * 16];
        src.readFully(pos, raw, 0, raw.length);
        MpqCrypto.decrypt(raw, 0, raw.length, key);
        int[] t = new int[entries * 4];
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(t);
        return t;
    }

    public String getName() {
        return name;
    }

    /** Индекс блока по имени файла или -1. */
    public int find(String path) {
        path = norm(path);
        int a = MpqCrypto.hash(path, MpqCrypto.HASH_NAME_A);
        int b = MpqCrypto.hash(path, MpqCrypto.HASH_NAME_B);
        int mask = hashSize - 1;
        int start = MpqCrypto.hash(path, MpqCrypto.HASH_TABLE_OFFSET) & mask;
        int i = start;
        do {
            int o = i * 4;
            int blk = hash[o + 3];
            if (blk == 0xFFFFFFFF) return -1;               // пустая ячейка — цепочка закончилась
            if (blk != 0xFFFFFFFE && hash[o] == a && hash[o + 1] == b && blk >= 0 && blk < blockCount) return blk;
            i = (i + 1) & mask;
        } while (i != start);
        return -1;
    }

    public boolean contains(String path) {
        return find(path) >= 0;
    }

    public long size(String path) throws FileNotFoundException {
        int bi = find(path);
        if (bi < 0) throw new FileNotFoundException(path);
        return block[bi * 4 + 2] & 0xFFFFFFFFL;
    }

    public byte[] read(String path) throws IOException {
        int bi = find(path);
        if (bi < 0) throw new FileNotFoundException(name + ": " + path);
        return readBlock(bi, norm(path));
    }

    /** Содержимое (listfile), если оно есть в архиве. */
    public List<String> listFile() {
        List<String> res = new ArrayList<>();
        try {
            if (!contains("(listfile)")) return res;
            String text = new String(read("(listfile)"), StandardCharsets.ISO_8859_1);
            for (String line : text.split("[\r\n]+")) {
                line = line.trim();
                if (!line.isEmpty()) res.add(line);
            }
        } catch (IOException | RuntimeException e) {
            // listfile опционален
        }
        return res;
    }

    private static String norm(String p) {
        return p.replace('/', '\\');
    }

    private byte[] readBlock(int bi, String path) throws IOException {
        int o = bi * 4;
        long pos = block[o] & 0xFFFFFFFFL;
        int cSize = block[o + 1];
        int fSize = block[o + 2];
        int flags = block[o + 3];
        if ((flags & FLAG_EXISTS) == 0 || (flags & FLAG_DELETE) != 0) throw new FileNotFoundException(path);
        if ((flags & FLAG_PATCH) != 0) throw new IOException("Патч-файлы MPQ v2+ не поддерживаются: " + path);
        if (fSize == 0) return new byte[0];

        int key = 0;
        if ((flags & FLAG_ENCRYPTED) != 0) {
            key = MpqCrypto.hash(path.substring(path.lastIndexOf('\\') + 1), MpqCrypto.HASH_FILE_KEY);
            if ((flags & FLAG_FIX_KEY) != 0) key = (key + (int) pos) ^ fSize;
        }
        boolean compressed = (flags & (FLAG_COMPRESS | FLAG_IMPLODE)) != 0;
        boolean implode = (flags & FLAG_IMPLODE) != 0 && (flags & FLAG_COMPRESS) == 0;

        if ((flags & FLAG_SINGLE_UNIT) != 0) {
            byte[] raw = new byte[cSize];
            src.readFully(pos, raw, 0, cSize);
            if ((flags & FLAG_ENCRYPTED) != 0) MpqCrypto.decrypt(raw, 0, cSize, key);
            if (compressed && cSize < fSize) return decompress(raw, fSize, implode);
            return raw;
        }

        int sectors = (fSize + sectorSize - 1) / sectorSize;
        byte[] out = new byte[fSize];

        if (!compressed) {
            src.readFully(pos, out, 0, fSize);
            if ((flags & FLAG_ENCRYPTED) != 0) {
                for (int i = 0; i < sectors; i++) {
                    int off = i * sectorSize;
                    MpqCrypto.decrypt(out, off, Math.min(sectorSize, fSize - off), key + i);
                }
            }
            return out;
        }

        byte[] tab = new byte[(sectors + 1) * 4];
        src.readFully(pos, tab, 0, tab.length);
        if ((flags & FLAG_ENCRYPTED) != 0) MpqCrypto.decrypt(tab, 0, tab.length, key - 1);
        int[] offs = new int[sectors + 1];
        ByteBuffer.wrap(tab).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(offs);

        for (int i = 0; i < sectors; i++) {
            int len = offs[i + 1] - offs[i];
            if (len < 0 || len > sectorSize * 2) throw new IOException(path + ": битая таблица секторов");
            byte[] raw = new byte[len];
            src.readFully(pos + (offs[i] & 0xFFFFFFFFL), raw, 0, len);
            if ((flags & FLAG_ENCRYPTED) != 0) MpqCrypto.decrypt(raw, 0, len, key + i);
            int off = i * sectorSize;
            int expected = Math.min(sectorSize, fSize - off);
            byte[] part = (len < expected) ? decompress(raw, expected, implode) : raw;
            System.arraycopy(part, 0, out, off, expected);
        }
        return out;
    }

    private static byte[] decompress(byte[] raw, int expected, boolean implode) throws IOException {
        if (implode) return Pkware.explode(raw, 0, raw.length, expected);
        int mask = raw[0] & 0xFF;
        switch (mask) {
            case 0x02:
                return inflate(raw, 1, raw.length - 1, expected);
            case 0x08:
                return Pkware.explode(raw, 1, raw.length - 1, expected);
            default:
                throw new UnsupportedOperationException(String.format(
                        "Сжатие MPQ 0x%02X пока не поддерживается (звук: Huffman+ADPCM — этап 2)", mask));
        }
    }

    private static byte[] inflate(byte[] data, int off, int len, int expected) throws IOException {
        Inflater inf = new Inflater();
        try {
            inf.setInput(data, off, len);
            byte[] out = new byte[expected];
            int n = 0;
            while (!inf.finished() && n < expected) {
                int r = inf.inflate(out, n, expected - n);
                if (r == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                n += r;
            }
            if (n != expected) throw new IOException("zlib: размер " + n + " вместо " + expected);
            return out;
        } catch (DataFormatException e) {
            throw new IOException("zlib: " + e.getMessage(), e);
        } finally {
            inf.end();
        }
    }

    @Override
    public void close() throws IOException {
        src.close();
    }
}
