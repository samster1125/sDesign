package com.marquette.eyesonthepower;

/**
 * Common interface for anything that produces Pi-style packets - either
 * the live Bluetooth source or the offline simulator.
 *
 * The acquisition thread does:
 *
 *     src.open();
 *     while (running) {
 *         PiPacket p = src.requestPacket(wantImage);
 *         ...post to UI thread...
 *     }
 *     src.close();
 */
public interface FrameSource {

    /** Open the underlying transport.  Throws if the source is unavailable. */
    void open() throws Exception;

    /** Close any held resources.  Safe to call repeatedly. */
    void close();

    /**
     * Send a request to the Pi (or, for the sim, just synthesize the
     * next frame) and block until exactly one valid packet comes back.
     *
     * @param wantImage true to request 0xB (stats + image), false for 0xA.
     * @return a fully decoded packet, never null.
     * @throws Exception on transport errors / timeouts.
     */
    PiPacket requestPacket(boolean wantImage) throws Exception;
}
