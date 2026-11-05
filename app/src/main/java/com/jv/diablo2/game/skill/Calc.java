package com.jv.diablo2.game.skill;

/**
 * Вычислитель формул из Skills.txt/Missiles.txt: числа, + - * / %, сравнения, && || !, ?:, скобки,
 * min/max/abs/floor/ceil, skill('Имя'.lvl), stat('имя'). Неизвестные имена дают 0.
 */
public final class Calc {
    public interface Ctx {
        double var(String name);

        double skill(String name, String field);
    }

    private final String s;
    private final Ctx ctx;
    private int i;

    private Calc(String s, Ctx ctx) {
        this.s = s;
        this.ctx = ctx;
    }

    public static double eval(String expr, Ctx ctx) {
        if (expr == null || expr.trim().isEmpty()) return 0;
        try {
            Calc c = new Calc(expr, ctx);
            double v = c.ternary();
            return Double.isNaN(v) || Double.isInfinite(v) ? 0 : v;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private boolean eat(String t) {
        ws();
        if (s.startsWith(t, i)) {
            i += t.length();
            return true;
        }
        return false;
    }

    private double ternary() {
        double c = or();
        if (eat("?")) {
            double a = ternary();
            if (!eat(":")) throw new IllegalStateException("':' expected");
            double b = ternary();
            return c != 0 ? a : b;
        }
        return c;
    }

    private double or() {
        double v = and();
        while (eat("||")) {
            double r = and();
            v = (v != 0 || r != 0) ? 1 : 0;
        }
        return v;
    }

    private double and() {
        double v = eq();
        while (eat("&&")) {
            double r = eq();
            v = (v != 0 && r != 0) ? 1 : 0;
        }
        return v;
    }

    private double eq() {
        double v = rel();
        while (true) {
            if (eat("==")) v = (v == rel()) ? 1 : 0;
            else if (eat("!=")) v = (v != rel()) ? 1 : 0;
            else return v;
        }
    }

    private double rel() {
        double v = add();
        while (true) {
            if (eat("<=")) v = (v <= add()) ? 1 : 0;
            else if (eat(">=")) v = (v >= add()) ? 1 : 0;
            else if (eat("<")) v = (v < add()) ? 1 : 0;
            else if (eat(">")) v = (v > add()) ? 1 : 0;
            else return v;
        }
    }

    private double add() {
        double v = mul();
        while (true) {
            if (eat("+")) v += mul();
            else if (eat("-")) v -= mul();
            else return v;
        }
    }

    private double mul() {
        double v = unary();
        while (true) {
            if (eat("*")) v *= unary();
            else if (eat("/")) {
                double d = unary();
                v = d == 0 ? 0 : v / d;
            } else if (eat("%")) {
                double d = unary();
                v = d == 0 ? 0 : v % d;
            } else return v;
        }
    }

    private double unary() {
        if (eat("-")) return -unary();
        if (eat("+")) return unary();
        if (eat("!")) return unary() == 0 ? 1 : 0;
        return primary();
    }

    private double primary() {
        ws();
        if (eat("(")) {
            double v = ternary();
            if (!eat(")")) throw new IllegalStateException("')' expected");
            return v;
        }
        char c = i < s.length() ? s.charAt(i) : 0;
        if (Character.isDigit(c) || c == '.') {
            int st = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            return Double.parseDouble(s.substring(st, i));
        }
        if (Character.isLetter(c) || c == '_') {
            int st = i;
            while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
            String name = s.substring(st, i).toLowerCase();
            if (eat("(")) return call(name);
            return ctx == null ? 0 : ctx.var(name);
        }
        throw new IllegalStateException("unexpected char at " + i);
    }

    private String quoted() {
        ws();
        if (i >= s.length() || s.charAt(i) != '\'') throw new IllegalStateException("quote expected");
        int e = s.indexOf('\'', i + 1);
        if (e < 0) throw new IllegalStateException("unterminated string");
        String r = s.substring(i + 1, e);
        i = e + 1;
        return r;
    }

    private double call(String fn) {
        if (fn.equals("skill") || fn.equals("stat")) {
            String name = quoted();
            String field = "lvl";
            if (fn.equals("skill") && eat(".")) {
                ws();
                int st = i;
                while (i < s.length() && Character.isLetterOrDigit(s.charAt(i))) i++;
                field = s.substring(st, i).toLowerCase();
            }
            if (!eat(")")) throw new IllegalStateException("')' expected");
            if (ctx == null) return 0;
            return fn.equals("skill") ? ctx.skill(name, field) : ctx.var("stat:" + name.toLowerCase());
        }
        double[] a = new double[8];
        int n = 0;
        if (!eat(")")) {
            do {
                double v = ternary();
                if (n < a.length) a[n++] = v;
            } while (eat(","));
            if (!eat(")")) throw new IllegalStateException("')' expected");
        }
        switch (fn) {
            case "min": return n >= 2 ? Math.min(a[0], a[1]) : a[0];
            case "max": return n >= 2 ? Math.max(a[0], a[1]) : a[0];
            case "abs": return Math.abs(a[0]);
            case "floor": return Math.floor(a[0]);
            case "ceil": return Math.ceil(a[0]);
            case "rand": return n >= 2 ? (a[0] + a[1]) / 2.0 : a[0];
            default: return 0;
        }
    }
}
