package com.jv.diablo2;

import com.jv.diablo2.game.CharClass;
import com.jv.diablo2.gfx.Dc6;
import com.jv.diablo2.gfx.Palette;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FrontEnd {
    private FrontEnd() {
    }

    static final String UI = "data\\global\\ui\\FrontEnd\\";
    static final String PAL = "data\\global\\palette\\";

    static Dc6 trademark() {
        return Res.dc6("trademark", new String[]{UI + "trademarkscreenEXP.dc6", UI + "trademarkscreen.dc6"}, "frontend", "trademark");
    }

    static Dc6 mainMenuBg() {
        return Res.dc6("mainmenu-bg", new String[]{UI + "gameselectscreenEXP.dc6", UI + "gameselectscreen.dc6"}, "frontend", "gameselect");
    }

    /** Экран «Character Select» (список сохранённых героев): characterselectscreen. */
    static Dc6 charSelectBg() {
        Dc6 d = Res.dc6("charselect-bg", new String[]{UI + "characterselectscreenEXP.dc6", UI + "characterselectscreen.dc6"}, "frontend", "characterselect");
        return d != null ? d : Res.dc6("charselect-bg2", new String[]{UI + "charactercreationscreenEXP.dc6"}, "frontend", "charactercreation");
    }

    /** Экран «Select Hero Class» (создание героя): charactercreationscreen. */
    static Dc6 charCreateBg() {
        Dc6 d = Res.dc6("charcreate-bg", new String[]{UI + "charactercreationscreenEXP.dc6", UI + "charactercreationscreen.dc6"}, "frontend", "charactercreation");
        return d != null ? d : charSelectBg();
    }

    static Dc6 loadingBg() {
        return Res.dc6("loading-bg", new String[]{"data\\global\\ui\\Loading\\loadingscreen.dc6"}, "loading", "loadingscreen");
    }

    static Dc6 buttonWide() {
        return Res.dc6("button-wide", new String[]{UI + "WideButtonBlank.dc6"}, "frontend", "widebutton");
    }

    static Dc6 buttonMedium() {
        return Res.dc6("button-medium", new String[]{UI + "MediumButtonBlank.dc6"}, "frontend", "mediumbutton");
    }

    static Dc6 logo(String part) {   
        return Res.dc6("logo-" + part, new String[]{UI + "D2logo" + part + ".dc6"}, "frontend", "logo" + part.toLowerCase(Locale.ROOT));
    }

    static Palette sky() {
        return Res.palette(PAL + "sky\\pal.dat", PAL + "loading\\pal.dat", PAL + "act1\\pal.dat");
    }

    static Palette units() {
        return Res.palette(PAL + "units\\pal.dat", PAL + "act1\\pal.dat");
    }

    static Palette fechar() {
        return Res.palette(PAL + "fechar\\pal.dat", PAL + "sky\\pal.dat", PAL + "act1\\pal.dat");
    }

    static Palette fontPalette() {
        return Res.palette(PAL + "static\\pal.dat", PAL + "sky\\pal.dat");
    }

    static String className(CharClass c) {
        return c.name().toLowerCase(Locale.ROOT);
    }

    static List<String> classFiles(CharClass c) {
        List<String> r = new ArrayList<>();
        if (Session.fs == null) return r;
        for (String f : Session.fs.search("frontend\\" + className(c) + "\\")) if (f.endsWith(".dc6")) r.add(f);
        return r;
    }

    static Dc6 classIdle(CharClass c) {
        List<String> files = classFiles(c);
        String pick = null;
        for (String f : files) {
            String n = f.substring(f.lastIndexOf('\\') + 1);
            if (n.contains("nu")) {
                pick = f;
                break;
            }
        }
        if (pick == null && !files.isEmpty()) pick = files.get(0);
        if (pick == null) {
            Res.note(String.format(Locale.ROOT, "%-22s НЕТ DC6 в frontend\\%s\\", "class-" + className(c), className(c)));
            return null;
        }
        Res.note(String.format(Locale.ROOT, "%-22s %s (всего файлов %d)", "class-" + className(c), pick, files.size()));
        return Res.dc6(pick);
    }
}
