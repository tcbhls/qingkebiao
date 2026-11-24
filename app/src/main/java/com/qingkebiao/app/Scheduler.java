package com.qingkebiao.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/** 定时刷新：每 3 小时更新小部件日期/周次；若开启 CAS 自动模式则同步后台拉取 */
public class Scheduler {

    public static final long INTERVAL_MS = 3L * 60 * 60 * 1000;

    public static void schedule(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(ctx, RefreshReceiver.class);
            i.setAction(RefreshReceiver.ACTION_REFRESH);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 1001, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 60_000L, INTERVAL_MS, pi);
        } catch (Exception ignored) {
        }
    }

    public static void cancel(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(ctx, RefreshReceiver.class);
            i.setAction(RefreshReceiver.ACTION_REFRESH);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 1001, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
        } catch (Exception ignored) {
        }
    }
}
