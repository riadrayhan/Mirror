package com.screenmirror.app;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

public class MirrorGameActivity extends AppCompatActivity {

    private WebView webView;
    private SharedPreferences prefs;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("sm_prefs", MODE_PRIVATE);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);

        webView.setBackgroundColor(0xFF06060E);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new GameBridge(), "Android");
        webView.loadUrl("file:///android_asset/game.html");
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    /** JavaScript interface for saving/loading SharedPreferences */
    public class GameBridge {

        @JavascriptInterface
        public String getPref(String key, String defVal) {
            // First try string, then try int (for migration from old version)
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