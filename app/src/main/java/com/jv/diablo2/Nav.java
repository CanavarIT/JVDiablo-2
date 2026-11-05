package com.jv.diablo2;



import java.io.File;

/** Навигация между экранами и доступ к функциям Activity (диалоги, отправка файлов). */
final class Nav {
    private Nav() {
    }

    interface TextCb {
        void done(String text);
    }

    static MainActivity act;
    static GameView view;

    static void go(Scene s) {
        if (view != null) view.setScene(s);
    }

    static void toast(String s) {
        if (act != null) act.toast(s);
    }

    static void exit() {
        if (act != null) act.finish();
    }

    static void askName(String title, String initial, TextCb cb) {
        if (act != null) act.askText(title, initial, cb);
    }

    static File saveDir() {
        File d = new File(act.getFilesDir(), "save");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Сохраняет файлы в «Загрузки» и открывает системное меню «Поделиться». */
    static void share(String title, File... files) {
        if (act != null) act.shareFiles(title, files);
    }
}
