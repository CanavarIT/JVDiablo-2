package com.jv.diablo2.io;

import java.io.Closeable;
import java.io.IOException;

/** Позиционное чтение: MPQ по 250+ МБ целиком в память не грузим. */
public interface RandomAccessSource extends Closeable {
    long length() throws IOException;

    /** Читает ровно len байт начиная с pos, иначе бросает EOFException. */
    void readFully(long pos, byte[] dst, int off, int len) throws IOException;
}
