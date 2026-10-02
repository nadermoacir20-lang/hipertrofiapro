package mz.nader.hipertrofiapro;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {

    private static final String APP_URL =
            "https://appassets.androidplatform.net/assets/www/index.html";

    private static final int REQ_CREATE_BACKUP = 5001;
    private static final int REQ_OPEN_BACKUP = 5002;
    private static final int REQ_NOTIFICATIONS = 5003;

    private static final long MAX_IMPORT_BYTES =
            8L * 1024L * 1024L;

    /*
     * PRIMEIRO VÍDEO DE TESTE
     *
     * Local:
     * app/src/main/assets/videos/
     */
    private static final String HIP_THRUST_VIDEO =
            "Hip Thrust Machine (Plate-loaded) - Team Royal Training (1080p).mp4";

    private WebView webView;

    private FrameLayout rootLayout;
    private FrameLayout videoOverlay;

    private PlayerView playerView;
    private ExoPlayer player;

    private NativeStateStore stateStore;

    private String pendingExportJson;
    private String pendingExportName;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        stateStore = new NativeStateStore(this);

        configureWindow();
        configureWebView();

        WeightReminderScheduler.scheduleNext(this);

        if (savedInstanceState == null) {
            webView.loadUrl(APP_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }


    private void configureWindow() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        );

        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow()
                    .getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    );
        }
    }


    private void configureWebView() {

        WebView.setWebContentsDebuggingEnabled(false);

        webView = new WebView(this);

        webView.setBackgroundColor(0xFF0B0D14);
        webView.setVerticalScrollBarEnabled(false);

        /*
         * ROOT DA APP
         *
         * FrameLayout
         * ├── WebView
         * └── Video Overlay
         */
        rootLayout = new FrameLayout(this);

        rootLayout.addView(
                webView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        setContentView(rootLayout);

        installInsetsHandler();


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

        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_NEVER_ALLOW
        );

        settings.setCacheMode(
                WebSettings.LOAD_DEFAULT
        );


        webView.addJavascriptInterface(
                new NativeBridge(),
                "NativeBridge"
        );


        WebViewAssetLoader assetLoader =
                new WebViewAssetLoader.Builder()
                        .addPathHandler(
                                "/assets/",
                                new WebViewAssetLoader.AssetsPathHandler(this)
                        )
                        .build();


        webView.setWebViewClient(
                new WebViewClientCompat() {

                    @Override
                    public WebResourceResponse shouldInterceptRequest(
                            WebView view,
                            WebResourceRequest request
                    ) {
                        return assetLoader.shouldInterceptRequest(
                                request.getUrl()
                        );
                    }


                    @Override
                    @SuppressWarnings("deprecation")
                    public WebResourceResponse shouldInterceptRequest(
                            WebView view,
                            String url
                    ) {
                        return assetLoader.shouldInterceptRequest(
                                Uri.parse(url)
                        );
                    }


                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            WebResourceRequest request
                    ) {

                        Uri uri = request.getUrl();

                        if (isAppAsset(uri)) {
                            return false;
                        }

                        openExternalUri(uri);

                        return true;
                    }


                    @Override
                    @SuppressWarnings("deprecation")
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            String url
                    ) {

                        Uri uri = Uri.parse(url);

                        if (isAppAsset(uri)) {
                            return false;
                        }

                        openExternalUri(uri);

                        return true;
                    }


                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ) {

                        super.onPageFinished(view, url);

                        ensureNotificationPermissionOnce();
                    }
                }
        );
    }


    private void installInsetsHandler() {

        webView.setOnApplyWindowInsetsListener(
                (view, insets) -> {

                    int left;
                    int top;
                    int right;
                    int bottom;

                    if (Build.VERSION.SDK_INT >= 30) {

                        Insets bars =
                                insets.getInsets(
                                        WindowInsets.Type.systemBars()
                                );

                        Insets ime =
                                insets.getInsets(
                                        WindowInsets.Type.ime()
                                );

                        left = bars.left;
                        top = bars.top;
                        right = bars.right;

                        bottom =
                                Math.max(
                                        bars.bottom,
                                        ime.bottom
                                );

                    } else {

                        left =
                                insets.getSystemWindowInsetLeft();

                        top =
                                insets.getSystemWindowInsetTop();

                        right =
                                insets.getSystemWindowInsetRight();

                        bottom =
                                insets.getSystemWindowInsetBottom();
                    }

                    view.setPadding(
                            left,
                            top,
                            right,
                            bottom
                    );

                    return insets;
                }
        );

        webView.requestApplyInsets();
    }


    private boolean isAppAsset(Uri uri) {

        return "https".equalsIgnoreCase(
                uri.getScheme()
        )
                &&
                "appassets.androidplatform.net"
                        .equalsIgnoreCase(
                                uri.getHost()
                        );
    }


    private void openExternalUri(Uri uri) {

        try {

            String scheme = uri.getScheme();

            if (
                    scheme == null
                            ||
                            !(
                                    scheme.equalsIgnoreCase("http")
                                            ||
                                            scheme.equalsIgnoreCase("https")
                                            ||
                                            scheme.equalsIgnoreCase("mailto")
                                            ||
                                            scheme.equalsIgnoreCase("tel")
                            )
            ) {
                throw new IllegalArgumentException(
                        "Unsupported scheme"
                );
            }

            startActivity(
                    new Intent(
                            Intent.ACTION_VIEW,
                            uri
                    )
            );

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Não foi possível abrir a ligação.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    private void ensureNotificationPermissionOnce() {

        if (Build.VERSION.SDK_INT < 33) {
            return;
        }

        if (
                checkSelfPermission(
                        Manifest.permission.POST_NOTIFICATIONS
                )
                        ==
                        PackageManager.PERMISSION_GRANTED
        ) {
            return;
        }

        SharedPreferences prefs =
                stateStore.prefs();

        if (
                prefs.getBoolean(
                        NativeStateStore.KEY_NOTIFICATION_PROMPTED,
                        false
                )
        ) {
            return;
        }

        prefs.edit()
                .putBoolean(
                        NativeStateStore.KEY_NOTIFICATION_PROMPTED,
                        true
                )
                .apply();

        requestPermissions(
                new String[]{
                        Manifest.permission.POST_NOTIFICATIONS
                },
                REQ_NOTIFICATIONS
        );
    }


    private void exportBackup(
            String json,
            String fileName
    ) {

        pendingExportJson = json;

        pendingExportName =
                sanitizeFilename(fileName);

        Intent intent =
                new Intent(
                        Intent.ACTION_CREATE_DOCUMENT
                );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.setType(
                "application/json"
        );

        intent.putExtra(
                Intent.EXTRA_TITLE,
                pendingExportName
        );

        startActivityForResult(
                intent,
                REQ_CREATE_BACKUP
        );
    }


    private void importBackup() {

        Intent intent =
                new Intent(
                        Intent.ACTION_OPEN_DOCUMENT
                );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.setType(
                "application/json"
        );

        intent.putExtra(
                Intent.EXTRA_MIME_TYPES,
                new String[]{
                        "application/json",
                        "text/plain"
                }
        );

        startActivityForResult(
                intent,
                REQ_OPEN_BACKUP
        );
    }


    private String sanitizeFilename(
            String value
    ) {

        String v =
                value == null
                        ?
                        "hipertrofia-pro-v7-backup.json"
                        :
                        value;

        v =
                v.replaceAll(
                        "[^a-zA-Z0-9._-]",
                        "_"
                );

        if (!v.endsWith(".json")) {
            v += ".json";
        }

        return v;
    }


    private String readText(
            Uri uri
    ) throws Exception {

        try (
                InputStream in =
                        getContentResolver()
                                .openInputStream(uri)
        ) {

            if (in == null) {
                throw new IllegalStateException(
                        "Ficheiro indisponível"
                );
            }

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    in,
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder sb =
                    new StringBuilder();

            char[] buffer =
                    new char[8192];

            long total = 0;

            int count;

            while (
                    (count = reader.read(buffer))
                            != -1
            ) {

                total += count;

                if (
                        total >
                                MAX_IMPORT_BYTES
                ) {
                    throw new IllegalArgumentException(
                            "Backup demasiado grande"
                    );
                }

                sb.append(
                        buffer,
                        0,
                        count
                );
            }

            return sb.toString();
        }
    }


    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (
                resultCode != RESULT_OK
                        ||
                        data == null
                        ||
                        data.getData() == null
        ) {
            return;
        }

        Uri uri =
                data.getData();


        if (
                requestCode ==
                        REQ_CREATE_BACKUP
        ) {

            try (
                    OutputStream out =
                            getContentResolver()
                                    .openOutputStream(
                                            uri,
                                            "w"
                                    )
            ) {

                if (out == null) {
                    throw new IllegalStateException(
                            "Destino indisponível"
                    );
                }

                out.write(
                        (
                                pendingExportJson == null
                                        ?
                                        "{}"
                                        :
                                        pendingExportJson
                        ).getBytes(
                                StandardCharsets.UTF_8
                        )
                );

                out.flush();

                Toast.makeText(
                        this,
                        "Backup guardado.",
                        Toast.LENGTH_SHORT
                ).show();

            } catch (Exception e) {

                Toast.makeText(
                        this,
                        "Erro ao guardar backup.",
                        Toast.LENGTH_LONG
                ).show();

            } finally {

                pendingExportJson = null;
                pendingExportName = null;
            }

        } else if (
                requestCode ==
                        REQ_OPEN_BACKUP
        ) {

            try {

                String text =
                        readText(uri);

                String js =
                        "window.receiveNativeBackup && " +
                                "window.receiveNativeBackup("
                                +
                                JSONObject.quote(text)
                                +
                                ");";

                webView.evaluateJavascript(
                        js,
                        null
                );

            } catch (Exception e) {

                Toast.makeText(
                        this,
                        "Não foi possível ler o backup.",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }


    /*
     * ============================================================
     * VIDEO PLAYER
     * ============================================================
     */


    private void ensureVideoOverlay() {

        if (videoOverlay != null) {
            return;
        }


        videoOverlay =
                new FrameLayout(this);

        videoOverlay.setBackgroundColor(
                Color.BLACK
        );

        videoOverlay.setVisibility(
                View.GONE
        );

        videoOverlay.setClickable(true);


        playerView =
                new PlayerView(this);

        playerView.setBackgroundColor(
                Color.BLACK
        );

        playerView.setUseController(true);

        playerView.setControllerAutoShow(true);

        playerView.setShowBuffering(
                PlayerView.SHOW_BUFFERING_WHEN_PLAYING
        );


        videoOverlay.addView(
                playerView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );


        TextView closeButton =
                new TextView(this);

        closeButton.setText("✕");

        closeButton.setTextColor(
                Color.WHITE
        );

        closeButton.setTextSize(28);

        closeButton.setGravity(
                Gravity.CENTER
        );

        closeButton.setBackgroundColor(
                0x99000000
        );

        closeButton.setOnClickListener(
                v -> closeTechniqueVideo()
        );


        FrameLayout.LayoutParams closeParams =
                new FrameLayout.LayoutParams(
                        dp(52),
                        dp(52),
                        Gravity.TOP
                                |
                                Gravity.END
                );

        closeParams.topMargin =
                dp(38);

        closeParams.rightMargin =
                dp(12);


        videoOverlay.addView(
                closeButton,
                closeParams
        );


        rootLayout.addView(
                videoOverlay,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );
    }


    private void openTechniqueVideo(
            String exerciseId
    ) {

        /*
         * PRIMEIRO TESTE:
         *
         * Apenas Hip Thrust.
         *
         * Depois de confirmarmos que funciona,
         * mapeamos todos os 43 exercícios activos.
         */

        if (
                !"thu-hipthrust"
                        .equals(exerciseId)
        ) {

            Toast.makeText(
                    this,
                    "Vídeo ainda não associado.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        ensureVideoOverlay();

        closePlayerOnly();


        Uri videoUri =
                new Uri.Builder()
                        .scheme("asset")
                        .path(
                                "/videos/"
                                        +
                                        HIP_THRUST_VIDEO
                        )
                        .build();


        player =
                new ExoPlayer.Builder(this)
                        .build();


        playerView.setPlayer(
                player
        );


        MediaItem mediaItem =
                MediaItem.fromUri(
                        videoUri
                );


        player.setMediaItem(
                mediaItem
        );

        player.prepare();

        player.play();


        videoOverlay.setVisibility(
                View.VISIBLE
        );

        videoOverlay.bringToFront();


        setKeepScreenOn(true);
    }


    private void closePlayerOnly() {

        if (player != null) {

            player.stop();

            player.release();

            player = null;
        }


        if (playerView != null) {

            playerView.setPlayer(
                    null
            );
        }
    }


    private void closeTechniqueVideo() {

        closePlayerOnly();


        if (videoOverlay != null) {

            videoOverlay.setVisibility(
                    View.GONE
            );
        }


        setKeepScreenOn(false);
    }


    private int dp(int value) {

        return Math.round(
                value
                        *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }


    /*
     * ============================================================
     * ACTIVITY LIFECYCLE
     * ============================================================
     */


    @Override
    protected void onSaveInstanceState(
            Bundle outState
    ) {

        if (webView != null) {
            webView.saveState(
                    outState
            );
        }

        super.onSaveInstanceState(
                outState
        );
    }


    @Override
    protected void onPause() {

        if (webView != null) {

            webView.evaluateJavascript(
                    "try{" +
                            "window.onNativePause&&" +
                            "window.onNativePause()" +
                            "}catch(e){}",
                    null
            );
        }


        if (player != null) {
            player.pause();
        }


        setKeepScreenOn(false);


        if (webView != null) {
            webView.onPause();
        }


        super.onPause();
    }


    @Override
    protected void onResume() {

        super.onResume();


        if (webView != null) {

            webView.onResume();

            webView.evaluateJavascript(
                    "try{" +
                            "window.onNativeResume&&" +
                            "window.onNativeResume()" +
                            "}catch(e){}",
                    null
            );
        }


        if (
                videoOverlay != null
                        &&
                        videoOverlay.getVisibility()
                                ==
                                View.VISIBLE
        ) {

            setKeepScreenOn(true);
        }
    }


    @Override
    protected void onDestroy() {

        closePlayerOnly();


        if (webView != null) {

            webView.removeJavascriptInterface(
                    "NativeBridge"
            );

            webView.stopLoading();

            webView.setWebViewClient(
                    null
            );

            webView.destroy();

            webView = null;
        }


        videoOverlay = null;
        playerView = null;
        rootLayout = null;


        super.onDestroy();
    }


    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {

        /*
         * Se o vídeo estiver aberto,
         * BACK fecha primeiro o vídeo.
         */
        if (
                videoOverlay != null
                        &&
                        videoOverlay.getVisibility()
                                ==
                                View.VISIBLE
        ) {

            closeTechniqueVideo();

            return;
        }


        if (webView == null) {

            super.onBackPressed();

            return;
        }


        webView.evaluateJavascript(
                "try{" +
                        "String(!!(" +
                        "window.handleAndroidBack&&" +
                        "window.handleAndroidBack()" +
                        "))" +
                        "}catch(e){'false'}",
                result -> {

                    if (
                            "\"true\""
                                    .equals(result)
                    ) {
                        return;
                    }


                    if (
                            webView != null
                                    &&
                                    webView.canGoBack()
                    ) {

                        webView.goBack();

                    } else {

                        MainActivity.super
                                .onBackPressed();
                    }
                }
        );
    }


    /*
     * ============================================================
     * UTILIDADES
     * ============================================================
     */


    private void setKeepScreenOn(
            boolean enabled
    ) {

        if (enabled) {

            getWindow()
                    .addFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    );

        } else {

            getWindow()
                    .clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    );
        }
    }


    private void vibrate(
            long milliseconds
    ) {

        if (milliseconds <= 0) {
            return;
        }


        try {

            Vibrator vibrator;


            if (Build.VERSION.SDK_INT >= 31) {

                VibratorManager manager =
                        (VibratorManager)
                                getSystemService(
                                        Context.VIBRATOR_MANAGER_SERVICE
                                );

                vibrator =
                        manager == null
                                ?
                                null
                                :
                                manager.getDefaultVibrator();

            } else {

                vibrator =
                        (Vibrator)
                                getSystemService(
                                        Context.VIBRATOR_SERVICE
                                );
            }


            if (
                    vibrator == null
                            ||
                            !vibrator.hasVibrator()
            ) {
                return;
            }


            if (Build.VERSION.SDK_INT >= 26) {

                vibrator.vibrate(
                        VibrationEffect.createOneShot(
                                milliseconds,
                                VibrationEffect.DEFAULT_AMPLITUDE
                        )
                );

            } else {

                vibrator.vibrate(
                        milliseconds
                );
            }

        } catch (Exception ignored) {
        }
    }


    private void vibratePattern(
            String csv
    ) {

        try {

            String[] parts =
                    csv == null
                            ?
                            new String[0]
                            :
                            csv.split(",");


            List<Long> values =
                    new ArrayList<>();


            values.add(0L);


            for (String part : parts) {

                long v =
                        Math.max(
                                0L,
                                Long.parseLong(
                                        part.trim()
                                )
                        );

                values.add(v);
            }


            if (values.size() <= 1) {
                return;
            }


            long[] pattern =
                    new long[
                            values.size()
                            ];


            for (
                    int i = 0;
                    i < values.size();
                    i++
            ) {

                pattern[i] =
                        values.get(i);
            }


            Vibrator vibrator;


            if (Build.VERSION.SDK_INT >= 31) {

                VibratorManager manager =
                        (VibratorManager)
                                getSystemService(
                                        Context.VIBRATOR_MANAGER_SERVICE
                                );

                vibrator =
                        manager == null
                                ?
                                null
                                :
                                manager.getDefaultVibrator();

            } else {

                vibrator =
                        (Vibrator)
                                getSystemService(
                                        Context.VIBRATOR_SERVICE
                                );
            }


            if (
                    vibrator == null
                            ||
                            !vibrator.hasVibrator()
            ) {
                return;
            }


            if (Build.VERSION.SDK_INT >= 26) {

                vibrator.vibrate(
                        VibrationEffect.createWaveform(
                                pattern,
                                -1
                        )
                );

            } else {

                vibrator.vibrate(
                        pattern,
                        -1
                );
            }

        } catch (Exception ignored) {
        }
    }


    /*
     * ============================================================
     * JAVASCRIPT ↔ ANDROID BRIDGE
     * ============================================================
     */


    public final class NativeBridge {


        @JavascriptInterface
        public void saveSnapshot(
                String json
        ) {

            stateStore.save(
                    json
            );
        }


        @JavascriptInterface
        public String loadSnapshot() {

            String value =
                    stateStore.loadLatest();

            return value == null
                    ?
                    ""
                    :
                    value;
        }


        @JavascriptInterface
        public void copyText(
                String text
        ) {

            runOnUiThread(
                    () -> {

                        ClipboardManager clipboard =
                                (ClipboardManager)
                                        getSystemService(
                                                Context.CLIPBOARD_SERVICE
                                        );


                        if (clipboard != null) {

                            clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                            "Hipertrofia Pro",
                                            text == null
                                                    ?
                                                    ""
                                                    :
                                                    text
                                    )
                            );
                        }
                    }
            );
        }


        @JavascriptInterface
        public void shareText(
                String title,
                String text
        ) {

            runOnUiThread(
                    () -> {

                        Intent send =
                                new Intent(
                                        Intent.ACTION_SEND
                                );

                        send.setType(
                                "text/plain"
                        );

                        send.putExtra(
                                Intent.EXTRA_SUBJECT,
                                title == null
                                        ?
                                        "Hipertrofia Pro"
                                        :
                                        title
                        );

                        send.putExtra(
                                Intent.EXTRA_TEXT,
                                text == null
                                        ?
                                        ""
                                        :
                                        text
                        );


                        startActivity(
                                Intent.createChooser(
                                        send,
                                        title == null
                                                ?
                                                "Partilhar"
                                                :
                                                title
                                )
                        );
                    }
            );
        }


        @JavascriptInterface
        public void openExternal(
                String url
        ) {

            runOnUiThread(
                    () -> {

                        try {

                            openExternalUri(
                                    Uri.parse(url)
                            );

                        } catch (Exception e) {

                            Toast.makeText(
                                    MainActivity.this,
                                    "Ligação inválida.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
            );
        }


        @JavascriptInterface
        public void exportBackup(
                String json,
                String fileName
        ) {

            runOnUiThread(
                    () ->
                            MainActivity.this
                                    .exportBackup(
                                            json,
                                            fileName
                                    )
            );
        }


        @JavascriptInterface
        public void importBackup() {

            runOnUiThread(
                    MainActivity.this::importBackup
            );
        }


        @JavascriptInterface
        public void setKeepScreenOn(
                boolean enabled
        ) {

            runOnUiThread(
                    () ->
                            MainActivity.this
                                    .setKeepScreenOn(
                                            enabled
                                    )
            );
        }


        @JavascriptInterface
        public void haptic(
                int milliseconds
        ) {

            runOnUiThread(
                    () ->
                            vibrate(
                                    Math.min(
                                            Math.max(
                                                    milliseconds,
                                                    1
                                            ),
                                            1000
                                    )
                            )
            );
        }


        @JavascriptInterface
        public void hapticPattern(
                String csv
        ) {

            runOnUiThread(
                    () ->
                            vibratePattern(
                                    csv
                            )
            );
        }


        /*
         * NOVO:
         * abre tutorial nativo
         */
        @JavascriptInterface
        public void openTechniqueVideo(
                String exerciseId
        ) {

            runOnUiThread(
                    () ->
                            MainActivity.this
                                    .openTechniqueVideo(
                                            exerciseId
                                    )
            );
        }


        @JavascriptInterface
        public String appVersion() {

            return "7.1.0";
        }
    }
}
