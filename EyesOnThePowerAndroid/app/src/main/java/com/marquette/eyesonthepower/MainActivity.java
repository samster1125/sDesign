package com.marquette.eyesonthepower;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Main dashboard for Eyes On The Power.
 *
 * Pick a source (Simulation or Bluetooth), tap Connect, and an
 * acquisition thread starts pinging the Pi.  Each request gets back
 * one packet from Sam's firmware: min / avg / max temperature plus an
 * optional 160x120 thermal image, exactly the format defined in
 * validate.cpp.
 *
 * Author: Danny Ryazanov - ELEN 4998 Senior Design : E1 - Marquette
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "EyesOnPower";

    // Source mode constants
    private static final int SRC_SIM = 0;
    private static final int SRC_BT  = 1;

    // Alarm states
    private static final int ST_OFFLINE  = 0;
    private static final int ST_NORMAL   = 1;
    private static final int ST_WARNING  = 2;
    private static final int ST_CRITICAL = 3;

    private static final int REQ_BT_PERMS = 101;

    // ---- views ----
    private RadioGroup sourceGroup;
    private RadioButton simRadio, btRadio;
    private Button btnConnect, btnRefresh;
    private CheckBox chkImage;
    private EditText editWarn, editCrit;
    private ThermalView thermalView;
    private TrendView trendView;
    private TextView statusBadge, statusDetail;
    private TextView lblFps, lblImageState;
    private TextView eventLog;
    private TextView valMin, valAvg, valMax, valPeak;

    // ---- state ----
    private FrameSource activeSource;
    private Thread acquisitionThread;
    private volatile boolean running = false;
    private volatile boolean wantImage = true;
    private final Handler ui = new Handler(Looper.getMainLooper());

    /** Hottest max we've seen since the most recent Connect. */
    private float sessionPeakF = Float.NaN;

    // alarm persistence counters
    private int currentState   = ST_OFFLINE;
    private int candidateState = ST_OFFLINE;
    private int candidateCount = 0;

    // FPS bookkeeping
    private long lastFpsTickMs = 0;
    private int  framesSinceTick = 0;

    /** Time the session started, for x-axis on the trend graph. */
    private long sessionStartMs = 0;

    private final SimpleDateFormat logFmt =
            new SimpleDateFormat("HH:mm:ss", Locale.US);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        wireListeners();

        trendView.setThresholds(Const.DEFAULT_WARN_F, Const.DEFAULT_CRIT_F);
        refreshSources();
        appendLog("System ready - select a source and press Connect.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAcquisition();
    }

    // ----------------------------------------------------------------
    // View binding
    // ----------------------------------------------------------------
    private void bindViews() {
        sourceGroup   = findViewById(R.id.source_group);
        simRadio      = findViewById(R.id.source_sim);
        btRadio       = findViewById(R.id.source_bt);
        btnConnect    = findViewById(R.id.btn_connect);
        btnRefresh    = findViewById(R.id.btn_refresh);
        chkImage      = findViewById(R.id.chk_image);
        editWarn      = findViewById(R.id.edit_warn);
        editCrit      = findViewById(R.id.edit_crit);
        thermalView   = findViewById(R.id.thermal_view);
        trendView     = findViewById(R.id.trend_view);
        statusBadge   = findViewById(R.id.status_badge);
        statusDetail  = findViewById(R.id.status_detail);
        lblFps        = findViewById(R.id.lbl_fps);
        lblImageState = findViewById(R.id.lbl_image_state);
        eventLog      = findViewById(R.id.event_log);
        valMin        = findViewById(R.id.val_min);
        valAvg        = findViewById(R.id.val_avg);
        valMax        = findViewById(R.id.val_max);
        valPeak       = findViewById(R.id.val_peak);
        eventLog.setMovementMethod(new ScrollingMovementMethod());
    }

    private void wireListeners() {
        btnRefresh.setOnClickListener(v -> {
            refreshSources();
            appendLog("Sources refreshed.");
        });

        btnConnect.setOnClickListener(v -> {
            if (running) stopAcquisition();
            else         startAcquisitionForSelected();
        });

        chkImage.setOnCheckedChangeListener((v, checked) -> {
            wantImage = checked;
            lblImageState.setText(checked
                    ? R.string.image_on : R.string.image_off);
            if (!checked) {
                thermalView.clearImage();
            }
        });
    }


    // ----------------------------------------------------------------
    // Source detection - greys out radios that aren't available right now
    // ----------------------------------------------------------------
    private void refreshSources() {
        simRadio.setEnabled(true);
        simRadio.setText(R.string.source_sim);

        BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
        boolean btHere = false;
        int paired = 0;
        if (ba != null && ba.isEnabled() && hasBluetoothPerm()) {
            try {
                Set<BluetoothDevice> bonded = ba.getBondedDevices();
                if (bonded != null) {
                    paired = bonded.size();
                    btHere = paired > 0;
                }
            } catch (SecurityException se) {
                btHere = false;
            }
        }
        btRadio.setEnabled(btHere);
        btRadio.setText(btHere
                ? "Bluetooth (" + paired + " paired)"
                : getString(R.string.source_bt_off));

        if (btRadio.isChecked() && !btRadio.isEnabled()) {
            simRadio.setChecked(true);
        }
    }

    private boolean hasBluetoothPerm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }


    // ----------------------------------------------------------------
    // Connect / Disconnect
    // ----------------------------------------------------------------
    private void startAcquisitionForSelected() {
        int checked = sourceGroup.getCheckedRadioButtonId();

        if (checked == R.id.source_sim) {
            launchSource(new SimulatorSource(), "Simulation");

        } else if (checked == R.id.source_bt) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && !hasBluetoothPerm()) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN
                        }, REQ_BT_PERMS);
                return;
            }
            showBluetoothPicker();
        }
    }

    private void launchSource(FrameSource src, String label) {
        activeSource = src;
        try {
            src.open();
        } catch (Exception e) {
            Log.e(TAG, "open failed", e);
            toast("Could not open " + label + ": " + e.getMessage());
            appendLog("FAILED to open " + label + " - " + e.getMessage());
            activeSource = null;
            return;
        }

        sessionPeakF    = Float.NaN;
        currentState    = ST_OFFLINE;
        candidateState  = ST_OFFLINE;
        candidateCount  = 0;
        framesSinceTick = 0;
        sessionStartMs  = SystemClock.elapsedRealtime();
        lastFpsTickMs   = sessionStartMs;
        trendView.clear();
        thermalView.clearImage();
        setReadouts(Float.NaN, Float.NaN, Float.NaN);

        running = true;
        btnConnect.setText(R.string.disconnect);
        appendLog("Connected to " + label + ".");

        acquisitionThread = new Thread(this::acquisitionLoop, "thermal-acquire");
        acquisitionThread.start();
    }

    private void stopAcquisition() {
        running = false;
        if (acquisitionThread != null) {
            try { acquisitionThread.join(500); } catch (InterruptedException ignored) {}
            acquisitionThread = null;
        }
        if (activeSource != null) {
            try { activeSource.close(); } catch (Exception ignored) {}
            activeSource = null;
        }
        ui.post(() -> {
            btnConnect.setText(R.string.connect);
            setStatus(ST_OFFLINE);
            statusDetail.setText(R.string.not_connected_detail);
            lblFps.setText("FPS: --");
            thermalView.clearImage();
            appendLog("Disconnected.");
        });
    }


    // ----------------------------------------------------------------
    // Bluetooth paired-device picker
    // ----------------------------------------------------------------
    @SuppressWarnings("MissingPermission")
    private void showBluetoothPicker() {
        BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
        if (ba == null || !ba.isEnabled()) {
            toast("Bluetooth is off - turn it on and run Refresh.");
            return;
        }

        Set<BluetoothDevice> bonded;
        try { bonded = ba.getBondedDevices(); }
        catch (SecurityException se) {
            toast("Bluetooth permission denied.");
            return;
        }
        if (bonded == null || bonded.isEmpty()) {
            toast("No paired devices - pair the Pi in Android Settings first.");
            return;
        }

        final List<BluetoothDevice> devs = new ArrayList<>(bonded);
        final String[] labels = new String[devs.size()];
        for (int i = 0; i < devs.size(); i++) {
            BluetoothDevice d = devs.get(i);
            String name;
            try { name = d.getName(); }
            catch (SecurityException se) { name = null; }
            if (name == null || name.isEmpty()) name = d.getAddress();
            labels[i] = name + "\n" + d.getAddress();
        }

        new AlertDialog.Builder(this)
                .setTitle("Pick the Pi")
                .setItems(labels, (dialog, which) -> {
                    BluetoothDevice picked = devs.get(which);
                    launchSource(new BluetoothSource(picked),
                            "Bluetooth (" + labels[which].split("\n")[0] + ")");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT_PERMS) {
            boolean ok = grantResults.length > 0;
            for (int g : grantResults) {
                if (g != PackageManager.PERMISSION_GRANTED) ok = false;
            }
            if (ok) {
                refreshSources();
                showBluetoothPicker();
            } else {
                toast("Bluetooth permission is required to connect.");
            }
        }
    }


    // ----------------------------------------------------------------
    // Acquisition thread - off the UI thread, posts results back
    // ----------------------------------------------------------------
    private void acquisitionLoop() {
        FrameSource src = activeSource;
        if (src == null) return;

        while (running) {
            long iterStart = SystemClock.elapsedRealtime();
            boolean wantImg = wantImage;

            PiPacket pkt;
            try {
                pkt = src.requestPacket(wantImg);
            } catch (Exception e) {
                Log.e(TAG, "request failed", e);
                final String msg = e.getMessage();
                ui.post(() -> {
                    appendLog("Read error: " + msg);
                    stopAcquisition();
                });
                return;
            }

            framesSinceTick++;
            final Bitmap bmp = (pkt.hasImage)
                    ? Colormap.toBitmap(pkt.image)
                    : null;
            final PiPacket finalPkt = pkt;
            ui.post(() -> applyPacket(finalPkt, bmp));

            // Throttle the polling.  The simulator already sleeps, so
            // this only really matters for the Bluetooth source.
            if (src instanceof BluetoothSource) {
                long elapsed = SystemClock.elapsedRealtime() - iterStart;
                if (elapsed < Const.POLL_INTERVAL_MS) {
                    try { Thread.sleep(Const.POLL_INTERVAL_MS - elapsed); }
                    catch (InterruptedException ie) { return; }
                }
            }
        }
    }


    // ----------------------------------------------------------------
    // UI-thread packet handler
    // ----------------------------------------------------------------
    private void applyPacket(PiPacket pkt, Bitmap bmp) {
        float minF = pkt.minF();
        float avgF = pkt.avgF();
        float maxF = pkt.maxF();

        // 1. update thermal image (or clear if image wasn't requested)
        if (pkt.hasImage && bmp != null) {
            thermalView.update(bmp, maxF);
        } else if (!wantImage) {
            // user toggled image off - leave whatever's there alone
        }

        // 2. update readouts + session peak
        if (Float.isNaN(sessionPeakF) || maxF > sessionPeakF) {
            sessionPeakF = maxF;
        }
        setReadouts(minF, avgF, maxF);

        // 3. trend graph - one max-temp sample per packet
        float t = (SystemClock.elapsedRealtime() - sessionStartMs) / 1000f;
        trendView.addSample(t, maxF);

        // 4. FPS once a second
        long now = SystemClock.elapsedRealtime();
        if (now - lastFpsTickMs >= 1000) {
            float dt = (now - lastFpsTickMs) / 1000f;
            float fps = framesSinceTick / dt;
            lblFps.setText(String.format(Locale.US, "FPS: %.1f", fps));
            framesSinceTick = 0;
            lastFpsTickMs = now;
        }

        // 5. read thresholds from the EditTexts (cheap to reparse)
        float warn = parseFloat(editWarn.getText().toString(), Const.DEFAULT_WARN_F);
        float crit = parseFloat(editCrit.getText().toString(), Const.DEFAULT_CRIT_F);
        trendView.setThresholds(warn, crit);

        // 6. alarm state machine on the max temperature
        int proposed;
        if (maxF >= crit)      proposed = ST_CRITICAL;
        else if (maxF >= warn) proposed = ST_WARNING;
        else                   proposed = ST_NORMAL;

        if (proposed == candidateState) {
            candidateCount++;
        } else {
            candidateState = proposed;
            candidateCount = 1;
        }

        if (candidateCount >= Const.ALARM_PERSISTENCE_FRAMES
                && candidateState != currentState) {
            int prev = currentState;
            currentState = candidateState;
            setStatus(currentState);
            statusDetail.setText(String.format(Locale.US,
                    "Max %.1f °F   Avg %.1f °F   Min %.1f °F",
                    maxF, avgF, minF));
            if (currentState == ST_WARNING || currentState == ST_CRITICAL) {
                appendLog(String.format(Locale.US,
                        "ALARM: %s (max %.1f °F)",
                        stateName(currentState), maxF));
            } else if (prev != ST_OFFLINE) {
                appendLog("Cleared - back to NORMAL.");
            } else {
                statusDetail.setText("Live - temperatures nominal.");
            }
        } else if (currentState != ST_OFFLINE) {
            statusDetail.setText(String.format(Locale.US,
                    "Max %.1f °F   Avg %.1f °F   Min %.1f °F",
                    maxF, avgF, minF));
        }
    }

    private void setReadouts(float minF, float avgF, float maxF) {
        valMin.setText(fmt(minF));
        valAvg.setText(fmt(avgF));
        valMax.setText(fmt(maxF));
        valPeak.setText(fmt(sessionPeakF));
    }

    private void setStatus(int state) {
        switch (state) {
            case ST_NORMAL:
                statusBadge.setText(R.string.status_normal);
                statusBadge.setBackgroundResource(R.drawable.bg_status_normal);
                break;
            case ST_WARNING:
                statusBadge.setText(R.string.status_warning);
                statusBadge.setBackgroundResource(R.drawable.bg_status_warning);
                break;
            case ST_CRITICAL:
                statusBadge.setText(R.string.status_critical);
                statusBadge.setBackgroundResource(R.drawable.bg_status_critical);
                break;
            default:
                statusBadge.setText(R.string.status_offline);
                statusBadge.setBackgroundResource(R.drawable.bg_status_offline);
                break;
        }
    }

    private static String stateName(int s) {
        switch (s) {
            case ST_NORMAL:   return "NORMAL";
            case ST_WARNING:  return "WARNING";
            case ST_CRITICAL: return "CRITICAL";
            default:          return "OFFLINE";
        }
    }


    // ----------------------------------------------------------------
    // small helpers
    // ----------------------------------------------------------------
    private static String fmt(float v) {
        if (Float.isNaN(v)) return "--";
        return String.format(Locale.US, "%.1f", v);
    }

    private static float parseFloat(String s, float fallback) {
        try { return Float.parseFloat(s.trim()); }
        catch (Exception e) { return fallback; }
    }

    private void appendLog(String msg) {
        String line = "[" + logFmt.format(new Date()) + "] " + msg + "\n";
        eventLog.append(line);
        if (eventLog.length() > 6000) {
            CharSequence cs = eventLog.getText();
            eventLog.setText(cs.subSequence(cs.length() - 4000, cs.length()));
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
