package com.ruslan.fitnesscouch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Простой линейный график: значения по порядку, подписи минимума и максимума, необязательная линия цели. */
final class LineChart extends View {
    private final float[] v;
    private final float goal;
    private final String unit;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG),
            grid = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    LineChart(Context c, float[] values, float goal, String unit, int accent, int muted) {
        super(c);
        this.v = values;
        this.goal = goal;
        this.unit = unit;
        d = c.getResources().getDisplayMetrics().density;
        line.setColor(accent);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.5f * d);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        dot.setColor(accent);
        grid.setColor(muted);
        grid.setAlpha(70);
        grid.setStrokeWidth(d);
        text.setColor(muted);
        text.setTextSize(11 * c.getResources().getDisplayMetrics().scaledDensity);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (v.length == 0) return;
        float min = v[0], max = v[0];
        for (float x : v) { min = Math.min(min, x); max = Math.max(max, x); }
        if (goal > 0) { min = Math.min(min, goal); max = Math.max(max, goal); }
        if (max - min < 0.5f) { max += 0.5f; min -= 0.5f; }

        float left = 8 * d, right = getWidth() - 44 * d, top = 12 * d, bottom = getHeight() - 12 * d;
        float h = bottom - top, w = right - left;

        c.drawLine(left, top, right, top, grid);
        c.drawLine(left, bottom, right, bottom, grid);
        c.drawText(Fmt.n(max) + unit, right + 6 * d, top + 4 * d, text);
        c.drawText(Fmt.n(min) + unit, right + 6 * d, bottom + 4 * d, text);
        if (goal > 0) {
            float gy = bottom - (goal - min) / (max - min) * h;
            line.setAlpha(110);
            for (float x = left; x < right; x += 10 * d) c.drawLine(x, gy, Math.min(x + 5 * d, right), gy, line);
            line.setAlpha(255);
        }

        Path path = new Path();
        for (int i = 0; i < v.length; i++) {
            float x = v.length == 1 ? left + w / 2 : left + w * i / (v.length - 1);
            float y = bottom - (v[i] - min) / (max - min) * h;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
            if (v.length <= 24 || i == v.length - 1) c.drawCircle(x, y, (i == v.length - 1 ? 4.5f : 3f) * d, dot);
        }
        c.drawPath(path, line);
    }
}
