package com.jv.diablo2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputFilter;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.jv.diablo2.mpq.MpqFileSystem;
import com.jv.diablo2.tools.SamplePack;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.List;

/** Единственная Activity: поверхность игры, подключение MPQ, диалоги и отправка отчётов. */
public class MainActivity extends Activity {
    private static final int REQ_TREE = 1001;
    private GameView view;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        view = new GameView(this);
        setContentView(view);
        Nav.act = this;
        Nav.view = view;
        final BootScene boot = new BootScene();
        view.setScene(boot);
        mountAsync(null, boot);
    }

    /** Порядок: assets APK -> сохранённая папка (SAF) -> /Android/data/com.jv.diablo2/files. */
    private void mountAsync(final Uri forced, final BootScene boot) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String note = "";
                try {
                    MpqFileSystem fs = new MpqFileSystem();
                    if (forced != null) {
                        fs = DataMounter.fromTree(MainActivity.this, forced);
                    } else {
                        fs = DataMounter.fromAssets(MainActivity.this);
                        if (fs.archiveCount() == 0) {
                            String saved = getSharedPreferences("jvd2", MODE_PRIVATE).getString("tree", null);
                            if (saved != null) fs = DataMounter.fromTree(MainActivity.this, Uri.parse(saved));
                        }
                        if (fs.archiveCount() == 0) fs = DataMounter.fromDir(DataMounter.defaultDir(MainActivity.this));
                    }
                    if (fs.archiveCount() > 0) {
                        Session.attach(fs);
                        note = Session.status;
                    } else {
                        note = "MPQ не найдены. " + DataMounter.lastAssetError;
                    }
                } catch (Exception e) {
                    note = "Ошибка подключения данных: " + e.getMessage();
                }
                if (!DataMounter.lastAssetError.isEmpty()) note += "\nПропущено: " + DataMounter.lastAssetError;
                toast(note);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Nav.go(new TrademarkScene());
                    }
                });
            }
        }, "jvd2-mount").start();
    }

    void toast(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, s, Toast.LENGTH_LONG).show();
            }
        });
    }

    void askText(final String title, final String initial, final Nav.TextCb cb) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final EditText et = new EditText(MainActivity.this);
                et.setText(initial);
                et.setSingleLine(true);
                et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(15)});
                new AlertDialog.Builder(MainActivity.this).setTitle(title).setView(et)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                cb.done(et.getText().toString());
                            }
                        }).setNegativeButton("Отмена", null).show();
            }
        });
    }

    void pickDataFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_TREE);
    }

    /** Пишет report.txt и files.txt во временную папку и открывает системное меню «Поделиться». */
    void shareReport(final String report, final List<String> files) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File r = new File(getCacheDir(), "jvd2_report.txt");
                    File f = new File(getCacheDir(), "jvd2_files.txt");
                    try (FileWriter w = new FileWriter(r)) {
                        w.write(report);
                    }
                    try (FileWriter w = new FileWriter(f)) {
                        for (String s : files) w.write(s + "\n");
                    }
                    Nav.share("Отправить отчёт JVDiablo2", r, f);
                } catch (Exception e) {
                    toast("Не удалось подготовить отчёт: " + e.getMessage());
                }
            }
        }, "jvd2-share").start();
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    /** Android 10+: кладёт файл в «Загрузки» через MediaStore (без разрешений и без записей в манифесте). */
    private Uri exportFile(File f) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, f.getName());
            v.put(MediaStore.MediaColumns.MIME_TYPE, f.getName().endsWith(".zip") ? "application/zip" : "text/plain");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) throw new IOException("MediaStore отказал в создании файла");
            try (OutputStream o = getContentResolver().openOutputStream(u); InputStream in = new FileInputStream(f)) {
                copy(in, o);
            }
            return u;
        }
        if (getPackageManager().resolveContentProvider("com.jv.diablo2.files", 0) != null) {
            return FileProvider.getUriForFile(this, "com.jv.diablo2.files", f);
        }
        File dst = new File(getExternalFilesDir(null), f.getName());
        try (InputStream in = new FileInputStream(f); OutputStream o = new java.io.FileOutputStream(dst)) {
            copy(in, o);
        }
        toast("Сохранено: " + dst.getAbsolutePath());
        return null;
    }

    void shareFiles(final String title, File... files) {
        ArrayList<Uri> uris = new ArrayList<>();
        for (File f : files) {
            try {
                Uri u = exportFile(f);
                if (u != null) uris.add(u);
            } catch (Exception e) {
                toast("Не удалось сохранить " + f.getName() + ": " + e.getMessage());
            }
        }
        if (uris.isEmpty()) return;
        final Intent i = new Intent(Intent.ACTION_SEND_MULTIPLE);
        i.setType("*/*");
        i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        toast("Файлы сохранены в папку «Загрузки»");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                startActivity(Intent.createChooser(i, title));
            }
        });
    }

    /** Собирает небольшой zip с образцами файлов из MPQ (около 10-25 МБ) и открывает «Поделиться». */
    void shareSamples() {
        if (Session.fs == null) {
            toast("MPQ не подключены");
            return;
        }
        toast("Собираю образцы, подождите…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File zip = new File(getCacheDir(), "jvd2_samples.zip");
                    try (FileOutputStream o = new FileOutputStream(zip)) {
                        SamplePack.build(Session.fs, o, 30L * 1024 * 1024);
                    }
                    File rep = new File(getCacheDir(), "jvd2_report.txt");
                    try (FileWriter w = new FileWriter(rep)) {
                        w.write(Res.fullReport());
                    }
                    Nav.share("Отправить образцы JVDiablo2", zip, rep);
                } catch (Throwable e) {
                    toast("Не удалось собрать образцы: " + e.getMessage());
                }
            }
        }, "jvd2-samples").start();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_TREE || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
        getSharedPreferences("jvd2", MODE_PRIVATE).edit().putString("tree", uri.toString()).apply();
        BootScene boot = new BootScene();
        boot.msg = "Подключение MPQ…";
        view.setScene(boot);
        mountAsync(uri, boot);
    }

    @Override
    public void onBackPressed() {
        Scene s = view.scene();
        if (s != null && s.onBack()) return;
        if (s instanceof MainMenuScene || s instanceof BootScene) super.onBackPressed();
        else Nav.go(new MainMenuScene());
    }

    @Override
    protected void onPause() {
        super.onPause();
        Scene s = view.scene();
        if (s instanceof SandboxScene) Saves.save(((SandboxScene) s).hero());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }
}
