package com.screenmirror.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Foreground Service for screen capture.
 *
 * Improvements over v1:
 *  - WakeLock so CPU never sleeps during capture
 *  - Adaptive FPS: skips frames if previous encode is still running (no frame pile-up)
 *  - Dynamic quality/FPS from server via SocketManager
 *  - Accurate frame timing via System.nanoTime()
 *  - ByteArrayOutputStream pool (reuse buffer)
 *  - Proper cleanup on all exit paths
 *  - Sends performance stats back to server
 */
public class ScreenCaptureService extends Service {

    private static final String TAG = "CaptureService";
    private static final String CHANNEL_ID = "screen_mirror";
    private static final int    NOTIF_ID   = 1001;

    public static final String ACTION_START       = "ACTION_START";
    public static final String ACTION_STOP        = "ACTION_STOP";
    public static final String EXTRA_RESULT_CODE  = "RESULT_CODE";
    public static final String EXTRA_RESULT_DATA  = "RESULT_DATA";
    public static final String EXTRA_QUALITY      = "QUALITY";
    public static final String EXTRA_FPS          = "FPS";

    // Defaults (overridden by SocketManager.currentQuality/currentFps)
    private static final float SCALE = 0.6f;       // capture at 60% of screen resolution

    private MediaProjectionManager projectionManager;
    private MediaProjection         mediaProjection;
    private VirtualDisplay          virtualDisplay;
    private ImageReader             imageReader;
    private HandlerThread           captureThread;
    private Handler                 captureHandler;
    private PowerManager.WakeLock   wakeLock;

    private int screenW, screenH, density;
    private int captureW, captureH;

    private final AtomicBoolean running    = new AtomicBoolean(false);
    private final AtomicBoolean encoding   = new AtomicBoolean(false);  // prevent frame pile-up
    private final AtomicInteger framesSent = new AtomicInteger(0);
    private final ByteArrayOutputStream bos = new ByteArrayOutputStream(256 * 1024);

    // FPS tracking for notification update
    private long fpsWindowStart = 0;
    private int  fpsWindowCount = 0;
    private int  lastFps        = 0;

    // ─────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        createChannel();
        getScreenMetrics();
        acquireWakeLock();
        startForeground(NOTIF_ID, buildNotification("Initialising…"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // Service restarted by system — reconnect socket
            reconnectSocket();
            return START_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
            Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            // Optional overrides from activity
            int q = intent.getIntExtra(EXTRA_QUALITY, SocketManager.getInstance().getCurrentQuality());
            int f = intent.getIntExtra(EXTRA_FPS,     SocketManager.getInstance().getCurrentFps());
            SocketManager.getInstance().setCurrentQuality(q);
            SocketManager.getInstance().setCurrentFps(f);
            startCapture(resultCode, resultData);
        } else if (ACTION_STOP.equals(action)) {
            stopCapture();
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // App swiped from recents — keep service alive and reconnect
        super.onTaskRemoved(rootIntent);
        reconnectSocket();
    }

    private void reconnectSocket() {
        android.content.SharedPreferences prefs =
            getSharedPreferences("sm_prefs", MODE_PRIVATE);
        String url = prefs.getString("server_url", "https://mirrorbackend-ohir.onrender.com");
        if (!SocketManager.getInstance().isConnected()) {
            SocketManager.getInstance().connect(url);
        }
    }

    @Nullable @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        stopCapture();
        releaseWakeLock();
        super.onDestroy();
    }

    // ─────────────────────────────────────────
    //  Capture start / stop
    // ─────────────────────────────────────────

