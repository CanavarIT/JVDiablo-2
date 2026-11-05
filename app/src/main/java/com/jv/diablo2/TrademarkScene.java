package com.jv.diablo2;

import android.graphics.Canvas;

import com.jv.diablo2.gfx.Dc6;
import com.jv.diablo2.gfx.Palette;

/** Заставка с торговыми марками (картинка из MPQ). Касание — к главному меню. */
final class TrademarkScene extends Screen800 {
    private final Dc6 bg = FrontEnd.trademark();
    private final Palette pal = FrontEnd.sky();

    @Override
    protected void render(Canvas c) {
        Ui.background(c, bg, pal);
        if (bg == null) Ui.text(c, "DIABLO II", 400, 250, 42, Ui.GOLD, 0);
        if ((tick / 20) % 2 == 0) Ui.text(c, "Коснитесь экрана", 400, 565, 16, Ui.GRAY, 0);
    }

    @Override
    protected void tap(float x, float y) {
        Nav.go(new MainMenuScene());
    }
}
