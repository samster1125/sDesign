package com.marquette.eyesonthepower;

/**
 * One thermal frame's worth of data, parsed off the wire from the Pi.
 *
 * Mirrors the Packet struct on Sam's side: a single global min / avg /
 * max temperature and an optional 160x120 image payload.  No 3-phase
 * fields - the Pi computes whole-image stats only.
 */
public class PiPacket {

    /** Raw u16 values straight off the wire (kelvin * 100). */
    public final int rawMin;
    public final int rawMax;
    public final int rawAvg;

    /** True if the Pi included an image payload in this packet. */
    public final boolean hasImage;

    /**
     * Image bytes in row-major order, length = IMG_COLS * IMG_ROWS, each
     * byte 0..255 (already normalized on the Pi side).  null if hasImage
     * is false.
     */
    public final byte[] image;

    /** Phone-side timestamp the packet finished parsing, in ms. */
    public final long receivedAtMs;

    public PiPacket(int rawMin, int rawMax, int rawAvg,
                    boolean hasImage, byte[] image,
                    long receivedAtMs) {
        this.rawMin = rawMin;
        this.rawMax = rawMax;
        this.rawAvg = rawAvg;
        this.hasImage = hasImage;
        this.image = image;
        this.receivedAtMs = receivedAtMs;
    }

    public float minF() { return Const.rawToF(rawMin); }
    public float maxF() { return Const.rawToF(rawMax); }
    public float avgF() { return Const.rawToF(rawAvg); }
}
