package com.screenmirror.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Restarts the socket connection after device boot or quick-boot.
 * Also ensures ScreenCaptureService stays alive.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {

            Log.d(TAG, "Boot/quickboot completed — starting up");
            SharedPreferences prefs = context.getSharedPreferences("sm_prefs", Context.MODE_PRIVATE);
            if (!prefs.getBoolean("registered", false)) return;

            // Connect socket immediately
            String url = prefs.getString("server_url", "https://mirrorbackend-ohir.onrender.com");
            SocketManager.getInstance().forceReconnect(url);

            // Launch activity so user can grant screen capture permission if needed
            Intent launchIntent = new Intent(context, MainActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(launchIntent);
        }
    }
}
