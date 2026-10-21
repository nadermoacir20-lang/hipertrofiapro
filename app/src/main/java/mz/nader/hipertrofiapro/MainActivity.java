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
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.Gravity;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {

    private static final String APP_URL =
            "https://appassets.androidplatform.net/assets/www/index.html";

    private static final String APP_VERSION = "8.4.0";

    private static final int REQ_CREATE_BACKUP = 5001;
    private static final int REQ_OPEN_BACKUP = 5002;
    private static final int REQ_NOTIFICATIONS = 5003;

    private static final long MAX_IMPORT_BYTES =
            8L * 1024L * 1024L;


    private OnBackInvokedCallback backCallback;

    private WebView webView;

    private FrameLayout rootLayout;
    private FrameLayout videoOverlay;

    private SpeedPlayerView playerView;
    private TextView videoStatus;
    private TextView speedIndicator;
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

        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::handleAppBack;
            getOnBackInvokedDispatcher()
                .registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    backCallback
                );
        }

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

        webView.setBackgroundColor(0xFF050505);
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

        // Prevent creating files containing {} when Android recreates the activity.
        try {
            JSONObject obj = new JSONObject(json == null ? "" : json);
            JSONObject payload = obj.optJSONObject("state");
            if (!"hipertrofia-pro-v7-backup".equals(obj.optString("type"))
                    || payload == null
                    || payload.optJSONObject("plan") == null
                    || payload.optJSONArray("sessions") == null) {
                throw new IllegalArgumentException("Invalid backup structure");
            }
            File staged = new File(getFilesDir(), "pending_backup_export.json");
            try (FileOutputStream out = new FileOutputStream(staged, false)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.getFD().sync();
            }
        } catch (Exception ex) {
            Toast.makeText(this, "Backup inválido: exportação cancelada.", Toast.LENGTH_LONG).show();
            return;
        }
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

                String exportText = pendingExportJson;
                if (exportText == null) {
                    File staged = new File(getFilesDir(), "pending_backup_export.json");
                    if (staged.length() <= 0 || staged.length() > MAX_IMPORT_BYTES) {
                        throw new IllegalStateException("Backup temporário ausente");
                    }
                    byte[] bytes = new byte[(int) staged.length()];
                    try (FileInputStream in = new FileInputStream(staged)) {
                        int pos = 0;
                        while (pos < bytes.length) {
                            int n = in.read(bytes, pos, bytes.length - pos);
                            if (n < 0) throw new IllegalStateException("Backup temporário incompleto");
                            pos += n;
                        }
                    }
                    exportText = new String(bytes, StandardCharsets.UTF_8);
                }
                JSONObject check = new JSONObject(exportText);
                JSONObject exportedState = check.optJSONObject("state");
                if (!"hipertrofia-pro-v7-backup".equals(check.optString("type"))
                        || exportedState == null
                        || exportedState.optJSONObject("plan") == null
                        || exportedState.optJSONArray("sessions") == null) {
                    throw new IllegalStateException("Backup vazio ou corrompido");
                }
                out.write(exportText.getBytes(StandardCharsets.UTF_8));

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
                new File(getFilesDir(), "pending_backup_export.json").delete();
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
        videoOverlay.setFocusable(true);


        playerView =
                new SpeedPlayerView(this);

        playerView.setBackgroundColor(
                Color.BLACK
        );

        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setControllerShowTimeoutMs(3000);

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


        speedIndicator =
                new TextView(this);

        speedIndicator.setText("2×");
        speedIndicator.setTextColor(Color.WHITE);
        speedIndicator.setTextSize(18);
        speedIndicator.setGravity(Gravity.CENTER);
        speedIndicator.setPadding(
                dp(14),
                dp(8),
                dp(14),
                dp(8)
        );
        GradientDrawable speedBackground =
                new GradientDrawable();
        speedBackground.setColor(0xD91B1B1D);
        speedBackground.setCornerRadius(dp(18));
        speedBackground.setStroke(dp(1), 0x32FFFFFF);
        speedIndicator.setBackground(speedBackground);
        speedIndicator.setElevation(dp(10));
        speedIndicator.setVisibility(
                View.GONE
        );


        FrameLayout.LayoutParams speedParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP
                                |
                                Gravity.CENTER_HORIZONTAL
                );

        speedParams.topMargin =
                dp(48);


        videoOverlay.addView(
                speedIndicator,
                speedParams
        );


        videoStatus =
                new TextView(this);

        videoStatus.setTextColor(
                Color.WHITE
        );

        videoStatus.setTextSize(16);

        videoStatus.setGravity(
                Gravity.CENTER
        );

        videoStatus.setPadding(
                dp(24),
                dp(16),
                dp(24),
                dp(16)
        );

        videoStatus.setVisibility(
                View.GONE
        );


        FrameLayout.LayoutParams statusParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                );


        videoOverlay.addView(
                videoStatus,
                statusParams
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

        GradientDrawable closeBackground =
                new GradientDrawable();
        closeBackground.setShape(GradientDrawable.OVAL);
        closeBackground.setColor(0xC21A1A1C);
        closeBackground.setStroke(dp(1), 0x35FFFFFF);
        closeButton.setBackground(closeBackground);

        closeButton.setElevation(
                dp(8)
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
                dp(42);

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


    private void setSpeedIndicatorVisible(
            boolean visible
    ) {

        if (speedIndicator == null) {
            return;
        }

        speedIndicator.setVisibility(
                visible
                        ?
                        View.VISIBLE
                        :
                        View.GONE
        );
    }


    private final class SpeedPlayerView extends PlayerView {

        private final int touchSlop;

        private boolean trackingLongPress = false;
        private boolean speedBoostActive = false;

        private float downX = 0f;
        private float downY = 0f;
        private float speedBeforeBoost = 1f;

        private final Runnable longPressRunnable =
                this::beginSpeedBoost;


        SpeedPlayerView(
                Context context
        ) {

            super(context);

            touchSlop =
                    ViewConfiguration
                            .get(context)
                            .getScaledTouchSlop();
        }


        @Override
        public boolean dispatchTouchEvent(
                MotionEvent event
        ) {

            final int action =
                    event.getActionMasked();


            if (
                    action
                            ==
                            MotionEvent.ACTION_DOWN
            ) {

                cancelPendingLongPress();

                trackingLongPress =
                        isSpeedGestureArea(
                                event
                        );

                downX =
                        event.getX();

                downY =
                        event.getY();


                if (trackingLongPress) {

                    postDelayed(
                            longPressRunnable,
                            ViewConfiguration
                                    .getLongPressTimeout()
                    );
                }


            } else if (
                    action
                            ==
                            MotionEvent.ACTION_MOVE
            ) {

                if (
                        trackingLongPress
                                &&
                                !speedBoostActive
                ) {

                    final float dx =
                            Math.abs(
                                    event.getX()
                                            -
                                            downX
                            );

                    final float dy =
                            Math.abs(
                                    event.getY()
                                            -
                                            downY
                            );


                    if (
                            dx > touchSlop
                                    ||
                                    dy > touchSlop
                    ) {

                        cancelPendingLongPress();
                    }
                }


                if (speedBoostActive) {
                    return true;
                }


            } else if (
                    action
                            ==
                            MotionEvent.ACTION_UP
                            ||
                            action
                                    ==
                                    MotionEvent.ACTION_CANCEL
            ) {

                final boolean wasBoosting =
                        speedBoostActive;


                cancelPendingLongPress();


                if (wasBoosting) {

                    endSpeedBoost();

                    return true;
                }
            }


            return super.dispatchTouchEvent(
                    event
            );
        }


        private boolean isSpeedGestureArea(
                MotionEvent event
        ) {

            /*
             * Reserva a zona inferior para a barra de progresso
             * e restantes controlos do Media3.
             *
             * O gesto 2× fica disponível no resto da imagem.
             */
            return event.getY()
                    <
                    getHeight()
                            *
                            0.72f;
        }


        private void beginSpeedBoost() {

            if (
                    !trackingLongPress
                            ||
                            player == null
            ) {
                return;
            }


            speedBeforeBoost =
                    player
                            .getPlaybackParameters()
                            .speed;


            /*
             * Cancela qualquer toque que tenha começado num controlo
             * antes de transformar o gesto num "manter para 2×".
             */
            MotionEvent cancelEvent =
                    MotionEvent.obtain(
                            android.os.SystemClock
                                    .uptimeMillis(),
                            android.os.SystemClock
                                    .uptimeMillis(),
                            MotionEvent.ACTION_CANCEL,
                            downX,
                            downY,
                            0
                    );


            try {

                super.dispatchTouchEvent(
                        cancelEvent
                );

            } finally {

                cancelEvent.recycle();
            }


            try {

                player.setPlaybackParameters(
                        new PlaybackParameters(
                                2.0f
                        )
                );

                speedBoostActive = true;

                hideController();

                setSpeedIndicatorVisible(
                        true
                );

                MainActivity.this.vibrate(
                        18
                );

            } catch (Exception ignored) {

                speedBoostActive = false;

                setSpeedIndicatorVisible(
                        false
                );
            }
        }


        private void endSpeedBoost() {

            if (!speedBoostActive) {
                return;
            }


            try {

                if (player != null) {

                    player.setPlaybackParameters(
                            new PlaybackParameters(
                                    speedBeforeBoost
                            )
                    );
                }

            } catch (Exception ignored) {
            }


            speedBoostActive = false;

            setSpeedIndicatorVisible(
                    false
            );
        }


        private void cancelPendingLongPress() {

            trackingLongPress = false;

            removeCallbacks(
                    longPressRunnable
            );
        }


        void cancelSpeedGesture() {

            cancelPendingLongPress();

            endSpeedBoost();
        }
    }


    private void setVideoStatus(
            String text,
            boolean visible
    ) {

        if (videoStatus == null) {
            return;
        }

        videoStatus.setText(
                text == null
                        ?
                        ""
                        :
                        text
        );

        videoStatus.setVisibility(
                visible
                        ?
                        View.VISIBLE
                        :
                        View.GONE
        );
    }



    private boolean assetExists(
            String assetPath
    ) {

        try (
                InputStream ignored =
                        getAssets().open(
                                assetPath
                        )
        ) {

            return true;

        } catch (Exception e) {

            return false;
        }
    }


    /*
     * Os vídeos mantêm os nomes originais enviados para assets/videos.
     *
     * Cada exercício é associado por um conjunto de palavras únicas.
     * Isto evita renomear 41 ficheiros e também permite que os dois
     * exercícios repetidos de sexta reutilizem o mesmo vídeo físico.
     */
    private String[] requiredVideoTokens(
            String exerciseId
    ) {

        if (exerciseId == null) {
            return null;
        }

        return switch (exerciseId) {

            // SEGUNDA
            case "mon-smith" ->
                    new String[]{"hack", "squat", "machine"};

            case "mon-legpress" ->
                    new String[]{"perfect", "leg", "press"};

            case "mon-legcurl-seated" ->
                    new String[]{"seated", "leg", "curl", "correctly", "exit"};

            case "mon-extension" ->
                    new String[]{"beinstrecker", "maschine"};

            case "mon-adductor" ->
                    new String[]{"hip", "adduction", "machine", "perfect", "technique"};

            case "mon-calves" ->
                    new String[]{"machine", "standing", "calf", "raise"};

            case "mon-tibialis" ->
                    new String[]{"tibialis", "raises", "why"};

            case "mon-ab-supra" ->
                    new String[]{"abdominal", "crunch", "hammer", "strength"};

            case "mon-legraise" ->
                    new String[]{"captain", "chair", "knee", "raise"};


            // TERÇA
            case "tue-incline", "fri-incline" ->
                    new String[]{"incline", "chest", "press", "exercise", "form", "guide"};

            case "tue-flat" ->
                    new String[]{"life", "fitness", "signature", "chest", "press", "instructions"};

            case "tue-fly" ->
                    new String[]{"pec", "deck", "machine", "60", "seconds"};

            case "tue-lateral" ->
                    new String[]{"seated", "machine", "lat", "demonstration", "guide"};

            case "tue-shoulderpress" ->
                    new String[]{"perform", "machine", "shoulder", "press"};

            case "tue-reversefly" ->
                    new String[]{"reverse", "pec", "deck", "shoulder", "health"};

            case "tue-cablecurl-bar" ->
                    new String[]{"straight","bar","cable","curl","good","form"};

            case "tue-ohtri", "fri-overheadtri-rope" ->
                    new String[]{"standing", "overhead", "cable", "triceps", "extension", "rope"};

            case "tue-pushdown", "fri-pushdown" ->
                    new String[]{"properly", "tricep", "rope", "mistake"};

            case "tue-reversepush" ->
                    new String[]{"reverse", "grip", "pushdown", "life", "fitness"};


            // QUARTA
            case "wed-neutral-pd" ->
                    new String[]{"mag", "grip", "lat", "pulldown"};

            case "wed-close-row" ->
                    new String[]{"seated", "cable", "rows", "bodybuilding"};

            case "wed-chestsupported-row" ->
                    new String[]{"plate", "loaded", "row", "pronated", "grip"};

            case "wed-straightarm-pd" ->
                    new String[]{"straight", "arm", "rope", "pulldown"};

            case "wed-seated-calves" ->
                    new String[]{"seated", "calf", "raise", "machine"};

            case "wed-scott" ->
                    new String[]{"preacher", "curl", "machine"};

            case "wed-inclinecurl-db" ->
                    new String[]{"incline","dumbbell","curl","how","to","form","tutorial"};

            case "wed-bayesian" ->
                    new String[]{"bayesian", "cable", "curl"};

            case "wed-hammer" ->
                    new String[]{"properly", "dumbbell", "hammer", "curl"};

            case "wed-reversecurl" ->
                    new String[]{"hunter", "labrada", "reverse", "ez", "bar", "curl"};

            case "wed-wristflex" ->
                    new String[]{"dumbbell", "seated", "wrist", "curls"};


            // QUINTA
            case "thu-hipthrust" ->
                    new String[]{"hip", "thrust", "machine", "plate", "loaded", "team", "royal"};

            case "thu-legcurl-seated" ->
                    new String[]{"seated", "leg", "curls", "stronger", "hamstrings"};

            case "thu-legpress" ->
                    new String[]{"machine", "leg", "press", "horizontal"};

            case "thu-rearkick" ->
                    new String[]{"rear", "glute", "kick", "machine"};

            case "thu-abductor" ->
                    new String[]{"seated", "hip", "abductor", "outer", "thigh"};

            case "thu-backextension" ->
                    new String[]{"seated", "back", "extension", "tutorial", "beginners"};

            case "thu-sidebend" ->
                    new String[]{"ab", "coaster", "obliques"};


            // SEXTA
            case "fri-flat" ->
                    new String[]{"hammer", "strength", "correct", "form", "positioning"};

            case "fri-neutral-pd" ->
                    new String[]{"underhand", "lat", "pulldown", "exercise", "library"};

            case "fri-open-row" ->
                    new String[]{"wide", "grip", "seated", "cable", "row"};

            case "fri-lateral" ->
                    new String[]{"elevacao", "unilateral", "polia", "jeito", "certo", "fazer"};

            case "fri-shrug" ->
                    new String[]{"properly", "dumbbell", "shrug"};

            case "fri-barcurl" ->
                    new String[]{"ez", "bar", "curl", "easy", "minute", "tutorial"};

            /*
             * wed-wristext está inactivo no plano final e deliberadamente
             * não recebe vídeo.
             */
            default ->
                    null;
        };
    }


    private String normalizeVideoName(
            String value
    ) {

        if (value == null) {
            return "";
        }

        String normalized =
                Normalizer.normalize(
                        value,
                        Normalizer.Form.NFD
                )
                        .replaceAll(
                                "\\p{M}+",
                                ""
                        )
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .replaceAll(
                                "[^a-z0-9]+",
                                " "
                        )
                        .trim();

        return normalized.replaceAll(
                "\\s+",
                " "
        );
    }


    private boolean filenameContainsEveryToken(
            String normalizedFilename,
            String[] tokens
    ) {

        if (
                normalizedFilename == null
                        ||
                        tokens == null
                        ||
                        tokens.length == 0
        ) {
            return false;
        }

        for (String token : tokens) {

            String normalizedToken =
                    normalizeVideoName(
                            token
                    );

            if (
                    normalizedToken.isEmpty()
                            ||
                            !normalizedFilename.contains(
                                    normalizedToken
                            )
            ) {

                return false;
            }
        }

        return true;
    }


    private String resolveVideoFilename(
            String exerciseId
    ) {

        String[] tokens =
                requiredVideoTokens(
                        exerciseId
                );

        if (tokens == null) {
            return null;
        }

        try {

            String[] files =
                    getAssets().list(
                            "videos"
                    );

            if (files == null) {
                return null;
            }

            for (String filename : files) {

                if (
                        filename == null
                                ||
                                !filename
                                        .toLowerCase(
                                                Locale.ROOT
                                        )
                                        .endsWith(
                                                ".mp4"
                                        )
                ) {
                    continue;
                }

                String normalized =
                        normalizeVideoName(
                                filename
                        );

                if (
                        filenameContainsEveryToken(
                                normalized,
                                tokens
                        )
                ) {

                    return filename;
                }
            }

        } catch (Exception ignored) {
        }

        return null;
    }


    private void openTechniqueVideo(
            String exerciseId
    ) {

        ensureVideoOverlay();

        closePlayerOnly();

        videoOverlay.animate().cancel();
        videoOverlay.setAlpha(0f);
        videoOverlay.setScaleX(0.985f);
        videoOverlay.setScaleY(0.985f);
        videoOverlay.setVisibility(
                View.VISIBLE
        );

        videoOverlay.bringToFront();
        videoOverlay.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180L)
                .start();
        vibrate(8);

        setVideoStatus(
                "A carregar vídeo…",
                true
        );

        setKeepScreenOn(true);


        String videoFilename =
                resolveVideoFilename(
                        exerciseId
                );


        if (videoFilename == null) {

            setVideoStatus(
                    "Vídeo não encontrado para este exercício.",
                    true
            );

            Toast.makeText(
                    this,
                    "Não encontrei o vídeo associado em assets/videos.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }


        String assetPath =
                "videos/"
                        +
                        videoFilename;


        if (
                !assetExists(
                        assetPath
                )
        ) {

            setVideoStatus(
                    "O vídeo associado não está disponível nesta build.",
                    true
            );

            Toast.makeText(
                    this,
                    "O ficheiro associado não foi incluído no APK.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }


        Uri videoUri =
                Uri.parse(
                        "asset:///videos/"
                                +
                                Uri.encode(
                                        videoFilename
                                )
                );


        try {

            player =
                    new ExoPlayer.Builder(this)
                            .build();


            player.addListener(
                    new Player.Listener() {

                        @Override
                        public void onPlaybackStateChanged(
                                int playbackState
                        ) {

                            if (
                                    playbackState
                                            ==
                                            Player.STATE_READY
                            ) {

                                setVideoStatus(
                                        "",
                                        false
                                );

                            } else if (
                                    playbackState
                                            ==
                                            Player.STATE_BUFFERING
                            ) {

                                setVideoStatus(
                                        "A carregar vídeo…",
                                        true
                                );
                            }
                        }


                        @Override
                        public void onPlayerError(
                                PlaybackException error
                        ) {

                            setVideoStatus(
                                    "Não foi possível reproduzir este vídeo.",
                                    true
                            );

                            Toast.makeText(
                                    MainActivity.this,
                                    "Erro no player: "
                                            +
                                            error.getErrorCodeName(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
            );


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


        } catch (Exception e) {

            closePlayerOnly();

            setVideoStatus(
                    "Não foi possível iniciar o player.",
                    true
            );

            Toast.makeText(
                    this,
                    "Erro ao iniciar o vídeo.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }


    private void closePlayerOnly() {

        if (playerView != null) {

            playerView.cancelSpeedGesture();
        }


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
            videoOverlay.animate().cancel();
            if (videoOverlay.getVisibility() == View.VISIBLE) {
                videoOverlay.animate()
                        .alpha(0f)
                        .scaleX(0.985f)
                        .scaleY(0.985f)
                        .setDuration(145L)
                        .withEndAction(() -> {
                            videoOverlay.setVisibility(View.GONE);
                            videoOverlay.setAlpha(1f);
                            videoOverlay.setScaleX(1f);
                            videoOverlay.setScaleY(1f);
                        })
                        .start();
                vibrate(6);
            } else {
                videoOverlay.setVisibility(View.GONE);
                videoOverlay.setAlpha(1f);
                videoOverlay.setScaleX(1f);
                videoOverlay.setScaleY(1f);
            }
        }


        setVideoStatus(
                "",
                false
        );


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


        if (playerView != null) {

            playerView.cancelSpeedGesture();
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

        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher()
                .unregisterOnBackInvokedCallback(backCallback);
            backCallback = null;
        }

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
        videoStatus = null;
        speedIndicator = null;
        rootLayout = null;


        super.onDestroy();
    }


    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        handleAppBack();
    }

    private void handleAppBack() {
        if (videoOverlay != null
      && videoOverlay.getVisibility() == View.VISIBLE) {
  closeTechniqueVideo();
  return;
        }

        if (webView == null) {
  finish();
  return;
        }

        webView.evaluateJavascript(
  "try{String(!!(window.handleAndroidBack&&window.handleAndroidBack()))}catch(e){'false'}",
  result -> {
      if ("\\\"true\\\"".equals(result)) {
          return;
      }

      if (webView != null && webView.canGoBack()) {
          webView.goBack();
      } else {
          finish();
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
        public void setWeightReminderEnabled(boolean enabled) {
            WeightReminderScheduler.setEnabled(
                MainActivity.this, enabled
            );
        }

        @JavascriptInterface
        public String appVersion() {

            return APP_VERSION;
        }
    }
}
