package com.jv.diablo2;

import android.graphics.Canvas;

/** Экран на время подключения MPQ. */
final class BootScene extends Screen800 {
    volatile String msg = "Загрузка данных игры…";

    @Override
    protected void render(Canvas c) {
        Ui.text(c, "DIABLO II", 400, 250, 42, Ui.GOLD, 0);
        Ui.text(c, msg, 400, 320, 16, Ui.WHITE, 0);
    }
}
