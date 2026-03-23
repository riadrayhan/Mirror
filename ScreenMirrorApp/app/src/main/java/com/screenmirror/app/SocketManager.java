package com.screenmirror.app;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * Singleton Socket.io manager.
 *
 * Features:
 *  - Auto-reconnect with infinite retries
 *  - Adaptive quality / FPS control from server
 *  - Connection state callbacks
 *  - Thread-safe emit queue
 */
public class SocketManager {

    private static final String TAG = "SocketManager";
    private static volatile SocketManager instance;

    private Socket socket;
    private Listener listener;

    // Current capture settings (can be changed by admin via server)
    private volatile int currentQuality = 30;   // JPEG quality 1-100
    private volatile int currentFps     = 30;   // target FPS
    private volatile boolean isCapturing = false;

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onConnectionError(String error);
        void onPermissionRequest(String requestId, String adminName);
        void onStartCapture();
        void onStopCapture();
        void onQualityChange(int quality, int fps);
    }

    private SocketManager() {}

    public static SocketManager getInstance() {
        if (instance == null) {
            synchronized (SocketManager.class) {
                if (instance == null) instance = new SocketManager();
            }
        }
        return instance;
    }

    // ─────────────────────────────────────────
    //  Connect
    // ─────────────────────────────────────────

    public synchronized void connect(String serverUrl) {
        if (socket != null && socket.connected()) return;
        if (socket != null) { socket.off(); socket.disconnect(); }

        try {
            IO.Options opts = new IO.Options();
            opts.path              = "/socket.io/";
            opts.reconnection      = true;
            opts.reconnectionDelay = 1500;
            opts.reconnectionDelayMax = 5000;
            opts.reconnectionAttempts = Integer.MAX_VALUE;
            opts.timeout           = 30000;
            opts.transports        = new String[]{"polling", "websocket"};
            opts.forceNew          = true;

            socket = IO.socket(serverUrl + "/device", opts);
            attachEvents();
            socket.connect();
            Log.d(TAG, "Connecting → " + serverUrl);

        } catch (URISyntaxException e) {
            Log.e(TAG, "Bad URL: " + serverUrl);
            if (listener != null) listener.onConnectionError("Bad server URL");
        }
    }

    private void attachEvents() {
        socket.on(Socket.EVENT_CONNECT, args -> {
            Log.d(TAG, "Connected");
            if (listener != null) listener.onConnected();
        });

        socket.on(Socket.EVENT_DISCONNECT, args -> {
            Log.d(TAG, "Disconnected");
            if (listener != null) listener.onDisconnected();
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            String msg = args.length > 0 ? args[0].toString() : "unknown";
            Log.e(TAG, "Connection error: " + msg);
            if (listener != null) listener.onConnectionError(msg);
        });

        socket.on("permission_request", args -> {
            try {
                JSONObject d = (JSONObject) args[0];
                String reqId     = d.optString("requestId", "");
                String adminName = d.optString("adminName", "Admin");
                if (listener != null) listener.onPermissionRequest(reqId, adminName);
            } catch (Exception e) {
                Log.e(TAG, "permission_request parse: " + e.getMessage());
            }
        });

        socket.on("start_capture", args -> {
            isCapturing = true;
            if (listener != null) listener.onStartCapture();
        });

        socket.on("stop_capture", args -> {
            isCapturing = false;
            if (listener != null) listener.onStopCapture();
        });

        socket.on("quality_change", args -> {
            try {
                JSONObject d = (JSONObject) args[0];
                int q = d.optInt("quality", currentQuality);
                int f = d.optInt("fps",     currentFps);
                currentQuality = clamp(q, 10, 95);
                currentFps     = clamp(f, 1, 30);
                Log.d(TAG, "Quality change → q=" + currentQuality + " fps=" + currentFps);
                if (listener != null) listener.onQualityChange(currentQuality, currentFps);
            } catch (Exception e) {
                Log.e(TAG, "quality_change parse: " + e.getMessage());
            }
        });

        socket.on("registered", args -> {
            Log.d(TAG, "Registration confirmed by server");
        });
    }

    // ─────────────────────────────────────────
    //  Emit helpers
    // ─────────────────────────────────────────

    public void register(String deviceId, String deviceName, String androidVersion,
                         String manufacturer, String model,
                         int screenW, int screenH, int battery,
                         String userName, String userPhone, String userPayment) {
        if (!isConnected()) return;
        try {
            JSONObject d = new JSONObject();
            d.put("deviceId",       deviceId);
            d.put("deviceName",     deviceName);
            d.put("androidVersion", androidVersion);
            d.put("manufacturer",   manufacturer);
            d.put("model",          model);
            d.put("screenWidth",    screenW);
            d.put("screenHeight",   screenH);
            d.put("battery",        battery);
            d.put("userName",       userName);
            d.put("userPhone",      userPhone);
            d.put("userPayment",    userPayment);
            socket.emit("register", d);
        } catch (JSONException e) {
            Log.e(TAG, "register: " + e.getMessage());
        }
    }

    public void sendPermissionResponse(boolean approved) {
        if (!isConnected()) return;
        try {
            JSONObject d = new JSONObject();
            d.put("approved", approved);
            socket.emit("permission_response", d);
        } catch (JSONException e) {
            Log.e(TAG, "permission_response: " + e.getMessage());
        }
    }

    /**
     * Send a captured frame. Called from background thread — socket.emit is thread-safe.
     */
    public void sendFrame(String base64Jpeg, int width, int height, long timestamp) {
        if (!isConnected()) return;
        try {
            JSONObject d = new JSONObject();
            d.put("frame",     base64Jpeg);
            d.put("width",     width);
            d.put("height",    height);
            d.put("timestamp", timestamp);
            d.put("quality",   currentQuality);
            socket.emit("screen_frame", d);
        } catch (JSONException e) {
            Log.e(TAG, "sendFrame: " + e.getMessage());
        }
    }

    public void sendBatteryUpdate(int level) {
        if (!isConnected()) return;
        try {
            JSONObject d = new JSONObject();
            d.put("battery", level);
            socket.emit("device_info_update", d);
        } catch (JSONException ignored) {}
    }

    // ─────────────────────────────────────────
    //  Accessors
    // ─────────────────────────────────────────

    public boolean isConnected() { return socket != null && socket.connected(); }
    public int  getCurrentQuality() { return currentQuality; }
    public int  getCurrentFps()     { return currentFps; }
    public void setCurrentQuality(int q) { currentQuality = clamp(q, 10, 95); }
    public void setCurrentFps(int f)     { currentFps     = clamp(f, 1, 30); }

    public void setListener(Listener l) { this.listener = l; }

    public void disconnect() {
        isCapturing = false;
        if (socket != null) { socket.off(); socket.disconnect(); socket = null; }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