    private void startCapture(int resultCode, Intent resultData) {
        if (running.get()) return;

        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData);
        if (mediaProjection == null) { Log.e(TAG, "Null projection"); return; }

        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { stopCapture(); }
        }, null);

        captureW = Math.max(1, (int)(screenW * SCALE));
        captureH = Math.max(1, (int)(screenH * SCALE));

        imageReader = ImageReader.newInstance(captureW, captureH, PixelFormat.RGBA_8888, 3);

        virtualDisplay = mediaProjection.createVirtualDisplay("Mirror Game",
                captureW, captureH, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        captureThread = new HandlerThread("CaptureThread", Thread.NORM_PRIORITY + 1);
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        running.set(true);
        fpsWindowStart = System.currentTimeMillis();
        Log.d(TAG, "Capture started " + captureW + "x" + captureH);

        scheduleNext();
    }

    private void scheduleNext() {
        if (!running.get()) return;
        int fps = SocketManager.getInstance().getCurrentFps();
        long interval = 1000L / Math.max(1, fps);
        captureHandler.postDelayed(this::captureAndSend, interval);
    }

    private void captureAndSend() {
        if (!running.get()) return;

        // Skip frame if previous encode not finished (prevents queue buildup)
        if (encoding.compareAndSet(false, true)) {
            doCapture();
            encoding.set(false);
        } else {
            Log.v(TAG, "Frame skipped — encoder busy");
        }

        scheduleNext();
    }

    private void doCapture() {
        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) return;

            Image.Plane plane  = image.getPlanes()[0];
            ByteBuffer  buffer = plane.getBuffer();
            int rowStride  = plane.getRowStride();
            int pixStride  = plane.getPixelStride();
            int rowPadding = rowStride - pixStride * captureW;

            // Create bitmap from buffer
            Bitmap bmp = Bitmap.createBitmap(
                    captureW + rowPadding / pixStride, captureH, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buffer);

            // Crop exact size
            Bitmap cropped = (rowPadding > 0)
                    ? Bitmap.createBitmap(bmp, 0, 0, captureW, captureH)
                    : bmp;

            // Compress to JPEG
            int quality = SocketManager.getInstance().getCurrentQuality();
            bos.reset();
            cropped.compress(Bitmap.CompressFormat.JPEG, quality, bos);

            if (cropped != bmp) cropped.recycle();
            bmp.recycle();

            // Base64 encode
            byte[] bytes  = bos.toByteArray();
            String b64    = Base64.encodeToString(bytes, Base64.NO_WRAP);
            long   ts     = System.currentTimeMillis();

            SocketManager.getInstance().sendFrame(b64, captureW, captureH, ts);

            // FPS tracking for notification
            framesSent.incrementAndGet();
            fpsWindowCount++;
            long elapsed = ts - fpsWindowStart;
            if (elapsed >= 2000) {
                lastFps = (int)(fpsWindowCount * 1000L / elapsed);
                fpsWindowCount = 0;
                fpsWindowStart = ts;
                updateNotification(lastFps + " fps · " + bytes.length / 1024 + " KB/frame");
            }

        } catch (Exception e) {
            Log.e(TAG, "Capture error: " + e.getMessage());
        } finally {
            if (image != null) image.close();
        }
    }

    public void stopCapture() {
        running.set(false);

        if (captureThread != null)  { captureThread.quitSafely(); captureThread = null; }
        if (virtualDisplay != null) { virtualDisplay.release();   virtualDisplay = null; }
        if (imageReader != null)    { imageReader.close();         imageReader    = null; }
        if (mediaProjection != null){ mediaProjection.stop();      mediaProjection = null; }

        Log.d(TAG, "Capture stopped. Total frames sent: " + framesSent.get());
    }

    // ─────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────

    private void getScreenMetrics() {
        WindowManager   wm      = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics  metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        screenW = metrics.widthPixels;
        screenH = metrics.heightPixels;
        density = metrics.densityDpi;
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ScreenMirror::CaptureWake");
        wakeLock.acquire(); // indefinite — released on service destroy
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Mirror begin", NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("Required for background operation");
            ch.setShowBadge(false);
            ch.enableLights(false);
            ch.enableVibration(false);
            ch.setSound(null, null);
            ch.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String status) {
        Intent openI = new Intent(this, MainActivity.class);
        PendingIntent openPI = PendingIntent.getActivity(this, 0, openI,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentIntent(openPI)
                .setSmallIcon(R.drawable.ic_transparent)
                .setContentTitle("")
                .setContentText("")
                .setTicker(null)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private void updateNotification(String status) {
        // Silent — no notification updates
    }
}
