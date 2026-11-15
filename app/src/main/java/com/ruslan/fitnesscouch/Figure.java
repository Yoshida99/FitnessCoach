package com.ruslan.fitnesscouch;

/**
 * Рисованная фигура атлета для анимаций упражнений. Чистая Java без Android:
 * поза задаётся углами суставов, рисование идёт через интерфейс Pen.
 *
 * Углы в градусах, абсолютные: 0 — вниз, 90 — вправо (лицом вперёд), 180 — вверх, −90 — назад.
 * Холст 200 × 130 условных единиц, пол на высоте GROUND.
 */
final class Figure {

    interface Pen {
        void line(float x1, float y1, float x2, float y2, float w, int color);

        void circle(float cx, float cy, float r, int fill, int stroke, float sw);

        void rect(float l, float t, float r, float b, float radius, int fill);

        void poly(float[] xy, int fill);
    }

    static final int NEAR = 0xFFE4E5E8, FAR = 0xFF6F737D, GOLD = 0xFFD9B45A, GOLD_DARK = 0xFF5E4F2A,
            PROP = 0xFF2A2C33, PROP_EDGE = 0xFF41444E, FLOOR = 0xFF30323A, CABLE = 0xFF9A8758;
    /** Тёмный контур вокруг ближних рук и ног, чтобы они не сливались с корпусом. */
    static int outline = 0xFF111215;
    static final float W = 200, H = 130, GROUND = 118;
    static final float TORSO = 34, NECK = 3, HEAD = 7.5f, UARM = 17, FARM = 16, THIGH = 22, SHIN = 21, FOOT = 8,
            SW = 11, HW = 6.5f;

    /** Индексы параметров позы. */
    static final int RX = 0, RY = 1, TOR = 2, TS = 3, HS = 4, AU = 5, AF = 6, AUS = 7, AFS = 8, BU = 9, BF = 10,
            BUS = 11, BFS = 12, LT = 13, LS = 14, LTS = 15, MT = 16, MS = 17, MTS = 18, FA = 19, FB = 20, N = 21;
    /** Какая точка тела закреплена на месте: таз, голеностоп ближней ноги или колено. */
    static final int HIP = 0, ANKLE = 1, KNEE = 2;

    /** Поза. Дальняя рука и нога по умолчанию повторяют ближние. */
    static final class P {
        final float[] v = new float[N];

        P(float x, float y) {
            v[RX] = x;
            v[RY] = y;
            v[TOR] = 180;
            v[TS] = v[HS] = 1;
            v[AUS] = v[AFS] = v[BUS] = v[BFS] = v[LTS] = v[MTS] = 1;
            v[FA] = 90;
            v[BU] = v[BF] = v[MT] = v[MS] = v[FB] = Float.NaN;
        }

        P torso(float a) { v[TOR] = a; return this; }
        P torsoS(float s) { v[TS] = s; return this; }
        P headS(float s) { v[HS] = s; return this; }
        P arms(float u, float f) { v[AU] = u; v[AF] = f; return this; }
        P armsS(float u, float f) { v[AUS] = v[BUS] = u; v[AFS] = v[BFS] = f; return this; }
        P armB(float u, float f) { v[BU] = u; v[BF] = f; return this; }
        P legs(float t, float s) { v[LT] = t; v[LS] = s; return this; }
        P legsS(float t) { v[LTS] = v[MTS] = t; return this; }
        P legB(float t, float s) { v[MT] = t; v[MS] = s; return this; }
        P foot(float a) { v[FA] = a; return this; }
        P footB(float a) { v[FB] = a; return this; }

        float[] done() {
            float[] o = v.clone();
            if (Float.isNaN(o[BU])) { o[BU] = o[AU]; o[BF] = o[AF]; }
            if (Float.isNaN(o[MT])) { o[MT] = o[LT]; o[MS] = o[LS]; }
            if (Float.isNaN(o[FB])) o[FB] = o[FA];
            return o;
        }
    }

    /** Упражнение: две крайние позы, вид (сбоку или спереди) и снаряды. */
    static class Anim {
        final boolean front, legs;
        final int anchor;
        final float[] a, b;

