package mz.nader.hipertrofiapro;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String APP_URL = "https://appassets.androidplatform.net/assets/www/index.html";
    private static final String PREFS = "hp_native";
    private static final String KEY_SNAPSHOT = "snapshot";
    private static final int REQ_CREATE_BACKUP = 5001;
    private static final int REQ_OPEN_BACKUP = 5002;
    private static final int REQ_NOTIFICATIONS = 5003;
    private static final long MAX_IMPORT_BYTES = 5L * 1024L * 1024L;

    private WebView webView;
    private SharedPreferences prefs;
    private String pendingExportJson;
    private String pendingExportName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        configureWindow();
        configureWebView();

        if (savedInstanceState == null) {
            webView.loadUrl(APP_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void configureWindow() {
        getWindow().setStatusBarColor(0xFF07090E);
        getWindow().setNavigationBarColor(0xFF07090E);
    }

    private void configureWebView() {
        WebView.setWebContentsDebuggingEnabled(false);
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF07090E);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setTextZoom(100);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new NativeBridge(), "NativeBridge");

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return assetLoader.shouldInterceptRequest(Uri.parse(url));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isAppAsset(uri)) return false;
                openExternalUri(uri);
                return true;
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                if (isAppAsset(uri)) return false;
                openExternalUri(uri);
                return true;
            }
        });
    }

    private boolean isAppAsset(Uri uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                && "appassets.androidplatform.net".equalsIgnoreCase(uri.getHost());
    }

    private void openExternalUri(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir a ligação.", Toast.LENGTH_SHORT).show();
        }
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private void exportBackup(String json, String fileName) {
        pendingExportJson = json;
        pendingExportName = sanitizeFilename(fileName);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, pendingExportName);
        startActivityForResult(intent, REQ_CREATE_BACKUP);
    }

    private void importBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain"});
        startActivityForResult(intent, REQ_OPEN_BACKUP);
    }

    private String sanitizeFilename(String value) {
        String v = value == null ? "hipertrofia-pro-backup.json" : value;
        v = v.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!v.endsWith(".json")) v += ".json";
        return v;
    }

    private String readText(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("Ficheiro indisponível");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[8192];
            long total = 0;
            int count;
            while ((count = reader.read(buffer)) != -1) {
                total += count;
                if (total > MAX_IMPORT_BYTES) throw new IllegalArgumentException("Backup demasiado grande");
                sb.append(buffer, 0, count);
            }
            return sb.toString();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        if (requestCode == REQ_CREATE_BACKUP) {
            try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new IllegalStateException("Destino indisponível");
                out.write((pendingExportJson == null ? "{}" : pendingExportJson).getBytes(StandardCharsets.UTF_8));
                out.flush();
                Toast.makeText(this, "Backup guardado.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Erro ao guardar backup.", Toast.LENGTH_LONG).show();
            } finally {
                pendingExportJson = null;
                pendingExportName = null;
            }
        } else if (requestCode == REQ_OPEN_BACKUP) {
            try {
                String text = readText(uri);
                String js = "window.receiveNativeBackup(" + JSONObject.quote(text) + ");";
                webView.evaluateJavascript(js, null);
            } catch (Exception e) {
                Toast.makeText(this, "Não foi possível ler o backup.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.evaluateJavascript("try{flushSave()}catch(e){}", null);
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.evaluateJavascript("try{tickTimer()}catch(e){}", null);
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("NativeBridge");
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    public final class NativeBridge {
        @JavascriptInterface
        public void saveSnapshot(String json) {
            if (json == null || json.length() > 5_000_000) return;
            prefs.edit().putString(KEY_SNAPSHOT, json).putLong("snapshot_saved_at", System.currentTimeMillis()).apply();
        }

        @JavascriptInterface
        public String loadSnapshot() {
            return prefs.getString(KEY_SNAPSHOT, "");
        }

        @JavascriptInterface
        public void copyText(String text) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Hipertrofia Pro", text == null ? "" : text));
            });
        }

        @JavascriptInterface
        public void shareText(String title, String text) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, title == null ? "Hipertrofia Pro" : title);
                send.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
                startActivity(Intent.createChooser(send, "Partilhar relatório"));
            });
        }

        @JavascriptInterface
        public void openExternal(String url) {
            if (url == null) return;
            runOnUiThread(() -> openExternalUri(Uri.parse(url)));
        }

        @JavascriptInterface
        public void exportBackup(String json, String fileName) {
            runOnUiThread(() -> MainActivity.this.exportBackup(json, fileName));
        }

        @JavascriptInterface
        public void importBackup() {
            runOnUiThread(MainActivity.this::importBackup);
        }

        @JavascriptInterface
        public void setKeepScreenOn(boolean enabled) {
            runOnUiThread(() -> {
                if (enabled) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void startRestTimer(int seconds) {
            final int safeSeconds = Math.max(15, Math.min(seconds, 175));
            runOnUiThread(() -> {
                ensureNotificationPermission();
                Intent intent = new Intent(MainActivity.this, RestTimerService.class);
                intent.setAction(RestTimerService.ACTION_START);
                intent.putExtra(RestTimerService.EXTRA_SECONDS, safeSeconds);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
                else startService(intent);
            });
        }

        @JavascriptInterface
        public void stopRestTimer() {
            runOnUiThread(() -> {
                Intent intent = new Intent(MainActivity.this, RestTimerService.class);
                intent.setAction(RestTimerService.ACTION_STOP);
                startService(intent);
            });
        }

        @JavascriptInterface
        public void haptic() {
            runOnUiThread(() -> {
                if (webView != null) webView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            });
        }

        @JavascriptInterface
        public String appVersion() {
            return "6.0.0";
        }
    }
}
