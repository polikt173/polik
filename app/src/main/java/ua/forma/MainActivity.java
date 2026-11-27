package ua.forma;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    private static final String START = "https://appassets.androidplatform.net/assets/index.html";
    private WebView web;
    private PermissionRequest pending;
    private SharedPreferences prefs;

    // Переносить дані зі старої версії (file://) у нову (https://appassets...)
    class Mig {
        @JavascriptInterface public void done(String data, String key) {
            prefs.edit().putString("data", data).putString("key", key).putBoolean("migrated", true).apply();
            runOnUiThread(() -> web.loadUrl(START));
        }
        @JavascriptInterface public boolean has() { return prefs.contains("data") || prefs.contains("key"); }
        @JavascriptInterface public String data() { return prefs.getString("data", ""); }
        @JavascriptInterface public String key() { return prefs.getString("key", ""); }
        @JavascriptInterface public void clear() { prefs.edit().remove("data").remove("key").apply(); }
    }

    // Міст для сторінки: нагадування і дозвіл на сповіщення
    class Native {
        @JavascriptInterface public void setReminders(String json) { Reminders.save(MainActivity.this, json); }
        @JavascriptInterface public String notifState() {
            if (Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return "granted";
                return prefs.getBoolean("notifAsked", false) ? "denied" : "default";
            }
            return ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled() ? "granted" : "denied";
        }
        @JavascriptInterface public void requestNotif() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("notifAsked", false)) {
                    prefs.edit().putBoolean("notifAsked", true).apply();
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
                } else {
                    startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.dispatchEvent(new Event('notifchange'))", null);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("forma", MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.parseColor("#0b0c0e"));
        getWindow().setNavigationBarColor(Color.parseColor("#111215"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0b0c0e"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public Bitmap getDefaultVideoPoster() {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            }

            @Override
            public void onPermissionRequest(final PermissionRequest req) {
                runOnUiThread(() -> {
                    boolean cam = false;
                    for (String r : req.getResources())
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) cam = true;
                    if (!cam) { req.deny(); return; }
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        req.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        pending = req;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
                    }
                });
            }
        });
        web.addJavascriptInterface(new Mig(), "Mig");
        web.addJavascriptInterface(new Native(), "Native");
        Reminders.schedule(this);
        setContentView(web);

        if (state != null) web.restoreState(state);
        else if (!prefs.getBoolean("migrated", false)) web.loadUrl("file:///android_asset/migrate.html");
        else web.loadUrl(START);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == 2) { web.evaluateJavascript("window.dispatchEvent(new Event('notifchange'))", null); return; }
        if (pending != null) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED)
                pending.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            else pending.deny();
            pending = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.forBack?window.forBack():false", v -> {
            if (!"true".equals(v)) finish();
        });
    }
}
