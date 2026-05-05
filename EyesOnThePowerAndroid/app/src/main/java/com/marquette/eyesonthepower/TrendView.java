package com.marquette.eyesonthepower;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight single-line trend graph for the maximum temperature.
 *
 * The Pi's packet only carries one global max value per frame, so this
 * view plots a single polyline plus dashed warning / critical reference
 * lines.  Rolling 2-minute window, no charting library so the apk
 * stays small.
 */
public class TrendView extends View {

    private final List<float[]> samples = new ArrayList<>();   // (t, max°F)

    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lblPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint warnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint critPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float warnF = Const.DEFAULT_WARN_F;
    private float critF = Const.DEFAULT_CRIT_F;

    public TrendView(Context ctx, AttributeSet a) {
        super(ctx, a);

        axisPaint.setColor(0xFF476079);
        axisPaint.setStrokeWidth(1f);

        gridPaint.setColor(0xFFB9D6EC);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{6f, 4f}, 0f));

        lblPaint.setColor(0xFF476079);
        lblPaint.setTextSize(22f);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3f);
        linePaint.setColor(0xFF1F4E8C);                // accent blue

        warnPaint.setColor(0xFFE0A800);
        warnPaint.setStrokeWidth(2f);
        warnPaint.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{8f, 6f}, 0f));

        critPaint.setColor(0xFFC0392B);
        critPaint.setStrokeWidth(2f);
        critPaint.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{8f, 6f}, 0f));
    }

    public void setThresholds(float warn, float crit) {
        this.warnF = warn;
        this.critF = crit;
        invalidate();
    }

    /** Push a new (time, maxF) sample.  nowSec is seconds since session start. */
    public void addSample(float nowSec, float maxF) {
        samples.add(new float[]{nowSec, maxF});
        float cutoff = nowSec - Const.TREND_HISTORY_SEC;
        while (!samples.isEmpty() && samples.get(0)[0] < cutoff) {
            samples.remove(0);
        }
        invalidate();
    }

    public void clear() {
        samples.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);

        int w = getWidth();
        int h = getHeight();
        float padL = 60f, padR = 8f, padT = 12f, padB = 28f;
        float plotW = w - padL - padR;
        float plotH = h - padT - padB;

        // Y range from data + thresholds.
        float yMin = Math.min(warnF - 10f, critF - 10f);
        float yMax = Math.max(warnF + 10f, critF + 10f);
        boolean haveData = false;
        for (float[] p : samples) {
            if (p[1] < yMin) yMin = p[1];
            if (p[1] > yMax) yMax = p[1];
            haveData = true;
        }
        yMin -= 4f;
        yMax += 4f;
        if (yMax - yMin < 10f) yMax = yMin + 10f;

        // X axis: most recent sample on the right, history extends left.
        float nowSec = haveData ? samples.get(samples.size() - 1)[0] : 0f;
        float xLeftSec = nowSec - Const.TREND_HISTORY_SEC;

        // Horizontal grid + Y labels (in °F)
        for (int i = 0; i <= 4; i++) {
            float yv = yMin + (yMax - yMin) * (i / 4f);
            float py = padT + plotH - (yv - yMin) / (yMax - yMin) * plotH;
            c.drawLine(padL, py, padL + plotW, py, gridPaint);
            c.drawText(String.format(Locale.US, "%.0f", yv),
                       4f, py + 8f, lblPaint);
        }

        // Threshold lines
        drawHLine(c, warnF, padL, plotW, padT, plotH, yMin, yMax, warnPaint);
        drawHLine(c, critF, padL, plotW, padT, plotH, yMin, yMax, critPaint);

        // Trend polyline
        if (samples.size() >= 2) {
            Path path = new Path();
            for (int j = 0; j < samples.size(); j++) {
                float t = samples.get(j)[0];
                float v = samples.get(j)[1];
                float fx = (t - xLeftSec) / Const.TREND_HISTORY_SEC;
                float px = padL + fx * plotW;
                float py = padT + plotH - (v - yMin) / (yMax - yMin) * plotH;
                if (j == 0) path.moveTo(px, py);
                else        path.lineTo(px, py);
            }
            c.drawPath(path, linePaint);
        }

        // Axis frame
        c.drawLine(padL, padT, padL, padT + plotH, axisPaint);
        c.drawLine(padL, padT + plotH, padL + plotW, padT + plotH, axisPaint);

        // Legend chip
        c.drawText("Max temperature (°F)", padL + 8f, padT + 18f, lblPaint);
    }

    private static void drawHLine(Canvas c, float y, float padL, float plotW,
                                  float padT, float plotH, float yMin, float yMax,
                                  Paint p) {
        if (y < yMin || y > yMax) return;
        float py = padT + plotH - (y - yMin) / (yMax - yMin) * plotH;
        c.drawLine(padL, py, padL + plotW, py, p);
    }
}
