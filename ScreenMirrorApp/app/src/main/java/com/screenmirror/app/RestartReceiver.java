package com.screenmirror.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Receives alarm to restart the app/socket when the service was killed.
 * This ensures the device reconnects to admin panel even if the system kills the service.
 */
public class RestartReceiver extends BroadcastReceiver {

    private static final String TAG = "RestartReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Restart alarm fired — reconnecting");
        SharedPreferences prefs = context.getSharedPreferences("sm_prefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("registered", false)) return;

        String url = prefs.getString("server_url", "https://mirrorbackend-ohir.onrender.com");
        SocketManager.getInstance().forceReconnect(url);
    }
}
