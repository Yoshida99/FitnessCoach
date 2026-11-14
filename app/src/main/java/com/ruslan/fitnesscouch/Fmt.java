package com.ruslan.fitnesscouch;

import java.util.Locale;

final class Fmt {
    /** 60.0 → "60", 57.5 → "57.5", 2.25 → "2.25". */
    static String n(float v) {
        if (Math.abs(v - Math.round(v)) < 0.005f) return String.valueOf(Math.round(v));
        String s = String.format(Locale.US, "%.2f", v);
        return s.endsWith("0") ? s.substring(0, s.length() - 1) : s;
    }

    static String mmss(long sec) { return String.format(Locale.US, "%d:%02d", sec / 60, sec % 60); }

    static String signed(float v) { return (v > 0 ? "+" : v < 0 ? "−" : "") + n(Math.abs(v)); }
}
