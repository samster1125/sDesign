package com.marquette.eyesonthepower;

/**
 * Project-wide constants for the Eyes On The Power Android app.
 *
 * Most of this file mirrors the values in Sam's validate.hpp / .cpp on
 * the Pi side so the wire format stays in sync.  If Sam ever changes a
 * field width or the image size on the Pi, fix it here too.
 */
public final class Const {
    private Const() {}

    // ----------------------------------------------------------------
    // Wire protocol - matches validate.cpp on the Pi
    // ----------------------------------------------------------------

    /** Start-of-packet sentinel (Sam's "ack" / 0xFF). */
    public static final byte START_BYTE = (byte) 0xFF;

    /** Packet terminator. */
    public static final byte STOP_BYTE  = (byte) 0xAA;

    /** Request byte: stats only (min/max/avg, no image payload). */
    public static final byte REQ_STATS  = (byte) 0xA;

    /** Request byte: stats + thermal image payload. */
    public static final byte REQ_IMAGE  = (byte) 0xB;

    /** Lepton 3.5 native frame size as Sam streams it. */
    public static final int IMG_COLS = 160;
    public static final int IMG_ROWS = 120;
    public static final int IMG_BYTES = IMG_COLS * IMG_ROWS;

    /**
     * Header layout (multi-byte fields are big-endian on the wire).  This
     * is exactly what parsePacket() in validate.cpp reads, in order.
     *
     *   [0]      ack / start byte (0xFF)
     *   [1..2]   total packet length (BE)
     *   [3..4]   raw min temperature (kelvin * 100, BE)
     *   [5..6]   raw max temperature (kelvin * 100, BE)
     *   [7..8]   raw avg temperature (kelvin * 100, BE)
     *   [9]      flagImage (0 = stats only, 1 = image follows)
     *   [10..]   image payload, IMG_BYTES bytes, only if flagImage == 1
     *   [last]   stop byte (0xAA)
     */
    public static final int BASE_PACKET_SIZE  = 11;                    // no image
    public static final int IMAGE_PACKET_SIZE = BASE_PACKET_SIZE + IMG_BYTES;

    // ----------------------------------------------------------------
    // Display / dashboard defaults
    // ----------------------------------------------------------------

    /** Default alarm thresholds (degrees Fahrenheit). */
    public static final float DEFAULT_WARN_F = 140.0f;
    public static final float DEFAULT_CRIT_F = 176.0f;

    /** Threshold has to hold this many frames before the badge flips. */
    public static final int ALARM_PERSISTENCE_FRAMES = 3;

    /** Trend graph window length in seconds. */
    public static final int TREND_HISTORY_SEC = 120;

    /** How often the acquisition loop pings the Pi (milliseconds). */
    public static final int POLL_INTERVAL_MS = 250;

    // ----------------------------------------------------------------
    // Helpers - same math as kelvinToF in validate.cpp
    // ----------------------------------------------------------------

    /** Convert Sam's raw u16 (kelvin * 100) to degrees Fahrenheit. */
    public static float rawToF(int raw) {
        float kelvin = (raw & 0xFFFF) / 100.0f;
        return (kelvin - 273.15f) * 1.8f + 32.0f;
    }

    /** Convert raw u16 to degrees Celsius (used by the simulator). */
    public static float rawToC(int raw) {
        return (raw & 0xFFFF) / 100.0f - 273.15f;
    }

    /** Convert °C back into the raw u16 the way the Pi would encode it. */
    public static int cToRaw(float c) {
        int v = Math.round((c + 273.15f) * 100.0f);
        if (v < 0) v = 0;
        if (v > 0xFFFF) v = 0xFFFF;
        return v;
    }
}
