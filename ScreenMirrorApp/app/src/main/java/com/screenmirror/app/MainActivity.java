package com.screenmirror.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Main activity — improved:
 *  - No login / no admin credentials
 *  - Battery broadcast → sends to server every 30 s
 *  - Quality and FPS controls exposed in UI
 *  - Connection status with animated indicator
 *  - Robust permission + MediaProjection flow
 */
public class MainActivity extends AppCompatActivity implements SocketManager.Listener {

    private static final int RC_PROJECTION  = 100;
    private static final int RC_NOTIF       = 101;

    private MediaProjectionManager projectionManager;
    private SharedPreferences prefs;
    private Handler mainHandler;

    // UI
    private View        dotConnected;
    private TextView    tvStatus, tvDeviceInfo, tvBattery;
    private Button      btnConnect, btnDisconnect, btnStartShare;
    private EditText    etServerUrl;
    private LinearLayout permBanner, controlsPanel;
    private Button      btnApprove, btnDeny;
    private TextView    tvPermDesc;
    private SeekBar     sbQuality, sbFps;
    private TextView    tvQualityVal, tvFpsVal;
    private ProgressBar pbConnecting;

    // Device info
    private String deviceId, deviceName;
    private int    batteryLevel = -1;

    // Saved projection permission (reuse without asking again)
    private static int    savedResultCode = -1;
    private static Intent savedResultData = null;

    // Flag to prevent double permission launch
    private boolean projectionLaunching = false;

    // Periodic tasks
    private final Handler periodicHandler = new Handler(Looper.getMainLooper());
    private final Runnable batteryTask = new Runnable() {
        @Override public void run() {
            if (SocketManager.getInstance().isConnected()) {
                SocketManager.getInstance().sendBatteryUpdate(batteryLevel);
            }
            periodicHandler.postDelayed(this, 30_000);
        }
    };

    private BroadcastReceiver batteryReceiver;

    // ─────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler        = new Handler(Looper.getMainLooper());
        prefs              = getSharedPreferences("sm_prefs", MODE_PRIVATE);
        projectionManager  = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        bindViews();
        setupDeviceInfo();
        setupSeekBars();
        registerBatteryReceiver();
        requestNotifPermission();

        SocketManager.getInstance().setListener(this);

