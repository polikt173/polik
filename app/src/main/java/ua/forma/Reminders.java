package ua.forma;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;

// Зберігає список нагадувань і ставить будильники на найближчий час кожного
public class Reminders {
    static final String PREFS = "forma_rem";

    static void save(Context ctx, String json) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("list", json).apply();
        schedule(ctx);
    }

    static void schedule(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        int old = p.getInt("count", 0);
        for (int i = 0; i < old; i++) {
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 1000 + i, new Intent(ctx, ReminderReceiver.class),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) { am.cancel(pi); pi.cancel(); }
        }
        int n = 0;
        try {
            JSONArray arr = new JSONArray(p.getString("list", "[]"));
            for (int i = 0; i < arr.length() && i < 200; i++) {
                JSONObject o = arr.getJSONObject(i);
                String[] hm = o.optString("time", "08:00").split(":");
                int h = Integer.parseInt(hm[0].trim()), m = hm.length > 1 ? Integer.parseInt(hm[1].trim().substring(0, 2)) : 0;
                Calendar c = Calendar.getInstance();
                c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
                if (c.getTimeInMillis() <= System.currentTimeMillis() + 1000) c.add(Calendar.DAY_OF_YEAR, 1);
                Intent it = new Intent(ctx, ReminderReceiver.class)
                        .putExtra("title", o.optString("title", "Форма"))
                        .putExtra("text", o.optString("text", ""))
                        .putExtra("code", 1000 + i)
                        .putExtra("meal", o.optString("meal", ""));
                PendingIntent pi = PendingIntent.getBroadcast(ctx, 1000 + i, it,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
                n++;
            }
        } catch (Exception e) { /* пошкоджений список — просто нічого не ставимо */ }
        p.edit().putInt("count", n).apply();
    }
}
