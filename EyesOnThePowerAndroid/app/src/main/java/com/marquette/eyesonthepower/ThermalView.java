package com.marquette.eyesonthepower;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/**
 * Custom view that paints the most recent thermal image letter-boxed
 * to fit the available space, plus a small max-temperature overlay.
 *
 * No 3-phase ROIs - the Pi only reports whole-image min/avg/max so
 * there's nothing to box up.
 */
public class ThermalView extends View {

    private Bitmap bitmap;
    private float maxF = Float.NaN;

    private final Paint imagePaint  = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint labelPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect dstRect = new Rect();

    public ThermalView(Context ctx, AttributeSet a) {
        super(ctx, a);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(34f);
        labelPaint.setFakeBoldText(true);

        shadowPaint.setColor(Color.BLACK);
        shadowPaint.setTextSize(34f);
        shadowPaint.setFakeBoldText(true);

        emptyPaint.setColor(0xFF7F8C8D);
        emptyPaint.setTextSize(28f);
        emptyPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** Push a new image + the matching max temp.  Either may be null/NaN. */
    public void update(Bitmap newBitmap, float newMaxF) {
        if (this.bitmap != null && this.bitmap != newBitmap) {
            this.bitmap.recycle();
        }
        this.bitmap = newBitmap;
        this.maxF = newMaxF;
        invalidate();
    }

    /** Drop the current image (e.g. on disconnect). */
    public void clearImage() {
        if (bitmap != null) bitmap.recycle();
        bitmap = null;
        maxF = Float.NaN;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int vw = getWidth();
        int vh = getHeight();
        if (vw <= 0 || vh <= 0) return;

        if (bitmap == null) {
            c.drawText("No image yet - tap Connect to start streaming.",
                    vw / 2f, vh / 2f, emptyPaint);
            return;
        }

        // Letter-box preserving aspect ratio.
        float scale = Math.min(vw / (float) bitmap.getWidth(),
                                vh / (float) bitmap.getHeight());
        int dw = (int) (bitmap.getWidth()  * scale);
        int dh = (int) (bitmap.getHeight() * scale);
        int x0 = (vw - dw) / 2;
        int y0 = (vh - dh) / 2;
        dstRect.set(x0, y0, x0 + dw, y0 + dh);
        c.drawBitmap(bitmap, null, dstRect, imagePaint);

        if (!Float.isNaN(maxF)) {
            String label = String.format(Locale.US, "MAX %.1f °F", maxF);
            float lx = x0 + 12f;
            float ly = y0 + 38f;
            c.drawText(label, lx + 2f, ly + 2f, shadowPaint);
            c.drawText(label, lx, ly, labelPaint);
        }
    }
}
