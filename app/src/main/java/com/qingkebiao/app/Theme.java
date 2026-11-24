package com.qingkebiao.app;

import android.content.Context;
import android.graphics.Color;

/**
 * 小部件与 App 视觉主题、课程配色表。
 * 同一门课在日程小部件、周课表小部件、App 课表页里永远保持同一种柔和配色。
 */
public final class Theme {

    public static final int MODE_LIGHT = 0;       // 浅色半透明卡片
    public static final int MODE_DARK = 1;        // 深色半透明卡片
    public static final int MODE_TRANSPARENT = 2; // 全透明无底框

    /** 12 组柔和现代配色：[竖条/主色, 浅色卡片底, 浅色文字, 深色卡片底, 深色文字] */
    private static final int[][] PALETTE = {
            {0xFF3B82F6, 0xFFE8F1FE, 0xFF1D4ED8, 0xCC1E3A5F, 0xFFBFDBFE}, // 钴蓝
            {0xFF10B981, 0xFFE6F7F0, 0xFF047857, 0xCC144336, 0xFFA7F3D0}, // 翠绿
            {0xFFF59E0B, 0xFFFEF3DC, 0xFFB45309, 0xCC4A3410, 0xFFFDE68A}, // 琥珀
            {0xFF8B5CF6, 0xFFF0EBFE, 0xFF6D28D9, 0xCC35225C, 0xFFDDD6FE}, // 紫罗兰
            {0xFFEC4899, 0xFFFCE8F3, 0xFFBE185D, 0xCC501B36, 0xFFFBCFE8}, // 玫红
            {0xFF06B6D4, 0xFFE4F7FA, 0xFF0E7490, 0xCC133E4A, 0xFFA5F3FC}, // 青蓝
            {0xFFEF4444, 0xFFFEEAEA, 0xFFB91C1C, 0xCC4F1B1B, 0xFFFECACA}, // 珊瑚红
            {0xFF6366F1, 0xFFECEEFE, 0xFF4338CA, 0xCC24275E, 0xFFC7D2FE}, // 靛蓝
            {0xFF14B8A6, 0xFFE4F8F5, 0xFF0F766E, 0xCC13413C, 0xFF99F6E4}, // 松石
            {0xFFF97316, 0xFFFFEFE3, 0xFFC2410C, 0xCC4F2811, 0xFFFED7AA}, // 暖橙
            {0xFF84CC16, 0xFFF0F9E2, 0xFF4D7C0F, 0xCC2B4011, 0xFFD9F99D}, // 青柠
            {0xFFD946EF, 0xFFFAEAFD, 0xFFA21CAF, 0xCC461A4E, 0xFFF5D0FE}  // 兰紫
    };

    public static int paletteIndex(String courseName) {
        if (courseName == null || courseName.isEmpty()) return 0;
        int h = 0;
        for (int i = 0; i < courseName.length(); i++) {
            h = h * 31 + courseName.charAt(i);
        }
        return Math.abs(h) % PALETTE.length;
    }

    public static int accentColor(String courseName) {
        return PALETTE[paletteIndex(courseName)][0];
    }

    public static int blockBg(String courseName, boolean dark) {
        int[] p = PALETTE[paletteIndex(courseName)];
        return dark ? p[3] : p[1];
    }

    public static int blockText(String courseName, boolean dark) {
        int[] p = PALETTE[paletteIndex(courseName)];
        return dark ? p[4] : p[2];
    }

    // ---------- 小部件外观配置 ----------

    public static int widgetMode(Context c) {
        return Prefs.sp(c).getInt("w_mode", MODE_LIGHT);
    }

    public static void setWidgetMode(Context c, int mode) {
        Prefs.sp(c).edit().putInt("w_mode", mode).apply();
    }

    /** 背景不透明度 0..100，默认 86（通透且清晰） */
    public static int widgetOpacity(Context c) {
        return Prefs.sp(c).getInt("w_opacity", 86);
    }

    public static void setWidgetOpacity(Context c, int pct) {
        int v = Math.max(0, Math.min(100, pct));
        Prefs.sp(c).edit().putInt("w_opacity", v).apply();
    }

    public static boolean showWeekend(Context c) {
        return Prefs.sp(c).getBoolean("w_weekend", false);
    }

    public static void setShowWeekend(Context c, boolean show) {
        Prefs.sp(c).edit().putBoolean("w_weekend", show).apply();
    }

    public static boolean showExamInWidget(Context c) {
        return Prefs.sp(c).getBoolean("w_exam", true);
    }

    public static void setShowExamInWidget(Context c, boolean show) {
        Prefs.sp(c).edit().putBoolean("w_exam", show).apply();
    }

    /** 周课表小部件当前翻页偏移（0=本周，+1=下周，-1=上周） */
    public static int weekWidgetOffset(Context c) {
        return Prefs.sp(c).getInt("w_week_off", 0);
    }

    public static void setWeekWidgetOffset(Context c, int off) {
        int v = Math.max(-10, Math.min(10, off));
        Prefs.sp(c).edit().putInt("w_week_off", v).apply();
    }

    public static boolean isDark(int mode) {
        return mode == MODE_DARK || mode == MODE_TRANSPARENT;
    }

    public static int cardBgColor(int mode, int opacityPct) {
        if (mode == MODE_TRANSPARENT) return Color.TRANSPARENT;
        int alpha = (int) Math.round(opacityPct * 2.55);
        alpha = Math.max(0, Math.min(255, alpha));
        if (mode == MODE_DARK) {
            return Color.argb(alpha, 24, 27, 34);
        }
        return Color.argb(alpha, 255, 255, 255);
    }

    public static int primaryText(int mode) {
        return isDark(mode) ? 0xFFF3F4F6 : 0xFF18181B;
    }

    public static int secondaryText(int mode) {
        return isDark(mode) ? 0xFFB4BDC9 : 0xFF71717A;
    }

    public static int dividerColor(int mode) {
        return isDark(mode) ? 0x26FFFFFF : 0x18000000;
    }

    public static int accentBlue(int mode) {
        return isDark(mode) ? 0xFF60A5FA : 0xFF2563EB;
    }

    public static int examTextColor(int mode) {
        return isDark(mode) ? 0xFFFCA5A5 : 0xFFDC2626;
    }

    private Theme() {
    }
}
