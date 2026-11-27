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
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.webkit.ValueCallback;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
    private static final int REQ_FILE = 10, REQ_SAVE = 11;
    private ValueCallback<Uri[]> fileCb;
    private String pendingText;

    private void saved(boolean ok) {
        runOnUiThread(() -> web.evaluateJavascript("window.onNativeSaved&&window.onNativeSaved(" + ok + ")", null));
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_FILE) {
            if (fileCb != null) fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCb = null;
        } else if (code == REQ_SAVE) {
            String text = pendingText;
            pendingText = null;
            if (result != RESULT_OK || data == null || data.getData() == null || text == null) { saved(false); return; }
            try (OutputStream os = getContentResolver().openOutputStream(data.getData(), "wt")) {
                if (os == null) throw new Exception("no stream");
                os.write(text.getBytes(StandardCharsets.UTF_8));
                saved(true);
            } catch (Exception e) { saved(false); }
        }
    }

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
        @JavascriptInterface public void setWidget(String json) { FormaWidget.save(MainActivity.this, json); }
        // "2026-11-28|bl" — які прийоми їжі вже записані сьогодні (для розумних нагадувань)
        @JavascriptInterface public void setLogged(String v) {
            getSharedPreferences(Reminders.PREFS, MODE_PRIVATE).edit().putString("logged", v).apply();
        }
        // Колір панелей телефона під вибрану тему
        @JavascriptInterface public void setBars(String status, String navBar) {
            runOnUiThread(() -> {
                try {
                    getWindow().setStatusBarColor(Color.parseColor(status));
                    String n = navBar;
                    if (n != null && n.startsWith("rgb(")) {
                        String[] p = n.substring(4, n.length() - 1).split(",");
                        getWindow().setNavigationBarColor(Color.rgb(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())));
                    } else if (n != null) getWindow().setNavigationBarColor(Color.parseColor(n));
                    web.setBackgroundColor(Color.parseColor(status));
                } catch (Exception ignored) { }
            });
        }
        @JavascriptInterface public boolean batteryOk() {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm == null || pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        @JavascriptInterface public void openBattery() {
            runOnUiThread(() -> {
                try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
                catch (Exception e) {
                    try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
                    catch (Exception ignored) { }
                }
            });
        }
        // Резервна копія: користувач сам вибирає, куди зберегти файл
        @JavascriptInterface public void saveFile(String name, String text) {
            pendingText = text;
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/json")
                        .putExtra(Intent.EXTRA_TITLE, name);
                try { startActivityForResult(i, REQ_SAVE); }
                catch (Exception e) { pendingText = null; saved(false); }
            });
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

            // <input type="file">: фото з галереї і файл резервної копії
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                String type = "*/*";
                String[] acc = p.getAcceptTypes();
                if (acc != null && acc.length > 0 && acc[0] != null && acc[0].startsWith("image/")) type = "image/*";
                Intent i = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type);
                try { startActivityForResult(Intent.createChooser(i, null), REQ_FILE); }
                catch (Exception e) { fileCb = null; cb.onReceiveValue(null); return false; }
                return true;
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
