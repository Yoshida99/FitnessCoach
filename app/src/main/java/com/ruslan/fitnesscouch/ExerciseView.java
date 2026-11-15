package com.ruslan.fitnesscouch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

/** Анимированная иллюстрация упражнения: фигура плавно ходит между двумя крайними позами. */
final class ExerciseView extends View implements Figure.Pen {
    static final long CYCLE = 3200;

    private final Figure.Anim anim;
    private final float boost;
    private final int outline;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final long offset;
    private Canvas canvas;

    /** boost — во сколько раз утолщать линии (для маленьких превью); outline — цвет фона под фигурой. */
    ExerciseView(Context c, Figure.Anim anim, float boost, int outline) {
        super(c);
        this.anim = anim;
        this.boost = boost;
        this.outline = outline;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        offset = (long) (Math.random() * CYCLE);
    }

    /** Фаза 0…1: пауза, подъём, пауза, медленное опускание. */
    static float phase(long ms) {
        long t = ms % CYCLE;
        if (t < 350) return 0;
        if (t < 1550) return ease((t - 350) / 1200f);
        if (t < 1900) return 1;
        return 1 - ease((t - 1900) / 1300f);
    }

    static float ease(float x) { return (float) (1 - Math.cos(Math.PI * x)) / 2; }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = MeasureSpec.getMode(hSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(hSpec)
                : Math.round(w * Figure.H / Figure.W);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), sc = Math.min(w / Figure.W, h / Figure.H);
        canvas = c;
        Figure.outline = outline;
        c.save();
        c.translate((w - Figure.W * sc) / 2, (h - Figure.H * sc) / 2);
        c.scale(sc, sc);
        Figure.draw(anim, phase(SystemClock.uptimeMillis() + offset), this);
        c.restore();
        canvas = null;
        if (isShown()) postInvalidateOnAnimation();
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float w, int color) {
        stroke.setColor(color);
        stroke.setStrokeWidth(w * boost);
        canvas.drawLine(x1, y1, x2, y2, stroke);
    }

    @Override
    public void circle(float cx, float cy, float r, int f, int s, float sw) {
        float rr = f == Figure.outline ? r * boost : r;
        if (f != 0) {
            fill.setColor(f);
            canvas.drawCircle(cx, cy, rr, fill);
        }
        if (s != 0 && sw > 0) {
            stroke.setColor(s);
            stroke.setStrokeWidth(sw * boost);
            canvas.drawCircle(cx, cy, rr, stroke);
        }
    }

    @Override
    public void rect(float l, float t, float r, float b, float radius, int f) {
        fill.setColor(f);
        canvas.drawRoundRect(l, t, r, b, radius, radius, fill);
    }

    @Override
    public void poly(float[] xy, int f) {
        path.reset();
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) path.lineTo(xy[i], xy[i + 1]);
        path.close();
        fill.setColor(f);
        canvas.drawPath(path, fill);
    }
}
