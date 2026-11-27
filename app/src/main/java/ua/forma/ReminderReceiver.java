package ua.forma;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || "android.intent.action.MY_PACKAGE_REPLACED".equals(intent.getAction())) {
            Reminders.schedule(ctx);
            return;
        }
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel("rem", "Нагадування", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);
        PendingIntent open = PendingIntent.getActivity(ctx, 0, new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE);
        int code = intent.getIntExtra("code", 1000);
        Notification n = new Notification.Builder(ctx, "rem")
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(Color.parseColor("#FF5A36"))
                .setContentTitle(intent.getStringExtra("title"))
                .setContentText(intent.getStringExtra("text"))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        try { nm.notify(code, n); } catch (SecurityException e) { /* немає дозволу на сповіщення */ }
        Reminders.schedule(ctx);
    }
}
