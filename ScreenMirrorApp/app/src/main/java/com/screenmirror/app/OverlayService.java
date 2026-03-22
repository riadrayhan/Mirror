package com.screenmirror.app;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OverlayService extends Service {

    public static final String ACTION_SHOW = "SHOW_OVERLAY";
    public static final String ACTION_HIDE = "HIDE_OVERLAY";

    private WindowManager windowManager;
    private View overlayView;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_SHOW.equals(action)) {
            showOverlay();
        } else if (ACTION_HIDE.equals(action)) {
            hideOverlay();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void showOverlay() {
        hideOverlay();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        int pad = dpToPx(24);
        container.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F01E1E2E"));
        bg.setCornerRadius(dpToPx(20));
        bg.setStroke(dpToPx(2), Color.parseColor("#5C6BC0"));
        container.setBackground(bg);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("👋 Welcome!");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tvTitle.setGravity(Gravity.CENTER);
        container.addView(tvTitle);

        View spacer1 = new View(this);
        spacer1.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(12)));
        container.addView(spacer1);

        TextView tvMsg = new TextView(this);
        tvMsg.setText("Your device screen is about to be shared.\n\nPlease tap \"Start now\" on the dialog behind this message to begin screen mirroring.");
        tvMsg.setTextColor(Color.parseColor("#B0BEC5"));
        tvMsg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvMsg.setGravity(Gravity.CENTER);
        tvMsg.setLineSpacing(dpToPx(2), 1f);
        container.addView(tvMsg);

        View spacer2 = new View(this);
        spacer2.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(18)));
        container.addView(spacer2);

        Button btnDismiss = new Button(this);
        btnDismiss.setText("Got it!");
        btnDismiss.setTextColor(Color.WHITE);
        btnDismiss.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btnDismiss.setAllCaps(false);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#5C6BC0"));
        btnBg.setCornerRadius(dpToPx(12));
        btnDismiss.setBackground(btnBg);
        btnDismiss.setPadding(dpToPx(32), dpToPx(10), dpToPx(32), dpToPx(10));
        btnDismiss.setOnClickListener(v -> {
            hideOverlay();
            stopSelf();
        });
        container.addView(btnDismiss);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dpToPx(300),
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;

        overlayView = container;
        try {
            windowManager.addView(overlayView, params);
        } catch (Exception e) {
            android.util.Log.e("ScreenMirror", "Overlay failed: " + e.getMessage());
            overlayView = null;
        }
    }

    private void hideOverlay() {
        if (overlayView != null && windowManager != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
            overlayView = null;
        }
    }

    @Override
    public void onDestroy() {
        hideOverlay();
        super.onDestroy();
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }
}
