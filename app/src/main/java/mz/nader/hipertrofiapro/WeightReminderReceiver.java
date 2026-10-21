package mz.nader.hipertrofiapro;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class WeightReminderReceiver extends BroadcastReceiver {
    private static final int NOTIFICATION_ID = 7101;

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (!WeightReminderScheduler.isEnabled(context)) return;
            if (needsReminder(context)) showReminder(context);
        } finally {
            WeightReminderScheduler.scheduleNext(context);
        }
    }

    private boolean needsReminder(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(NativeStateStore.PREFS, Context.MODE_PRIVATE);
        String last = prefs.getString(NativeStateStore.KEY_LAST_WEIGHT_DATE, "");
        if (last == null || last.isEmpty()) return true;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            format.setLenient(false);
            Date d = format.parse(last);
            if (d == null) return true;
            long days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - d.getTime());
            return days >= 7;
        } catch (Exception e) {
            return true;
        }
    }

    private void showReminder(Context context) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        WeightReminderScheduler.ensureChannel(context);
        Intent launch = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, WeightReminderScheduler.CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_stat_fitness)
                .setContentTitle("Hipertrofia Pro")
                .setContentText("Segunda-feira: actualiza o teu peso corporal desta semana.")
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, builder.build());
    }
}