        Anim(boolean front, int anchor, P a, P b) { this(front, anchor, true, a, b); }

        Anim(boolean front, int anchor, boolean legs, P a, P b) {
            this.front = front;
            this.anchor = anchor;
            this.legs = legs;
            this.a = a.done();
            this.b = b.done();
        }

        /** Тренажёры и скамьи — за телом. */
        void props(Geo g, Pen p) { }

        /** Снаряд в дальней руке (вид сбоку) — за телом. */
        void gearBack(Geo g, Pen p) { }

        /** Снаряды — перед телом. */
        void gear(Geo g, Pen p) { }
    }

    /** Рассчитанные точки тела. */
    static final class Geo {
        float tor;
        float[] hip, sh, head, hipA, hipB, shA, shB, elA, wrA, elB, wrB, knA, anA, toA, knB, anB, toB;
    }

    static float[] vec(float deg, float len) {
        double r = Math.toRadians(deg);
        return new float[]{(float) Math.sin(r) * len, (float) Math.cos(r) * len};
    }

    static float[] pt(float x, float y) { return new float[]{x, y}; }

    static float[] add(float[] a, float[] b) { return new float[]{a[0] + b[0], a[1] + b[1]}; }

    static float[] lerp(float[] a, float[] b, float t) {
        float[] o = new float[a.length];
        for (int i = 0; i < a.length; i++) o[i] = a[i] + (b[i] - a[i]) * t;
        return o;
    }

    static Geo geo(Anim an, float[] v) {
        Geo g = new Geo();
        boolean fr = an.front;
        float hw = fr ? HW : 0, sw = fr ? SW : 0, m = fr ? -1 : 1;
        float[] th = vec(v[LT], THIGH * v[LTS]), sh = vec(v[LS], SHIN);
        float hx, hy;
        if (an.anchor == ANKLE) {
            hx = v[RX] - sh[0] - th[0] - hw;
            hy = v[RY] - sh[1] - th[1];
        } else if (an.anchor == KNEE) {
            hx = v[RX] - th[0] - hw;
            hy = v[RY] - th[1];
        } else {
            hx = v[RX];
            hy = v[RY];
        }
        g.tor = v[TOR];
        g.hip = pt(hx, hy);
        g.sh = add(g.hip, vec(v[TOR], TORSO * v[TS]));
        g.head = add(g.sh, vec(v[TOR], (NECK + HEAD) * v[HS]));
        g.hipA = pt(hx + hw, hy);
        g.hipB = pt(hx - hw, hy);
        g.shA = pt(g.sh[0] + sw, g.sh[1]);
        g.shB = pt(g.sh[0] - sw, g.sh[1]);
        g.elA = add(g.shA, vec(v[AU], UARM * v[AUS]));
        g.wrA = add(g.elA, vec(v[AF], FARM * v[AFS]));
        g.elB = add(g.shB, vec(m * v[BU], UARM * v[BUS]));
        g.wrB = add(g.elB, vec(m * v[BF], FARM * v[BFS]));
        g.knA = add(g.hipA, th);
        g.anA = add(g.knA, sh);
        g.knB = add(g.hipB, vec(m * v[MT], THIGH * v[MTS]));
        g.anB = add(g.knB, vec(m * v[MS], SHIN));
        if (fr) {
            g.toA = add(g.anA, pt(3.5f, 1.5f));
            g.toB = add(g.anB, pt(-3.5f, 1.5f));
        } else {
            g.toA = add(g.anA, vec(v[LS] + v[FA], FOOT));
            g.toB = add(g.anB, vec(v[MS] + v[FB], FOOT));
        }
        return g;
    }

