package com.jv.diablo2;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Воспроизведение PCM WAV из MPQ. WAV, сжатые в MPQ через Huffman/ADPCM, пока не читаются (этап 3). */
final class AudioPlayer {
    private AudioPlayer() {
    }

    private static int token;

    static synchronized void stop() {
        token++;
    }

    static synchronized void play(final byte[] wav) throws IOException {
        token++;
        final int my = token;
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if (wav.length < 44 || b.getInt(0) != 0x46464952 || b.getInt(8) != 0x45564157) throw new IOException("Это не WAV");
        int p = 12, tag = 0, ch = 0, rate = 0, bits = 0, dataOff = -1, dataLen = 0;
        while (p + 8 <= wav.length) {
            int id = b.getInt(p), len = b.getInt(p + 4);
            if (id == 0x20746D66) {            // "fmt "
                tag = b.getShort(p + 8);
                ch = b.getShort(p + 10);
                rate = b.getInt(p + 12);
                bits = b.getShort(p + 22);
            } else if (id == 0x61746164) {     // "data"
                dataOff = p + 8;
                dataLen = Math.min(len, wav.length - dataOff);
                break;
            }
            p += 8 + len + (len & 1);
        }
        if (dataOff < 0) throw new IOException("WAV без данных");
        if (tag != 1 || (bits != 16 && bits != 8) || ch < 1 || ch > 2) throw new IOException("WAV формата " + tag + " не поддерживается (нужен PCM)");
        final int mask = ch == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
        final int enc = bits == 16 ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT;
        final int min = AudioTrack.getMinBufferSize(rate, mask, enc);
        final AudioTrack t = new AudioTrack(AudioManager.STREAM_MUSIC, rate, mask, enc, Math.max(min, 16384), AudioTrack.MODE_STREAM);
        final int off = dataOff, len = dataLen;
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    t.play();
                    int pos = 0;
                    while (pos < len && my == token) {
                        int n = t.write(wav, off + pos, Math.min(16384, len - pos));
                        if (n <= 0) break;
                        pos += n;
                    }
                    t.stop();
                } catch (RuntimeException ignored) {
                } finally {
                    t.release();
                }
            }
        }, "jvd2-audio");
        th.setDaemon(true);
        th.start();
    }
}
