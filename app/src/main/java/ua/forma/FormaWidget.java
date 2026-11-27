package ua.forma;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// Віджет на робочому столі: ккал, білки і вода за сьогодні
public class FormaWidget extends AppWidgetProvider {
    static final String PREFS = "forma_widget";

    static void save(Context ctx, String json) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("data", json).apply();
        updateAll(ctx);
    }

    static void updateAll(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        int[] ids = m.getAppWidgetIds(new ComponentName(ctx, FormaWidget.class));
        for (int id : ids) m.updateAppWidget(id, build(ctx));
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(ctx));
    }

    private static int pct(int v, int t) {
        if (t <= 0) return 0;
        return Math.max(0, Math.min(100, Math.round(v * 100f / t)));
    }

    private static String liters(int ml) {
        float l = ml / 1000f;
        String s = String.format(Locale.US, "%.1f", l);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    static RemoteViews build(Context ctx) {
        int k = 0, kt = 2000, p = 0, pt = 100, w = 0, wt = 2000;
        boolean en = false;
        try {
            JSONObject o = new JSONObject(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("data", "{}"));
            kt = o.optInt("kt", kt); pt = o.optInt("pt", pt); wt = o.optInt("wt", wt);
            en = o.optInt("en", 0) == 1;
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            if (today.equals(o.optString("d", ""))) { k = o.optInt("k", 0); p = o.optInt("p", 0); w = o.optInt("w", 0); }
        } catch (Exception e) { /* немає даних — показуємо нулі */ }

        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        int left = kt - k;
        v.setTextViewText(R.id.wBig, String.valueOf(Math.abs(left)));
        v.setTextViewText(R.id.wBigL, left < 0 ? (en ? "kcal over" : "ккал перебір") : (en ? "kcal left" : "ккал залишилось"));
        v.setTextViewText(R.id.wK, (en ? "Kcal " : "Ккал ") + k + " / " + kt);
        v.setTextViewText(R.id.wP, (en ? "Protein " : "Білки ") + p + " / " + pt + (en ? " g" : " г"));
        v.setTextViewText(R.id.wW, (en ? "Water " : "Вода ") + liters(w) + " / " + liters(wt) + (en ? " L" : " л"));
        v.setProgressBar(R.id.wKb, 100, pct(k, kt), false);
        v.setProgressBar(R.id.wPb, 100, pct(p, pt), false);
        v.setProgressBar(R.id.wWb, 100, pct(w, wt), false);
        PendingIntent open = PendingIntent.getActivity(ctx, 0,
                new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.wRoot, open);
        return v;
    }
}
