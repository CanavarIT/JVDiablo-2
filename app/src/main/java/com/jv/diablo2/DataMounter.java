package com.jv.diablo2;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.documentfile.provider.DocumentFile;

import com.jv.diablo2.io.ChannelSource;
import com.jv.diablo2.mpq.MpqFileSystem;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Подключение MPQ: из assets APK (основной путь), из папки SAF или из обычной папки. */
final class DataMounter {
    private DataMounter() {
    }

    /** Ошибки последнего чтения assets (например, архив сжат в APK). */
    static volatile String lastAssetError = "";

    /** Читает .mpq прямо из APK через AssetFileDescriptor: без копирования, с позиционным чтением. */
    static MpqFileSystem fromAssets(Context ctx) {
        MpqFileSystem fs = new MpqFileSystem();
        AssetManager am = ctx.getAssets();
        Set<String> seen = new HashSet<>();
        StringBuilder err = new StringBuilder();
        for (String dir : new String[]{"", "d2", "data", "mpq"}) {
            String[] list;
            try {
                list = am.list(dir);
            } catch (IOException e) {
                continue;
            }
            if (list == null) continue;
            for (String n : list) {
                String low = n.toLowerCase(Locale.ROOT);
                if (!low.endsWith(".mpq") || !seen.add(low)) continue;
                try {
                    AssetFileDescriptor afd = am.openFd(dir.isEmpty() ? n : dir + "/" + n);
                    FileInputStream in = new FileInputStream(afd.getFileDescriptor());
                    fs.mount(n, new ChannelSource(in.getChannel(), afd, afd.getStartOffset(), afd.getLength()));
                } catch (IOException | RuntimeException e) {
                    err.append(n).append(": ").append(e.getMessage()).append("; ");
                }
            }
        }
        lastAssetError = err.toString();
        return fs;
    }

    static File defaultDir(Context ctx) {
        return ctx.getExternalFilesDir(null);
    }

    static MpqFileSystem fromDir(File dir) throws IOException {
        MpqFileSystem fs = new MpqFileSystem();
        File[] list = dir == null ? null : dir.listFiles();
        if (list != null) {
            for (File f : list) {
                if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".mpq")) {
                    try {
                        fs.mount(f.getName(), ChannelSource.open(f));
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        return fs;
    }

    static MpqFileSystem fromTree(Context ctx, Uri tree) throws IOException {
        MpqFileSystem fs = new MpqFileSystem();
        DocumentFile root = DocumentFile.fromTreeUri(ctx, tree);
        if (root == null) return fs;
        for (DocumentFile f : root.listFiles()) {
            String n = f.getName();
            if (n == null || !n.toLowerCase(Locale.ROOT).endsWith(".mpq")) continue;
            try {
                ParcelFileDescriptor pfd = ctx.getContentResolver().openFileDescriptor(f.getUri(), "r");
                if (pfd == null) continue;
                FileInputStream in = new FileInputStream(pfd.getFileDescriptor());
                fs.mount(n, new ChannelSource(in.getChannel(), pfd));
            } catch (IOException | RuntimeException ignored) {
            }
        }
        return fs;
    }
}
