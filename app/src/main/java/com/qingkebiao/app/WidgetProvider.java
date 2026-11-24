package com.qingkebiao.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 4×4 现代日程卡片小部件：
 * - 半透明磨砂感底色（浅色/深色/全透明 + 0-100% 透明度可调）
 * - 每门课左侧固定彩色竖条（与周课表同课同色）
 * - 未来 7 天课程，精准星期标签 + 教室胶囊 + 顶部一键刷新 + 底部考试倒计时胶囊
 */
public class WidgetProvider extends AppWidgetProvider {

    private static final int MAX_ROWS = 7;

    private static final int[] ROW_ID = {R.id.row1, R.id.row2, R.id.row3, R.id.row4,
            R.id.row5, R.id.row6, R.id.row7};
    private static final int[] D_ID = {R.id.d1, R.id.d2, R.id.d3, R.id.d4,
            R.id.d5, R.id.d6, R.id.d7};
    private static final int[] BAR_ID = {R.id.bar1, R.id.bar2, R.id.bar3, R.id.bar4,
            R.id.bar5, R.id.bar6, R.id.bar7};
    private static final int[] T_ID = {R.id.t1, R.id.t2, R.id.t3, R.id.t4,
            R.id.t5, R.id.t6, R.id.t7};
    private static final int[] N_ID = {R.id.n1, R.id.n2, R.id.n3, R.id.n4,
            R.id.n5, R.id.n6, R.id.n7};
    private static final int[] R_ID = {R.id.r1, R.id.r2, R.id.r3, R.id.r4,
            R.id.r5, R.id.r6, R.id.r7};

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) {
            mgr.updateAppWidget(id, build(context));
        }
    }

    @Override
    public void onEnabled(Context context) {
        Scheduler.schedule(context);
    }

    public static void updateAll(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        ComponentName cn = new ComponentName(context, WidgetProvider.class);
        int[] ids = mgr.getAppWidgetIds(cn);
        if (ids != null && ids.length > 0) {
            RemoteViews rv = build(context);
            for (int id : ids) mgr.updateAppWidget(id, rv);
        }
        WeekWidgetProvider.updateAll(context);
    }

    public static void updateAllAsync(final Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    updateAll(context);
                } catch (Throwable ignored) {
                }
            }
        }).start();
    }

    private static final class Row {
        String day = "";
        String time = "";
        String name = "";
        String room = "";
        int accentColor;
        boolean isToday;
    }

    public static RemoteViews build(Context ctx) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_today);

        int mode = Theme.widgetMode(ctx);
        int opacity = Theme.widgetOpacity(ctx);
        boolean dark = Theme.isDark(mode);

        if (mode == Theme.MODE_TRANSPARENT) {
            rv.setViewVisibility(R.id.bg_img, View.INVISIBLE);
        } else {
            rv.setViewVisibility(R.id.bg_img, View.VISIBLE);
            rv.setImageViewResource(R.id.bg_img,
                    mode == Theme.MODE_DARK ? R.drawable.widget_bg_dark : R.drawable.widget_bg_light);
            int alpha255 = Math.max(15, Math.min(255, (int) Math.round(opacity * 2.55)));
            rv.setInt(R.id.bg_img, "setImageAlpha", alpha255);
        }

        int pri = Theme.primaryText(mode);
        int sec = Theme.secondaryText(mode);
        int accent = Theme.accentBlue(mode);
        int chipBg = dark ? R.drawable.chip_bg_dark : R.drawable.chip_bg_light;

        int week = Prefs.currentWeek(ctx);
        rv.setTextViewText(R.id.hdr_date, Prefs.todayLabel());
        rv.setTextColor(R.id.hdr_date, pri);

        rv.setTextViewText(R.id.hdr_week, "第" + week + "周");
        rv.setTextColor(R.id.hdr_week, accent);
        rv.setInt(R.id.hdr_week, "setBackgroundResource", chipBg);

        rv.setTextColor(R.id.btn_refresh, sec);
        rv.setInt(R.id.hdr_div, "setBackgroundColor", Theme.dividerColor(mode));

        List<Row> rows = buildRows(ctx, week);
        boolean empty = rows.isEmpty();

        for (int i = 0; i < MAX_ROWS; i++) {
            if (i < rows.size()) {
                Row r = rows.get(i);
                rv.setViewVisibility(ROW_ID[i], View.VISIBLE);

                rv.setTextViewText(D_ID[i], r.day);
                rv.setTextColor(D_ID[i], r.isToday ? accent : sec);

                rv.setInt(BAR_ID[i], "setColorFilter", r.accentColor);

                rv.setTextViewText(T_ID[i], r.time);
                rv.setTextColor(T_ID[i], sec);

                rv.setTextViewText(N_ID[i], r.name);
                rv.setTextColor(N_ID[i], pri);

                if (r.room == null || r.room.isEmpty()) {
                    rv.setViewVisibility(R_ID[i], View.GONE);
                } else {
                    rv.setViewVisibility(R_ID[i], View.VISIBLE);
                    rv.setTextViewText(R_ID[i], trim(r.room, 9));
                    rv.setTextColor(R_ID[i], sec);
                    rv.setInt(R_ID[i], "setBackgroundResource", chipBg);
                }
            } else {
                rv.setViewVisibility(ROW_ID[i], empty ? View.GONE : View.INVISIBLE);
            }
        }

        rv.setViewVisibility(R.id.empty, empty ? View.VISIBLE : View.GONE);
        if (empty) {
            rv.setTextViewText(R.id.empty, emptyText(ctx));
            rv.setTextColor(R.id.empty, sec);
        }

        String exam = Theme.showExamInWidget(ctx) ? examText(ctx) : "";
        if (exam.isEmpty()) {
            rv.setViewVisibility(R.id.hdr_exam, View.GONE);
        } else {
            rv.setViewVisibility(R.id.hdr_exam, View.VISIBLE);
            rv.setTextViewText(R.id.hdr_exam, exam);
            rv.setTextColor(R.id.hdr_exam, Theme.examTextColor(mode));
            rv.setInt(R.id.hdr_exam, "setBackgroundResource",
                    dark ? R.drawable.exam_strip_dark : R.drawable.exam_strip_light);
        }

        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(ctx, 10, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.widget_root, piOpen);

        Intent ref = new Intent(ctx, RefreshReceiver.class);
        ref.setAction(RefreshReceiver.ACTION_REFRESH);
        PendingIntent piRef = PendingIntent.getBroadcast(ctx, 11, ref,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.btn_refresh, piRef);

        return rv;
    }

    private static List<Row> buildRows(Context ctx, int week) {
        List<Row> rows = new ArrayList<>();
        List<Course> all = Prefs.courses(ctx);
        if (all.isEmpty()) return rows;
        Map<Integer, String> times = Prefs.nodeTime(ctx);
        int todayDow = Prefs.todayDow();

        for (int off = 0; off < 7 && rows.size() < MAX_ROWS; off++) {
            int dow = ((todayDow - 1 + off) % 7) + 1;
            int w = week + ((todayDow - 1 + off) / 7);
            List<Course> day = new ArrayList<>();
            for (Course c : all) {
                if (c.dayOfWeek == dow && c.activeInWeek(w)) day.add(c);
            }
            Collections.sort(day, new Comparator<Course>() {
                @Override
                public int compare(Course a, Course b) {
                    return Integer.compare(a.startNode, b.startNode);
                }
            });
            for (int i = 0; i < day.size() && rows.size() < MAX_ROWS; i++) {
                Course c = day.get(i);
                Row r = new Row();
                r.day = i == 0 ? dayLabel(todayDow, off) : "";
                r.isToday = (off == 0);
                r.name = c.name;
                r.room = c.room;
                r.accentColor = Theme.accentColor(c.name);
                String st = times.get(c.startNode);
                String period = (c.startNode == c.endNode)
                        ? (c.startNode + "节")
                        : (c.startNode + "-" + c.endNode + "节");
                r.time = (st == null ? "" : st + "\n") + period;
                rows.add(r);
            }
        }
        return rows;
    }

    public static String dayLabel(int todayDow, int offset) {
        return Prefs.dayLabel(todayDow, offset);
    }

    private static String emptyText(Context ctx) {
        if (Prefs.courses(ctx).isEmpty()) {
            String msg = Prefs.lastMessage(ctx);
            if (msg != null && !msg.isEmpty() && !msg.startsWith("已更新")) return msg;
            return "还没有课表数据\n点这里打开「轻课表」导入";
        }
        return "最近 7 天没课";
    }

    private static String examText(Context ctx) {
        Exam e = Prefs.nextExam(ctx);
        if (e == null) return "";
        StringBuilder sb = new StringBuilder("考试 · ");
        sb.append(trim(e.name, 10));
        if (e.startMillis > 0) {
            long days = Prefs.daysUntil(e.startMillis, System.currentTimeMillis());
            sb.append("  ").append(Prefs.fmtTime(e.startMillis));
            if (days == 0) sb.append(" (今天)");
            else if (days > 0) sb.append(" (").append(days).append("天后)");
        } else if (!e.timeRaw.isEmpty()) {
            sb.append("  ").append(e.timeRaw);
        }
        if (!e.room.isEmpty()) sb.append(" · ").append(e.room);
        return sb.toString();
    }

    private static String trim(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
