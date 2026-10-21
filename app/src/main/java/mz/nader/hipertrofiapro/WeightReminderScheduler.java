package mz.nader.hipertrofiapro;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

final class WeightReminderScheduler {
    static final String CHANNEL_ID = "weekly_weight";
    private static final String PREFS = "hp_weight_reminder";
    private static final int REQUEST_CODE = 7001;
    private static final int NOTIFICATION_ID = 7101;

    private WeightReminderScheduler() {}

    static boolean isEnabled(Context context) {
        return context.getSharedPreferences(
      PREFS, Context.MODE_PRIVATE
        ).getBoolean("enabled", true);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(
      PREFS, Context.MODE_PRIVATE
        ).edit().putBoolean("enabled", enabled).apply();

        if (enabled) {
  scheduleNext(context);
        } else {
  cancel(context);
        }
    }

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;

        NotificationManager manager =
      context.getSystemService(
              NotificationManager.class
      );

        if (manager == null) return;

        NotificationChannel channel = new NotificationChannel(
      CHANNEL_ID,
      "Registo semanal de peso",
      NotificationManager.IMPORTANCE_DEFAULT
        );

        channel.setDescription(
      "Lembrete semanal para actualizar o peso corporal."
        );

        manager.createNotificationChannel(channel);
    }

    private static PendingIntent reminderIntent(Context context) {
        Intent intent = new Intent(
      context, WeightReminderReceiver.class
        );

        return PendingIntent.getBroadcast(
      context,
      REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT
              | PendingIntent.FLAG_IMMUTABLE
        );
    }

    static void cancel(Context context) {
        AlarmManager alarm =
      (AlarmManager) context.getSystemService(
              Context.ALARM_SERVICE
      );

        PendingIntent pending = reminderIntent(context);

        if (alarm != null) {
  alarm.cancel(pending);
        }

        pending.cancel();

        NotificationManager manager =
      (NotificationManager) context.getSystemService(
              Context.NOTIFICATION_SERVICE
      );

        if (manager != null) {
  manager.cancel(NOTIFICATION_ID);
        }
    }

    static void scheduleNext(Context context) {
        if (!isEnabled(context)) {
  cancel(context);
  return;
        }

        ensureChannel(context);

        AlarmManager alarm =
      (AlarmManager) context.getSystemService(
              Context.ALARM_SERVICE
      );

        if (alarm == null) return;

        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();

        next.set(Calendar.HOUR_OF_DAY, 9);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);

        int today = now.get(Calendar.DAY_OF_WEEK);
        int daysUntilMonday =
      (Calendar.MONDAY - today + 7) % 7;

        if (daysUntilMonday == 0 && !next.after(now)) {
  daysUntilMonday = 7;
        }

        next.add(
      Calendar.DAY_OF_YEAR,
      daysUntilMonday
        );

        alarm.setAndAllowWhileIdle(
      AlarmManager.RTC_WAKEUP,
      next.getTimeInMillis(),
      reminderIntent(context)
        );
    }
}
