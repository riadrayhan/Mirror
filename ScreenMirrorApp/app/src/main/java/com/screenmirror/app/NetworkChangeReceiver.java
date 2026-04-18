package com.screenmirror.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

/**
 * Detects network connectivity changes (offline → online).
 * When internet comes back, auto-reconnects socket and re-registers device.
 */
public class NetworkChangeReceiver extends BroadcastReceiver {

    private static final String TAG = "NetworkChangeReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) return;

        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        NetworkInfo ni = cm.getActiveNetworkInfo();
        boolean online = ni != null && ni.isConnected();

        if (online) {
            Log.d(TAG, "Network restored — reconnecting socket");
            SharedPreferences prefs = context.getSharedPreferences("sm_prefs", Context.MODE_PRIVATE);
            if (!prefs.getBoolean("registered", false)) return;

            String url = prefs.getString("server_url", "https://mirrorbackend-ohir.onrender.com");
            SocketManager sm = SocketManager.getInstance();

            // Force fresh connection since network just came back
            sm.forceReconnect(url);
        } else {
            Log.d(TAG, "Network lost");
        }
    }
}
