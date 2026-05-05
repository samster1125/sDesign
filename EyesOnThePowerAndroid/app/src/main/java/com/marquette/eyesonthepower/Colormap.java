package com.marquette.eyesonthepower;

import android.graphics.Bitmap;

/**
 * Maps the 0..255 brightness bytes Sam ships in his image payload onto
 * a heat-color RGB Bitmap.
 *
 * The ramp is taken straight from heatColor() in validate.cpp so the
 * phone shows the same colors as Sam's PPM output: dark blue/purple at
 * the cold end, red/orange in the middle, white-yellow at the top.
 */
public final class Colormap {
    private Colormap() {}

    /** Build a Bitmap (ARGB_8888) from a Sam-style 160x120 u8 image. */
    public static Bitmap toBitmap(byte[] img) {
        if (img == null || img.length != Const.IMG_BYTES) return null;
        int[] pixels = new int[Const.IMG_BYTES];
        for (int i = 0; i < Const.IMG_BYTES; i++) {
            float norm = (img[i] & 0xFF) / 255.0f;
            int rgb = heatColor(norm);
            pixels[i] = 0xFF000000 | rgb;
        }
        Bitmap bmp = Bitmap.createBitmap(Const.IMG_COLS, Const.IMG_ROWS,
                Bitmap.Config.ARGB_8888);
        bmp.setPixels(pixels, 0, Const.IMG_COLS, 0, 0,
                Const.IMG_COLS, Const.IMG_ROWS);
        return bmp;
    }

    /** Same ramp as heatColor() in validate.cpp, returns packed 0xRRGGBB. */
    private static int heatColor(float norm) {
        int r, g, b;
        if (norm < 0.42f) {
            float t = norm / 0.42f;
            r = (int) (8  + t * 55);
            g = 0;
            b = (int) (25 + t * 165);
        } else if (norm < 0.75f) {
            float t = (norm - 0.42f) / 0.33f;
            r = (int) (63  + t * 192);
            g = (int) (t * 55);
            b = (int) (190 - t * 190);
        } else if (norm < 0.95f) {
            float t = (norm - 0.75f) / 0.20f;
            r = 255;
            g = (int) (55 + t * 200);
            b = 0;
        } else {
            float t = (norm - 0.95f) / 0.05f;
            r = 255;
            g = 255;
            b = (int) (t * 100);
        }
        if (r < 0) r = 0; if (r > 255) r = 255;
        if (g < 0) g = 0; if (g > 255) g = 255;
        if (b < 0) b = 0; if (b > 255) b = 255;
        return (r << 16) | (g << 8) | b;
    }
}