    /** Нарисовать упражнение в фазе t: 0 — исходная поза, 1 — конечная. */
    static void draw(Anim an, float t, Pen p) {
        Geo g = geo(an, lerp(an.a, an.b, t));
        boolean fr = an.front;
        p.line(4, GROUND + 2, W - 4, GROUND + 2, 1.6f, FLOOR);
        an.props(g, p);
        int far = fr ? NEAR : FAR;
        if (an.legs) leg(p, g.hipB, g.knB, g.anB, g.toB, far);
        if (!fr) arm(p, g.shB, g.elB, g.wrB, far);
        an.gearBack(g, p);
        if (fr) {
            p.poly(new float[]{g.shA[0], g.shA[1], g.shB[0], g.shB[1], g.hipB[0], g.hipB[1], g.hipA[0], g.hipA[1]}, NEAR);
            p.line(g.shA[0], g.shA[1], g.shB[0], g.shB[1], 7, NEAR);
            p.line(g.hipA[0], g.hipA[1], g.hipB[0], g.hipB[1], 7, NEAR);
        } else {
            p.line(g.hip[0], g.hip[1], g.sh[0], g.sh[1], 11, NEAR);
        }
        p.line(g.sh[0], g.sh[1], g.head[0], g.head[1], 4.5f, NEAR);
        p.circle(g.head[0], g.head[1], HEAD, NEAR, 0, 0);
        if (an.legs) leg(p, g.hipA, g.knA, g.anA, g.toA, NEAR);
        if (fr) arm(p, g.shB, g.elB, g.wrB, NEAR);
        arm(p, g.shA, g.elA, g.wrA, NEAR);
        an.gear(g, p);
    }

    static void leg(Pen p, float[] h, float[] k, float[] a, float[] t, int c) {
        if (c == NEAR) {
            p.line(h[0], h[1], k[0], k[1], 10, outline);
            p.line(k[0], k[1], a[0], a[1], 8.5f, outline);
        }
        p.line(h[0], h[1], k[0], k[1], 7.5f, c);
        p.line(k[0], k[1], a[0], a[1], 6, c);
        p.line(a[0], a[1], t[0], t[1], 3.5f, c);
    }

    static void arm(Pen p, float[] s, float[] e, float[] w, int c) {
        if (c == NEAR) {
            p.line(s[0], s[1], e[0], e[1], 8, outline);
            p.line(e[0], e[1], w[0], w[1], 7.3f, outline);
            p.circle(w[0], w[1], 4, outline, 0, 0);
        }
        p.line(s[0], s[1], e[0], e[1], 5.5f, c);
        p.line(e[0], e[1], w[0], w[1], 4.8f, c);
        p.circle(w[0], w[1], 2.7f, c, 0, 0);
    }

    // ── Снаряды ─────────────────────────────────────────────────────────────

    /** Блин штанги, вид сбоку. */
    static void plate(Pen p, float[] c, float r) {
        p.circle(c[0], c[1], r, GOLD_DARK, GOLD, 2);
        p.circle(c[0], c[1], Math.max(1.6f, r * 0.2f), GOLD, 0, 0);
    }

    /** Гантель, вид с торца. */
    static void db(Pen p, float[] c) {
        p.circle(c[0], c[1], 4.6f, GOLD_DARK, GOLD, 1.6f);
    }

    /** Гантель, вид спереди (гриф горизонтально). */
    static void dbSide(Pen p, float[] c) {
        p.line(c[0] - 6, c[1], c[0] + 6, c[1], 2, GOLD);
        p.rect(c[0] - 9.5f, c[1] - 4.5f, c[0] - 5, c[1] + 4.5f, 1.2f, GOLD);
        p.rect(c[0] + 5, c[1] - 4.5f, c[0] + 9.5f, c[1] + 4.5f, 1.2f, GOLD);
    }

    static void cable(Pen p, float[] from, float x, float y) {
        p.line(from[0], from[1], x, y, 1.1f, CABLE);
        p.circle(x, y, 3.2f, PROP_EDGE, GOLD, 1.2f);
    }

    static void bench(Pen p, float l, float r, float top) {
        p.line(l + 8, top + 5, l + 8, GROUND, 3, PROP);
        p.line(r - 8, top + 5, r - 8, GROUND, 3, PROP);
        p.rect(l, top, r, top + 6, 2, PROP_EDGE);
    }

    /** Спинка вдоль корпуса: со стороны спины, от таза на длину len. */
    static void backPad(Pen p, Geo g, float len) {
        float[] back = vec(g.tor + 90, 7.5f);
        float[] s = add(g.hip, back), e = add(s, vec(g.tor, len));
        p.line(s[0], s[1], e[0], e[1], 6, PROP_EDGE);
    }
}
