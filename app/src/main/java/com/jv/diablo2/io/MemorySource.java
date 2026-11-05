package com.jv.diablo2.io;

import java.io.EOFException;
import java.io.IOException;

/** Источник из массива байт (для тестов). */
public final class MemorySource implements RandomAccessSource {
    private final byte[] data;

    public MemorySource(byte[] data) {
        this.data = data;
    }

    @Override
    public long length() {
        return data.length;
    }

    @Override
    public void readFully(long pos, byte[] dst, int off, int len) throws IOException {
        if (pos < 0 || pos + len > data.length) throw new EOFException();
        System.arraycopy(data, (int) pos, dst, off, len);
    }

    @Override
    public void close() {
    }
}
