package com.ruslan.fitnesscouch;

/** Сигналы конца отдыха. Звук синтезируется в коде — в приложении нет аудиофайлов. */
final class Signal {
    static final int RATE = 44100;

    /** Порядок = порядок в настройках. "system" — звук уведомлений телефона, "none" — без звука. */
    static final String[] IDS = {"triple", "siren", "bell", "long", "double", "system", "none"};
    static final String[] NAMES = {"Тройной сигнал", "Сирена", "Колокол", "Длинный гудок", "Двойной короткий",
            "Звук уведомлений телефона", "Без звука"};
    static final String DEFAULT = "triple";

    static String name(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return NAMES[i];
        return NAMES[0];
    }

    /** Пары {частота Гц, длительность мс}; частота 0 — пауза. Частоты выбраны там, где динамик телефона звучит громче всего. */
    static int[][] pattern(String id) {
        switch (id) {
            case "siren":
                return new int[][]{{1400, 220}, {2000, 220}, {1400, 220}, {2000, 220}, {1400, 220}, {2000, 220}, {1400, 220}, {2000, 320}};
            case "bell":
                return new int[][]{{1760, 700}, {0, 120}, {1760, 900}};
            case "long":
                return new int[][]{{1500, 1400}};
            case "double":
                return new int[][]{{2100, 160}, {0, 110}, {2100, 160}};
            default:
                return new int[][]{{1800, 260}, {0, 140}, {1800, 260}, {0, 140}, {1800, 420}};
        }
    }

    static int durationMs(String id) {
        int ms = 0;
        for (int[] seg : pattern(id)) ms += seg[1];
        return ms;
    }

    /** 16-битный моно-звук на полной громкости. */
    static short[] build(String id) {
        boolean decay = "bell".equals(id);
        int[][] pat = pattern(id);
        short[] out = new short[(int) ((long) durationMs(id) * RATE / 1000)];
        int pos = 0;
        for (int[] seg : pat) {
            int n = Math.min(out.length - pos, seg[1] * RATE / 1000);
            int fade = Math.min(n / 2, RATE / 200);
            for (int i = 0; i < n && seg[0] > 0; i++) {
                double ph = 2 * Math.PI * seg[0] * i / RATE;
                // Основной тон плюс гармоники, слегка «прижатые» к потолку: так сигнал слышнее в шумном зале.
                double v = Math.tanh(2.2 * (Math.sin(ph) + 0.35 * Math.sin(2 * ph) + 0.2 * Math.sin(3 * ph)));
                double env = Math.min(1.0, Math.min(i, n - 1 - i) / (double) Math.max(1, fade));
                if (decay) env *= Math.exp(-3.5 * i / n);
                out[pos + i] = (short) Math.round(v * env * 32000);
            }
            pos += n;
        }
        return out;
    }
}
