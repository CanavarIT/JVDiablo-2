package com.jv.diablo2.io;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/** Источник поверх FileChannel (обычный файл или дескриптор из SAF). Потокобезопасен. */
public final class ChannelSource implements RandomAccessSource {
    private final FileChannel ch;
    private final Closeable extra;
    private final long base;
    private final long len;     // -1 = до конца файла

    public ChannelSource(FileChannel ch, Closeable extra) {
        this(ch, extra, 0, -1);
    }

    /** base/len — окно внутри файла (так читаются несжатые assets внутри APK). */
    public ChannelSource(FileChannel ch, Closeable extra, long base, long len) {
        this.ch = ch;
        this.extra = extra;
        this.base = base;
        this.len = len;
    }

    public static ChannelSource open(File f) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        return new ChannelSource(raf.getChannel(), raf);
    }

    @Override
    public long length() throws IOException {
        return len >= 0 ? len : ch.size() - base;
    }

    @Override
    public void readFully(long pos, byte[] dst, int off, int len) throws IOException {
        if (this.len >= 0 && pos + len > this.len) throw new EOFException("MPQ: чтение за концом окна, pos=" + pos);
        ByteBuffer bb = ByteBuffer.wrap(dst, off, len);
        while (bb.hasRemaining()) {
            int n = ch.read(bb, base + pos + (bb.position() - off));
            if (n < 0) throw new EOFException("MPQ: чтение за концом файла, pos=" + pos);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            ch.close();
        } finally {
            if (extra != null) extra.close();
        }
    }
}
