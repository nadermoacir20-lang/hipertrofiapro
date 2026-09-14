package mz.nader.hipertrofiapro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

public class RestTimerService extends Service {
    public static final String ACTION_START = "mz.nader.hipertrofiapro.action.START_REST";
    public static final String ACTION_STOP = "mz.nader.hipertrofiapro.action.STOP_REST";
    public static final String EXTRA_SECONDS = "seconds";

    private static final String PREFS = "hp_native";
    private static final String KEY_END_AT = "rest_end_at";
    private static final String CHANNEL_ID = "rest_timer";
    private static final int NOTIFICATION_ID = 6101;
    private static final int COMPLETE_NOTIFICATION_ID = 6102;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private long endAt;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopTimer();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {
            int seconds = intent.getIntExtra(EXTRA_SECONDS, 90);
            seconds = Math.max(15, Math.min(seconds, 175));
            endAt = System.currentTimeMillis() + seconds * 1000L;
            prefs.edit().putLong(KEY_END_AT, endAt).apply();
        } else {
            endAt = prefs.getLong(KEY_END_AT, 0L);
        }

        if (endAt <= System.currentTimeMillis()) {
            stopTimer();
            return START_NOT_STICKY;
        }

        Notification n = buildRunningNotification(endAt);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
        scheduleFinish();
        return START_STICKY;
    }

    private void scheduleFinish() {
        handler.removeCallbacksAndMessages(null);
        long delay = Math.max(0L, endAt - System.currentTimeMillis());
        handler.postDelayed(this::finishTimer, delay);
    }

    private Notification buildRunningNotification(long endTime) {
        PendingIntent openApp = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Descanso")
                .setContentText("Próxima série quando o temporizador terminar")
                .setContentIntent(openApp)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STOPWATCH)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setWhen(endTime)
                .setUsesChronometer(true);

        if (Build.VERSION.SDK_INT >= 24) b.setChronometerCountDown(true);
        return b.build();
    }

    private Notification buildCompleteNotification() {
        PendingIntent openApp = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Descanso terminado")
                .setContentText("Pronto para a próxima série.")
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setPriority(Notification.PRIORITY_HIGH)
                .build();
    }

    private void finishTimer() {
        prefs.edit().remove(KEY_END_AT).apply();
        vibrate();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(COMPLETE_NOTIFICATION_ID, buildCompleteNotification());
        stopForeground(true);
        stopSelf();
    }

    private void stopTimer() {
        handler.removeCallbacksAndMessages(null);
        prefs.edit().remove(KEY_END_AT).apply();
        stopForeground(true);
        stopSelf();
    }

    private void vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) vm.getDefaultVibrator().vibrate(VibrationEffect.createWaveform(new long[]{0, 180, 90, 220}, -1));
            } else {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 180, 90, 220}, -1));
                    else v.vibrate(new long[]{0, 180, 90, 220}, -1);
                }
            }
        } catch (Exception ignored) {}
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Temporizador de descanso",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Avisos do temporizador entre séries");
            channel.enableVibration(true);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
