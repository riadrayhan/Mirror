package com.screenmirror.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Restarts the socket connection after device boot.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("sm_prefs", Context.MODE_PRIVATE);
            if (prefs.getBoolean("registered", false)) {
                // Reconnect socket in background
                String url = prefs.getString("server_url", "https://mirrorbackend-ohir.onrender.com");
                SocketManager.getInstance().connect(url);

                // Also launch activity so user can grant screen capture permission if needed
                Intent launchIntent = new Intent(context, MainActivity.class);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
            }
        }
    }
}