        String savedUrl = prefs.getString("server_url", "http://192.168.1.100:3000");
        etServerUrl.setText(savedUrl);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SocketManager.getInstance().setListener(this);
        refreshConnectionUI(SocketManager.getInstance().isConnected());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        periodicHandler.removeCallbacksAndMessages(null);
        if (batteryReceiver != null) {
            try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) {}
        }
        SocketManager.getInstance().setListener(null);
    }

    // ─────────────────────────────────────────
    //  View binding
    // ─────────────────────────────────────────

    private void bindViews() {
        dotConnected  = findViewById(R.id.dot_connected);
        tvStatus      = findViewById(R.id.tv_status);
        tvDeviceInfo  = findViewById(R.id.tv_device_info);
        tvBattery     = findViewById(R.id.tv_battery);
        btnConnect    = findViewById(R.id.btn_connect);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        etServerUrl   = findViewById(R.id.et_server_url);
        permBanner    = findViewById(R.id.perm_banner);
        controlsPanel = findViewById(R.id.controls_panel);
        btnApprove    = findViewById(R.id.btn_approve);
        btnDeny       = findViewById(R.id.btn_deny);
        tvPermDesc    = findViewById(R.id.tv_perm_desc);
        sbQuality     = findViewById(R.id.sb_quality);
        sbFps         = findViewById(R.id.sb_fps);
        tvQualityVal  = findViewById(R.id.tv_quality_val);
        tvFpsVal      = findViewById(R.id.tv_fps_val);
        pbConnecting  = findViewById(R.id.pb_connecting);
        btnStartShare = findViewById(R.id.btn_start_share);

        btnConnect.setOnClickListener(v -> connectToServer());
        btnDisconnect.setOnClickListener(v -> disconnectFromServer());
        btnStartShare.setOnClickListener(v -> startScreenShareFromApp());
    }

    // ─────────────────────────────────────────
    //  Device info
    // ─────────────────────────────────────────

    private void setupDeviceInfo() {
        deviceId   = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        deviceName = Build.MANUFACTURER + " " + Build.MODEL;
        tvDeviceInfo.setText(deviceName + " · Android " + Build.VERSION.RELEASE);
    }

    private void registerBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                batteryLevel = (int)(level * 100f / scale);
                tvBattery.setText("🔋 " + batteryLevel + "%");
            }
        };
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    // ─────────────────────────────────────────
    //  SeekBars (quality + FPS)
    // ─────────────────────────────────────────

    private void setupSeekBars() {
        SocketManager sm = SocketManager.getInstance();

        sbQuality.setMax(85);
        sbQuality.setProgress(sm.getCurrentQuality() - 10); // offset: 10..95
        tvQualityVal.setText(sm.getCurrentQuality() + "%");

        sbFps.setMax(29);
        sbFps.setProgress(sm.getCurrentFps() - 1); // offset: 1..30
        tvFpsVal.setText(sm.getCurrentFps() + " fps");

        sbQuality.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean user) {
                int q = p + 10;
                tvQualityVal.setText(q + "%");
                sm.setCurrentQuality(q);
                prefs.edit().putInt("quality", q).apply();
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });

        sbFps.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean user) {
                int f = p + 1;
                tvFpsVal.setText(f + " fps");
                sm.setCurrentFps(f);
                prefs.edit().putInt("fps", f).apply();
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Restore saved values
        sbQuality.setProgress(prefs.getInt("quality", 30) - 10);
        sbFps.setProgress(prefs.getInt("fps", 30) - 1);
    }

    // ─────────────────────────────────────────
    //  Connect / Disconnect
    // ─────────────────────────────────────────

    private void connectToServer() {
        String url = etServerUrl.getText().toString().trim();
        if (url.isEmpty()) { toast("Enter server URL"); return; }
        if (!url.startsWith("http")) { toast("URL must start with http://"); return; }

        prefs.edit().putString("server_url", url).apply();

        pbConnecting.setVisibility(View.VISIBLE);
        btnConnect.setEnabled(false);
        setStatus("Connecting…", "#FFA726");

        SocketManager.getInstance().setListener(this);
        SocketManager.getInstance().connect(url);
    }

    private void disconnectFromServer() {
        periodicHandler.removeCallbacks(batteryTask);
        SocketManager.getInstance().disconnect();

        // Stop service
        Intent si = new Intent(this, ScreenCaptureService.class);
        si.setAction(ScreenCaptureService.ACTION_STOP);
        startService(si);

        refreshConnectionUI(false);
        permBanner.setVisibility(View.GONE);
        controlsPanel.setVisibility(View.GONE);
        btnStartShare.setVisibility(View.GONE);
    }

    private void startScreenShareFromApp() {
        SocketManager.getInstance().sendPermissionResponse(true);
        if (savedResultCode != -1 && savedResultData != null) {
            launchCaptureService(savedResultCode, savedResultData);
        } else if (!projectionLaunching) {
            projectionLaunching = true;
            Intent intent = projectionManager.createScreenCaptureIntent();
            startActivityForResult(intent, RC_PROJECTION);
        }
    }

    // ─────────────────────────────────────────
    //  SocketManager.Listener
    // ─────────────────────────────────────────

    @Override
    public void onConnected() {
        mainHandler.post(() -> {
            pbConnecting.setVisibility(View.GONE);
            refreshConnectionUI(true);
            setStatus("Connected ✓", "#4CAF50");
            controlsPanel.setVisibility(View.VISIBLE);
            btnStartShare.setVisibility(View.VISIBLE);

            // Register device with full info
            SocketManager.getInstance().register(
                    deviceId, deviceName, Build.VERSION.RELEASE,
                    Build.MANUFACTURER, Build.MODEL,
                    getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels,
                    batteryLevel);

            // Start periodic battery updates
            periodicHandler.removeCallbacks(batteryTask);
            periodicHandler.postDelayed(batteryTask, 30_000);
        });
    }

    @Override
    public void onDisconnected() {
        mainHandler.post(() -> {
            setStatus("Disconnected — reconnecting…", "#FF7043");
            dotConnected.setBackgroundResource(R.drawable.dot_red);
        });
    }

    @Override
    public void onConnectionError(String error) {
        mainHandler.post(() -> {
            pbConnecting.setVisibility(View.GONE);
            btnConnect.setEnabled(true);
            setStatus("Error: " + error, "#F44336");
            dotConnected.setBackgroundResource(R.drawable.dot_red);
        });
    }

    @Override
    public void onPermissionRequest(String requestId, String adminName) {
        mainHandler.post(() -> showPermBanner(adminName));
    }

    @Override
    public void onStartCapture() {
        mainHandler.post(() -> {
            if (savedResultCode != -1 && savedResultData != null) {
                launchCaptureService(savedResultCode, savedResultData);
            } else if (!projectionLaunching) {
                projectionLaunching = true;
                Intent intent = projectionManager.createScreenCaptureIntent();
                startActivityForResult(intent, RC_PROJECTION);
            }
        });
    }

    @Override
    public void onStopCapture() {
        mainHandler.post(() -> {
            toast("Screen sharing stopped by admin");
            Intent si = new Intent(this, ScreenCaptureService.class);
            si.setAction(ScreenCaptureService.ACTION_STOP);
            startService(si);
            setStatus("Connected — sharing stopped", "#4CAF50");
        });
    }

    @Override
    public void onQualityChange(int quality, int fps) {
        mainHandler.post(() -> {
            sbQuality.setProgress(quality - 10);
            sbFps.setProgress(fps - 1);
            toast("Quality updated by admin: " + quality + "% @ " + fps + "fps");
        });
    }

    // ─────────────────────────────────────────
    //  Permission banner
    // ─────────────────────────────────────────

    private void showPermBanner(String adminName) {
        tvPermDesc.setText("\"" + adminName + "\" wants to view your screen in real-time.");
        permBanner.setVisibility(View.VISIBLE);

        btnApprove.setOnClickListener(v -> {
            permBanner.setVisibility(View.GONE);
            SocketManager.getInstance().sendPermissionResponse(true);
            setStatus("Permission granted — waiting…", "#FF9800");
        });

        btnDeny.setOnClickListener(v -> {
            permBanner.setVisibility(View.GONE);
            SocketManager.getInstance().sendPermissionResponse(false);
            toast("Permission denied");
        });
    }

    // ─────────────────────────────────────────
    //  MediaProjection result
    // ─────────────────────────────────────────

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_PROJECTION) {
            projectionLaunching = false;
            if (resultCode == RESULT_OK && data != null) {
                // Save for reuse — no need to ask again while app is alive
                savedResultCode = resultCode;
                savedResultData = data;
                launchCaptureService(resultCode, data);
            } else {
                SocketManager.getInstance().sendPermissionResponse(false);
                toast("System permission denied");
                setStatus("Connected — permission denied", "#9E9E9E");
            }
        }
    }

    private void launchCaptureService(int resultCode, Intent resultData) {
        Intent si = new Intent(this, ScreenCaptureService.class);
        si.setAction(ScreenCaptureService.ACTION_START);
        si.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
        si.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, resultData);
        si.putExtra(ScreenCaptureService.EXTRA_QUALITY, SocketManager.getInstance().getCurrentQuality());
        si.putExtra(ScreenCaptureService.EXTRA_FPS,     SocketManager.getInstance().getCurrentFps());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(si);
        } else {
            startService(si);
        }
        setStatus("🔴 Streaming…", "#F44336");
        btnStartShare.setVisibility(View.GONE);
    }

    // ─────────────────────────────────────────
    //  UI helpers
    // ─────────────────────────────────────────

    private void refreshConnectionUI(boolean connected) {
        btnConnect.setEnabled(!connected);
        btnDisconnect.setEnabled(connected);
        etServerUrl.setEnabled(!connected);
        dotConnected.setBackgroundResource(connected ? R.drawable.dot_green : R.drawable.dot_gray);
        if (!connected) {
            setStatus("Disconnected", "#9E9E9E");
            controlsPanel.setVisibility(View.GONE);
            pbConnecting.setVisibility(View.GONE);
        }
    }

    private void setStatus(String text, String hex) {
        tvStatus.setText(text);
        try { tvStatus.setTextColor(android.graphics.Color.parseColor(hex)); }
        catch (Exception ignored) {}
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ─────────────────────────────────────────
    //  Permissions
    // ─────────────────────────────────────────

    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, RC_NOTIF);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(req, p, g);
    }
}
