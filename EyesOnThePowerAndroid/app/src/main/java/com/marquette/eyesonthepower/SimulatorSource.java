package com.marquette.eyesonthepower;

import android.os.SystemClock;

import java.util.Random;

/**
 * Offline simulator that emits packets in the exact same shape Sam's
 * Pi sends them: one global min / avg / max plus an optional 160x120
 * u8 image payload.
 *
 * No 3-phase ROIs, no temperature grid - just a single hot spot that
 * slowly drifts around the frame so the dashboard has something
 * believable to display when no Pi is connected.
 *
 * Images are generated as 0..255 brightness values, same convention
 * Sam uses on the Pi (already-normalized luminance, not raw kelvin).
 */
public class SimulatorSource implements FrameSource {

    private final Random rng = new Random();
    private final long t0 = SystemClock.elapsedRealtime();

    /** Cached image buffer reused across frames. */
    private final byte[] img = new byte[Const.IMG_BYTES];

    @Override public void open()  { /* nothing to do */ }
    @Override public void close() { /* nothing to release */ }

    @Override
    public PiPacket requestPacket(boolean wantImage) {
        // Throttle so the simulator runs at roughly the polling rate
        // we'd see talking to the real Pi.
        try { Thread.sleep(Const.POLL_INTERVAL_MS); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

        float t = (SystemClock.elapsedRealtime() - t0) / 1000.0f;

        // Hot spot drifts on a slow Lissajous so the trend graph has
        // motion.  Temperature peaks near 90 C, ambient around 26 C.
        float hotC = 60.0f + 30.0f * (float) Math.sin(t * 0.35f);
        float ambC = 26.0f;

        // Center of the hot blob.
        float cx = Const.IMG_COLS * (0.5f + 0.30f * (float) Math.sin(t * 0.25f));
        float cy = Const.IMG_ROWS * (0.5f + 0.30f * (float) Math.cos(t * 0.18f));
        float sigmaX = 22f;
        float sigmaY = 28f;

        float frameMinC = Float.POSITIVE_INFINITY;
        float frameMaxC = Float.NEGATIVE_INFINITY;
        double sumC = 0;

        // Render a temperature field, track stats, then convert each
        // pixel to a u8 brightness for the image payload.
        for (int y = 0; y < Const.IMG_ROWS; y++) {
            for (int x = 0; x < Const.IMG_COLS; x++) {
                float dx = (x - cx) / sigmaX;
                float dy = (y - cy) / sigmaY;
                float blob = (hotC - ambC) * (float) Math.exp(-(dx*dx + dy*dy) * 0.5f);
                float c = ambC + blob + (float) (rng.nextGaussian() * 0.3f);

                if (c < frameMinC) frameMinC = c;
                if (c > frameMaxC) frameMaxC = c;
                sumC += c;

                // Map ambient..peak (~26..90 C) onto 0..255 the same
                // way Sam's image bytes are already 0..255 brightness.
                float norm = (c - 25.0f) / (95.0f - 25.0f);
                if (norm < 0) norm = 0;
                if (norm > 1) norm = 1;
                img[y * Const.IMG_COLS + x] = (byte) (norm * 255f);
            }
        }

        float avgC = (float) (sumC / (Const.IMG_COLS * Const.IMG_ROWS));

        return new PiPacket(
                Const.cToRaw(frameMinC),
                Const.cToRaw(frameMaxC),
                Const.cToRaw(avgC),
                wantImage,
                wantImage ? img.clone() : null,
                System.currentTimeMillis()
        );
    }
}
