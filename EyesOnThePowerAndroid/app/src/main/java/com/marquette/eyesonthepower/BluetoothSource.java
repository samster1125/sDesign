package com.marquette.eyesonthepower;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Bluetooth Classic / SPP source.
 *
 * Mirrors the collectorThread loop in Sam's validate.cpp: write a
 * request byte (0xA stats / 0xB stats+image), then read bytes off the
 * stream until PiPacketParser produces a complete packet.  One packet
 * per requestPacket() call.
 *
 * The Pi advertises a standard SPP service (UUID 00001101...).  We
 * connect with the well-known UUID once the user has paired the device
 * in Android Settings and picked it from the dashboard's picker.
 */
public class BluetoothSource implements FrameSource {

    /** Standard SPP service UUID. */
    public static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BluetoothDevice device;
    private BluetoothSocket socket;
    private InputStream  in;
    private OutputStream out;
    private final PiPacketParser parser = new PiPacketParser();
    private final byte[] readBuf = new byte[4096];

    public BluetoothSource(BluetoothDevice device) {
        this.device = device;
    }

    @Override
    public void open() throws IOException {
        // createRfcommSocketToServiceRecord requires BLUETOOTH_CONNECT
        // permission on Android 12+.  MainActivity prompts for it before
        // ever instantiating this source.
        socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
        socket.connect();
        in  = socket.getInputStream();
        out = socket.getOutputStream();
        parser.reset();
    }

    @Override
    public void close() {
        try { if (in  != null) in.close();  } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        in = null;
        out = null;
        socket = null;
    }

    @Override
    public PiPacket requestPacket(boolean wantImage) throws IOException {
        if (out == null || in == null) throw new IOException("Not connected");

        // 1. Send the request byte.  Same as writeAll(fd, &start, 1) on Pi.
        byte req = wantImage ? Const.REQ_IMAGE : Const.REQ_STATS;
        out.write(req);
        out.flush();

        // 2. Read until parser yields one packet.  The Pi is a little
        //    chatty so we may have to swallow a few iterations of bytes.
        long deadline = System.currentTimeMillis() + 5000;     // 5s timeout
        while (true) {
            PiPacket pkt = parser.takePacket();
            if (pkt != null) return pkt;

            if (System.currentTimeMillis() > deadline) {
                throw new IOException("Timed out waiting for Pi packet");
            }

            int n = in.read(readBuf);
            if (n < 0) throw new IOException("Bluetooth stream closed");
            if (n > 0) parser.feed(readBuf, 0, n);
        }
    }

    public BluetoothDevice getDevice() { return device; }
}
