package mz.nader.hipertrofiapro;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

final class NativeStateStore {
    static final String PREFS = "hp_native_v7";
    static final String KEY_SNAPSHOT = "snapshot";
    static final String KEY_SAVED_AT = "saved_at";
    static final String KEY_LAST_WEIGHT_DATE = "last_weight_date";
    static final String KEY_NOTIFICATION_PROMPTED = "notification_prompted";

    private final AtomicFile atomicFile;
    private final SharedPreferences prefs;
    private final SharedPreferences legacyPrefs;

    NativeStateStore(Context context) {
        Context app = context.getApplicationContext();
        atomicFile = new AtomicFile(new File(app.getFilesDir(), "hipertrofia_state_v7.json"));
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        legacyPrefs = app.getSharedPreferences("hp_native", Context.MODE_PRIVATE);
    }

    synchronized void save(String json) {
        if (json == null || json.trim().isEmpty()) return;
        long savedAt = System.currentTimeMillis();

        FileOutputStream out = null;
        try {
            out = atomicFile.startWrite();
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.flush();
            atomicFile.finishWrite(out);
            out = null;
        } catch (Exception e) {
            if (out != null) atomicFile.failWrite(out);
        }

        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_SNAPSHOT, json)
                .putLong(KEY_SAVED_AT, savedAt);
        String latestWeightDate = latestWeightDate(json);
        if (latestWeightDate != null) editor.putString(KEY_LAST_WEIGHT_DATE, latestWeightDate);
        else editor.remove(KEY_LAST_WEIGHT_DATE);
        editor.apply();
    }

    synchronized String loadLatest() {
        String atomic = readAtomic();
        String fallback = prefs.getString(KEY_SNAPSHOT, null);
        String chosen = fresher(atomic, fallback);
        if (chosen != null && !chosen.isEmpty()) return chosen;

        // Compatibility fallback for an older v6 APK using the same package id.
        return legacyPrefs.getString("snapshot", "");
    }

    SharedPreferences prefs() {
        return prefs;
    }

    private String readAtomic() {
        if (!atomicFile.getBaseFile().exists()) return null;
        try (FileInputStream in = atomicFile.openRead(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return null;
        }
    }

    private static String fresher(String a, String b) {
        if (a == null || a.trim().isEmpty()) return b;
        if (b == null || b.trim().isEmpty()) return a;
        return stateTimestamp(a) >= stateTimestamp(b) ? a : b;
    }

    private static long stateTimestamp(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            if (obj.has("updatedAt")) return obj.optLong("updatedAt", 0L);
            JSONObject state = obj.optJSONObject("state");
            if (state != null) return state.optLong("updatedAt", 0L);
        } catch (Exception ignored) {}
        return 0L;
    }

    private static String latestWeightDate(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject state = root.optJSONObject("state");
            if (state == null) state = root;
            JSONArray arr = state.optJSONArray("weightHistory");
            if (arr == null) return null;
            String latest = null;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                String date = item.optString("date", "");
                if (date.matches("\\d{4}-\\d{2}-\\d{2}") && (latest == null || date.compareTo(latest) > 0)) {
                    latest = date;
                }
            }
            return latest;
        } catch (Exception e) {
            return null;
        }
    }
}
