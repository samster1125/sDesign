# Eyes On The Power - Android Dashboard

Senior design project for ELEN 4998 : E1 at Marquette University.
Sponsored by Dynamic Ratings.

This Android app talks to the Pi over Bluetooth Classic / SPP and
shows the live thermal readings exactly as Sam's firmware emits them:
one global min / avg / max temperature, plus an optional 160x120
thermal image.

## Team
- Samuel Winn, COEN
- Kaden Keller, ELEN
- Danny Ryazanov, ELEN  *(this app)*
- Hubert Mendrycki, ELEN

## How to open it

1. Install Android Studio (Hedgehog or newer).
2. **File -> Open** and pick the `EyesOnThePowerAndroid` folder.
3. Let Gradle sync.  First sync downloads Gradle 8.7, AGP 8.5,
   AndroidX, and Material.  Give it a couple minutes.
4. Plug in a phone with USB debugging on (or start an emulator) and
   hit the green **Run** arrow.

> Min SDK is **26** (Android 8.0).  Target SDK is **34**.

## Sources

- **Simulation** - generates a fake Pi packet (same shape Sam sends:
  one min/max/avg + a 160x120 image) so the dashboard works without
  hardware.
- **Bluetooth** - SPP/RFCOMM to whichever paired device you pick.
  The Pi shows up here once you've paired it in Android Settings.

Sources that aren't currently available are greyed out.  Hit
**Refresh** any time to re-scan.

## Wire protocol

This is the format Sam ships in `validate.cpp`.  The Android side
matches it byte-for-byte in `PiPacketParser.java`.

**Request bytes** (phone -> Pi):
| Byte | Meaning |
|------|---------|
| `0x0A` | send stats only (no image payload) |
| `0x0B` | send stats + 160x120 image |

**Response packet** (Pi -> phone, big-endian for u16):

| Offset | Size | Field | Notes |
|--------|------|-------|-------|
| 0      | 1    | start (`0xFF`) | a.k.a. "ack" |
| 1..2   | 2    | total length | `11` no-image, `19211` with image |
| 3..4   | 2    | min temperature | u16, kelvin x 100 |
| 5..6   | 2    | max temperature | u16, kelvin x 100 |
| 7..8   | 2    | avg temperature | u16, kelvin x 100 |
| 9      | 1    | flagImage | `1` if image follows |
| 10..   | 19200 | image | only if flagImage=1, u8 per pixel |
| last   | 1    | stop (`0xAA`) |

Temperature is decoded with the same formula as `kelvinToF` in
validate.cpp:

```
F = (raw / 100 - 273.15) * 1.8 + 32
```

The 19,200-byte image is laid out row-major, 160 columns x 120 rows,
each byte 0..255 (already-normalized brightness, not raw kelvin).
Sam's `heatColor` ramp is reproduced in `Colormap.java` so the phone
shows the same colors as the PPM files he writes on the Pi.

## App layout

```
app/src/main/java/com/marquette/eyesonthepower/
  MainActivity.java        - UI, source switching, alarm logic
  Const.java               - protocol bytes, sizes, °F helpers
  FrameSource.java         - common interface (open/close/requestPacket)
  SimulatorSource.java     - offline source, emits Pi-shaped packets
  BluetoothSource.java     - SPP source, writes 0xA/0xB and reads packets
  PiPacket.java            - parsed packet (min, max, avg, image)
  PiPacketParser.java      - streaming parser, mirrors validate.cpp
  Colormap.java            - heatColor() port -> ARGB Bitmap
  ThermalView.java         - paints the bitmap + max-temp overlay
  TrendView.java           - rolling 2-min line graph of max temp
```

## Notes for the demo

- Pair the Pi in Android Settings before launching the app - the
  picker only shows already-paired devices.
- The "Request image" checkbox flips between `0xA` and `0xB`.  Turn
  it off to save bandwidth if the link gets choppy; the readouts
  and trend will keep updating from the stats-only packets.
- Threshold boxes at the top right are live - tweak Warning /
  Critical mid-demo and the trend graph + alarm state machine pick
  up the new values on the next packet.

Developed by **Danny Ryazanov** for Marquette ELEN 4998 Senior
Design : E1 - Eyes On The Power.
