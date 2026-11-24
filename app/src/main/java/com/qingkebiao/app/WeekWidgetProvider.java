package com.qingkebiao.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 整周彩色网格小部件（4×4 / 5×4 可拉伸）。
 * 按控件实际内容区宽高比动态渲染 Bitmap，并以有效物理密度锚定字号，零拉伸变形且不超 Binder 预算。
 */
public class WeekWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_PREV_WEEK = "com.qingkebiao.app.ACTION_WEEK_PREV";
    public static final String ACTION_NEXT_WEEK = "com.qingkebiao.app.ACTION_WEEK_NEXT";
    public static final String ACTION_RESET_WEEK = "com.qingkebiao.app.ACTION_WEEK_RESET";

    private static final String[] DOW_SHORT = {"一", "二", "三", "四", "五", "六", "日"};

    private static final float PAD_LR = 10f;
    private static final float PAD_TOP = 10f;
    private static final float PAD_BOTTOM = 8f;
    private static final float HEADER_H = 28f;

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null || intent.getAction() == null) return;
        String act = intent.getAction();
        if (ACTION_PREV_WEEK.equals(act)) {
            Theme.setWeekWidgetOffset(context, Theme.weekWidgetOffset(context) - 1);
            updateAll(context);
        } else if (ACTION_NEXT_WEEK.equals(act)) {
            Theme.setWeekWidgetOffset(context, Theme.weekWidgetOffset(context) + 1);
            updateAll(context);
        } else if (ACTION_RESET_WEEK.equals(act)) {
            Theme.setWeekWidgetOffset(context, 0);
            updateAll(context);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) {
            try {
                Bundle o = mgr.getAppWidgetOptions(id);
                if (o != null) {
                    int[] sz = sizeFromOptions(o);
                    Prefs.saveWidgetSize(context, id, sz[0], sz[1]);
                }
            } catch (Throwable ignored) {
            }
            mgr.updateAppWidget(id, build(context, id));
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager mgr,
                                          int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, mgr, appWidgetId, newOptions);
        int[] sz = sizeFromOptions(newOptions);
        Prefs.saveWidgetSize(context, appWidgetId, sz[0], sz[1]);
        try {
            mgr.updateAppWidget(appWidgetId, build(context, appWidgetId));
        } catch (Throwable ignored) {
        }
    }

    private static int[] sizeFromOptions(Bundle o) {
        if (o == null) return new int[]{250, 250};
        int w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
        int maxW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0);
        int maxH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        int useW = w > 60 ? w : (maxW > 60 ? maxW : 250);
        int useH = maxH > 60 ? maxH : (h > 60 ? h : 250);
        if (useW > 1200) useW = 1200;
        if (useH > 1200) useH = 1200;
        return new int[]{useW, useH};
    }

    public static void updateAll(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        ComponentName cn = new ComponentName(context, WeekWidgetProvider.class);
        int[] ids = mgr.getAppWidgetIds(cn);
        if (ids == null || ids.length == 0) return;
        for (int id : ids) {
            try {
                Bundle o = mgr.getAppWidgetOptions(id);
                if (o != null) {
                    int[] sz = sizeFromOptions(o);
                    Prefs.saveWidgetSize(context, id, sz[0], sz[1]);
                }
            } catch (Throwable ignored) {
            }
            mgr.updateAppWidget(id, build(context, id));
        }
    }

    public static RemoteViews build(Context ctx, int widgetId) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_week);

        int mode = Theme.widgetMode(ctx);
        int opacity = Theme.widgetOpacity(ctx);
        boolean dark = Theme.isDark(mode);

        if (mode == Theme.MODE_TRANSPARENT) {
            rv.setViewVisibility(R.id.week_bg, View.INVISIBLE);
        } else {
            rv.setViewVisibility(R.id.week_bg, View.VISIBLE);
            rv.setImageViewResource(R.id.week_bg,
                    mode == Theme.MODE_DARK ? R.drawable.widget_bg_dark : R.drawable.widget_bg_light);
            int alpha255 = Math.max(15, Math.min(255, (int) Math.round(opacity * 2.55)));
            rv.setInt(R.id.week_bg, "setImageAlpha", alpha255);
        }

        int pri = Theme.primaryText(mode);
        int sec = Theme.secondaryText(mode);
        int curWeek = Prefs.currentWeek(ctx);
        int offset = Theme.weekWidgetOffset(ctx);
        int showWeek = Math.max(1, Math.min(30, curWeek + offset));

        rv.setTextViewText(R.id.week_title, "第" + showWeek + "周" + (offset == 0 ? "" : " (非本周)"));
        rv.setTextColor(R.id.week_title, offset == 0 ? pri : Theme.accentBlue(mode));

        rv.setTextViewText(R.id.week_sub, SchoolProfile.schoolName(ctx));
        rv.setTextColor(R.id.week_sub, sec);

        rv.setTextColor(R.id.btn_prev_week, sec);
        rv.setTextColor(R.id.btn_next_week, sec);
        rv.setTextColor(R.id.btn_week_refresh, sec);

        int[] widgetDp = Prefs.widgetSize(ctx, widgetId);
        Bitmap bmp = renderGridBitmap(ctx, showWeek, offset == 0, mode, dark, widgetDp[0], widgetDp[1]);
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                bmp = bmp.asShared();
            } catch (Throwable ignored) {
            }
        }
        rv.setImageViewBitmap(R.id.week_canvas, bmp);

        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(ctx, 20, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.week_canvas, piOpen);
        rv.setOnClickPendingIntent(R.id.week_sub, piOpen);

        rv.setOnClickPendingIntent(R.id.btn_prev_week, broadcastPi(ctx, ACTION_PREV_WEEK, 21));
        rv.setOnClickPendingIntent(R.id.btn_next_week, broadcastPi(ctx, ACTION_NEXT_WEEK, 22));
        rv.setOnClickPendingIntent(R.id.week_title, broadcastPi(ctx, ACTION_RESET_WEEK, 23));

        Intent ref = new Intent(ctx, RefreshReceiver.class);
        ref.setAction(RefreshReceiver.ACTION_REFRESH);
        PendingIntent piRef = PendingIntent.getBroadcast(ctx, 24, ref,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.btn_week_refresh, piRef);

        return rv;
    }

    private static PendingIntent broadcastPi(Context ctx, String action, int reqCode) {
        Intent i = new Intent(ctx, WeekWidgetProvider.class);
        i.setAction(action);
        return PendingIntent.getBroadcast(ctx, reqCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Bitmap renderGridBitmap(Context ctx, int showWeek, boolean isCurrentWeek,
                                           int mode, boolean dark,
                                           int widgetWDp, int widgetHDp) {
        float density = ctx.getResources().getDisplayMetrics().density;
        if (density <= 0) density = 2f;

        float contentWDp = Math.max(80f, widgetWDp - PAD_LR * 2);
        float contentHDp = Math.max(80f, widgetHDp - PAD_TOP - PAD_BOTTOM - HEADER_H);

        int budgetBytes = Build.VERSION.SDK_INT >= 31 ? 4_000_000 : 620_000;
        int w = Math.round(contentWDp * density);
        int h = Math.round(contentHDp * density);
        if (w < 80) w = 80;
        if (h < 80) h = 80;

        int capW = Build.VERSION.SDK_INT >= 31 ? 1080 : 420;
        if (w > capW) {
            float k = capW / (float) w;
            w = capW;
            h = Math.max(60, Math.round(h * k));
        }
        int guard = 0;
        while ((long) w * h * 4L > budgetBytes && guard++ < 12 && w > 60 && h > 60) {
            float k = 0.88f;
            w = Math.max(60, Math.round(w * k));
            h = Math.max(60, Math.round(h * k));
        }

        float pxPerDp = w / contentWDp;

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);

        List<Course> all = Prefs.courses(ctx);
        List<Course> weekCourses = new ArrayList<>();
        boolean hasWeekend = Theme.showWeekend(ctx);
        int highestNode = 0;
        for (Course c : all) {
            if (c.activeInWeek(showWeek)) {
                weekCourses.add(c);
                if (c.dayOfWeek >= 6) hasWeekend = true;
                if (c.endNode > highestNode) highestNode = c.endNode;
            }
        }
        int maxNode = highestNode <= 10 ? 10 : (highestNode <= 12 ? 12 : Math.min(14, highestNode));

        int cols = hasWeekend ? 7 : 5;
        float railW = Math.max(12f, 16f * pxPerDp);
        float headerH = Math.max(20f, 26f * pxPerDp);
        float colW = (w - railW) / cols;
        float rowH = Math.max(10f, (h - headerH - 2f * pxPerDp) / maxNode);

        int pri = Theme.primaryText(mode);
        int sec = Theme.secondaryText(mode);
        int lineColor = Theme.dividerColor(mode);
        int accent = Theme.accentBlue(mode);

        Paint pLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        pLine.setColor(lineColor);
        pLine.setStrokeWidth(Math.max(1f, 0.75f * pxPerDp));

        Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
        pText.setTextAlign(Paint.Align.CENTER);

        Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        pFill.setStyle(Paint.Style.FILL);

        Calendar mon = Prefs.firstMonday(ctx);
        mon.add(Calendar.DAY_OF_MONTH, (showWeek - 1) * 7);
        int todayDow = isCurrentWeek ? Prefs.todayDow() : -1;

        float dowSize = Math.max(8f, 10f * pxPerDp);
        float dateSize = Math.max(7f, 7.8f * pxPerDp);
        for (int c = 0; c < cols; c++) {
            float x0 = railW + c * colW;
            float cx = x0 + colW / 2f;
            boolean isTodayCol = (c + 1 == todayDow);
            if (isTodayCol) {
                pFill.setColor(dark ? 0x1A60A5FA : 0x142563EB);
                cv.drawRoundRect(new RectF(x0 + 0.8f * pxPerDp, 0.8f * pxPerDp,
                        x0 + colW - 0.8f * pxPerDp, h - 0.8f * pxPerDp),
                        5f * pxPerDp, 5f * pxPerDp, pFill);
            }

            Calendar dayCal = (Calendar) mon.clone();
            dayCal.add(Calendar.DAY_OF_MONTH, c);
            String dateStr = (dayCal.get(Calendar.MONTH) + 1) + "/" + dayCal.get(Calendar.DAY_OF_MONTH);

            pText.setTypeface(isTodayCol ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            pText.setColor(isTodayCol ? accent : pri);
            pText.setTextSize(dowSize);
            cv.drawText(DOW_SHORT[c], cx, headerH * 0.44f, pText);

            pText.setTypeface(Typeface.DEFAULT);
            pText.setColor(isTodayCol ? accent : sec);
            pText.setTextSize(dateSize);
            cv.drawText(dateStr, cx, headerH * 0.86f, pText);
        }

        cv.drawLine(0, headerH, w, headerH, pLine);

        pText.setTextSize(Math.max(7f, 8.5f * pxPerDp));
        pText.setColor(sec);
        pText.setTypeface(Typeface.DEFAULT);
        Paint.FontMetrics nFm = pText.getFontMetrics();
        float nBaselineOff = -(nFm.ascent + nFm.descent) / 2f;
        for (int n = 1; n <= maxNode; n++) {
            float yTop = headerH + (n - 1) * rowH;
            float yMid = yTop + rowH / 2f + nBaselineOff;
            cv.drawText(String.valueOf(n), railW / 2f, yMid, pText);
            if (n > 1) cv.drawLine(railW, yTop, w, yTop, pLine);
        }

        if (weekCourses.isEmpty()) {
            pText.setTextSize(Math.max(10f, 12f * pxPerDp));
            pText.setColor(sec);
            cv.drawText("第" + showWeek + "周暂无课程", railW + (w - railW) / 2f, h / 2f, pText);
            return bmp;
        }

        float pad = Math.max(1.2f, 1.6f * pxPerDp);
        float radius = Math.max(3f, 5.5f * pxPerDp);
        float barW = Math.max(2f, 2.4f * pxPerDp);
        Paint pCourse = new Paint(Paint.ANTI_ALIAS_FLAG);
        pCourse.setTextAlign(Paint.Align.LEFT);

        float titleSize = Math.max(8f, (cols == 7 ? 8.2f : 9.6f) * pxPerDp);
        float roomSize = Math.max(7f, (cols == 7 ? 7.2f : 8.2f) * pxPerDp);

        for (Course c : weekCourses) {
            if (c.dayOfWeek < 1 || c.dayOfWeek > cols) continue;
            int s = Math.max(1, Math.min(maxNode, c.startNode));
            int e = Math.max(s, Math.min(maxNode, c.endNode));

            float x0 = railW + (c.dayOfWeek - 1) * colW + pad;
            float x1 = railW + c.dayOfWeek * colW - pad;
            float y0 = headerH + (s - 1) * rowH + pad;
            float y1 = headerH + e * rowH - pad;
            if (x1 - x0 < 4f || y1 - y0 < 4f) continue;
            RectF rect = new RectF(x0, y0, x1, y1);

            pFill.setColor(Theme.blockBg(c.name, dark));
            cv.drawRoundRect(rect, radius, radius, pFill);

            pFill.setColor(Theme.accentColor(c.name));
            cv.drawRoundRect(new RectF(x0, y0 + 1.8f * pxPerDp, x0 + barW, y1 - 1.8f * pxPerDp),
                    1.2f * pxPerDp, 1.2f * pxPerDp, pFill);

            int fg = Theme.blockText(c.name, dark);
            float tx = x0 + barW + 2.4f * pxPerDp;
            float maxTextW = (x1 - tx) - 2.0f * pxPerDp;
            if (maxTextW < 8f) continue;

            pCourse.setColor(fg);
            pCourse.setTypeface(Typeface.DEFAULT_BOLD);
            pCourse.setTextSize(titleSize);
            Paint.FontMetrics tFm = pCourse.getFontMetrics();
            float lineH = (tFm.descent - tFm.ascent) * 1.06f;

            boolean drawRoom = !c.room.isEmpty() && (y1 - y0) >= (titleSize + roomSize + 6f * pxPerDp);
            float roomReserve = drawRoom ? (roomSize * 1.25f + 2.5f * pxPerDp) : 2f * pxPerDp;
            float availH = (y1 - y0) - 2.5f * pxPerDp - roomReserve;
            int maxLines = Math.max(1, (int) Math.floor(availH / lineH));

            List<String> wrapped = wrapText(c.name, pCourse, maxTextW);
            float curY = y0 + 2.5f * pxPerDp - tFm.ascent;
            int drawLines = Math.min(wrapped.size(), maxLines);
            for (int i = 0; i < drawLines; i++) {
                String line = wrapped.get(i);
                if (i == maxLines - 1 && wrapped.size() > maxLines && line.length() > 1) {
                    line = line.substring(0, line.length() - 1) + "…";
                }
                cv.drawText(line, tx, curY, pCourse);
                curY += lineH;
            }

            if (drawRoom) {
                pCourse.setTypeface(Typeface.DEFAULT);
                pCourse.setTextSize(roomSize);
                pCourse.setAlpha(215);
                Paint.FontMetrics rFm = pCourse.getFontMetrics();
                List<String> rl = wrapText("@" + c.room, pCourse, maxTextW);
                if (!rl.isEmpty()) {
                    String rTxt = rl.get(0);
                    if (rl.size() > 1 && rTxt.length() > 1) rTxt = rTxt.substring(0, rTxt.length() - 1) + "…";
                    cv.drawText(rTxt, tx, y1 - 2.2f * pxPerDp - rFm.descent, pCourse);
                }
                pCourse.setAlpha(255);
            }
        }

        return bmp;
    }

    private static List<String> wrapText(String text, Paint p, float maxW) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty() || maxW <= 4f) return out;
        int start = 0;
        int guard = 0;
        while (start < text.length() && guard++ < 64) {
            int count = p.breakText(text, start, text.length(), true, maxW, null);
            if (count <= 0) count = 1;
            out.add(text.substring(start, start + count));
            start += count;
        }
        return out;
    }
}
