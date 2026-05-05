package com.marquette.eyesonthepower;

/**
 * Streaming byte parser for Sam's Pi packet format.
 *
 * Mirrors validate.cpp: bytes get fed in as they arrive, the parser
 * looks for a 0xFF start byte, reads the big-endian length field, and
 * waits until the full packet is buffered before validating the stop
 * byte and producing a PiPacket.  Junk bytes between packets are
 * skipped automatically (same behavior as searchForStart + buf.erase
 * in validate.cpp).
 *
 * Not thread-safe - feed and takePacket should be called from the same
 * thread (the acquisition thread).
 */
public class PiPacketParser {

    /** Hard cap on the rolling buffer so malformed streams can't OOM us. */
    private static final int MAX_BUFFER = Const.IMAGE_PACKET_SIZE * 4;

    private byte[] buf = new byte[Const.IMAGE_PACKET_SIZE * 2];
    private int len = 0;     // bytes currently in buf

    /** Push raw bytes from the socket into the parser. */
    public void feed(byte[] data, int offset, int n) {
        if (n <= 0) return;
        ensureCapacity(len + n);
        System.arraycopy(data, offset, buf, len, n);
        len += n;
        if (len > MAX_BUFFER) {
            // pathological - drop the tail and keep the most recent bytes
            int keep = MAX_BUFFER / 2;
            System.arraycopy(buf, len - keep, buf, 0, keep);
            len = keep;
        }
    }

    /**
     * Try to extract one complete packet from the buffer.
     *
     * @return a PiPacket if a valid one was found, otherwise null.  Call
     *         repeatedly to drain multiple back-to-back packets.
     */
    public PiPacket takePacket() {
        while (true) {
            // 1. Find the start byte.
            int start = -1;
            for (int i = 0; i < len; i++) {
                if (buf[i] == Const.START_BYTE) { start = i; break; }
            }
            if (start < 0) {
                // No start byte anywhere - drop everything.
                len = 0;
                return null;
            }
            if (start > 0) {
                // Drop junk before the start byte.
                System.arraycopy(buf, start, buf, 0, len - start);
                len -= start;
            }

            // Need at least 3 bytes to read the length field.
            if (len < 3) return null;

            int packetSize = ((buf[1] & 0xFF) << 8) | (buf[2] & 0xFF);

            // Sam's validate.cpp only accepts the two valid sizes.
            if (packetSize != Const.BASE_PACKET_SIZE
                    && packetSize != Const.IMAGE_PACKET_SIZE) {
                // Bad length - skip the start byte and try again.
                System.arraycopy(buf, 1, buf, 0, len - 1);
                len -= 1;
                continue;
            }

            if (len < packetSize) return null;       // wait for more bytes

            // 2. Validate the stop byte at the end of the packet.
            if ((buf[packetSize - 1] & 0xFF) != (Const.STOP_BYTE & 0xFF)) {
                // Bad stop byte - drop the start, keep scanning.
                System.arraycopy(buf, 1, buf, 0, len - 1);
                len -= 1;
                continue;
            }

            // 3. Decode the header fields (all u16 are big-endian).
            int rawMin   = u16(buf, 3);
            int rawMax   = u16(buf, 5);
            int rawAvg   = u16(buf, 7);
            int flagImg  = buf[9] & 0xFF;

            byte[] img = null;
            if (flagImg == 1 && packetSize == Const.IMAGE_PACKET_SIZE) {
                img = new byte[Const.IMG_BYTES];
                System.arraycopy(buf, 10, img, 0, Const.IMG_BYTES);
            }

            PiPacket pkt = new PiPacket(rawMin, rawMax, rawAvg,
                    img != null, img, System.currentTimeMillis());

            // 4. Slide remaining bytes down for the next packet.
            int leftover = len - packetSize;
            if (leftover > 0) {
                System.arraycopy(buf, packetSize, buf, 0, leftover);
            }
            len = leftover;

            return pkt;
        }
    }

    /** Drop everything we've buffered so far. */
    public void reset() { len = 0; }

    private static int u16(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    private void ensureCapacity(int needed) {
        if (needed <= buf.length) return;
        int cap = buf.length;
        while (cap < needed) cap *= 2;
        byte[] nb = new byte[cap];
        System.arraycopy(buf, 0, nb, 0, len);
        buf = nb;
    }
}
