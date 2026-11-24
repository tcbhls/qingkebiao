package com.qingkebiao.app;

import android.content.Context;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多高校教务档案与作息预设。
 * 默认使用「内置浏览器一键导入」（适配绝大多数高校教务系统），
 * 同时提供「通用金智 CAS + EasyUI 自动后台刷新」供同类架构高校按需配置使用。
 */
public final class SchoolProfile {

    public static final String MODE_WEB_IMPORT = "web_import";
    public static final String MODE_CAS_AUTO = "cas_auto";

    public static final class Preset {
        public final String id;
        public final String name;
        public final String hintUrl;
        public final String mode;
        public final String schedulePreset;

        public Preset(String id, String name, String hintUrl, String mode, String schedulePreset) {
            this.id = id;
            this.name = name;
            this.hintUrl = hintUrl;
            this.mode = mode;
            this.schedulePreset = schedulePreset;
        }
    }

    public static final Preset[] PRESETS = {
            new Preset("zf_new", "新正方教务 jwglxt（内置浏览器导入）",
                    "https://jwglxt.example.edu.cn", MODE_WEB_IMPORT, "std0800"),
            new Preset("qz", "强智教务 jsxsd（内置浏览器导入）",
                    "https://jw.example.edu.cn/jsxsd", MODE_WEB_IMPORT, "std0800"),
            new Preset("emap", "金智 / EMAP 教务（内置浏览器导入）",
                    "https://ehall.example.edu.cn", MODE_WEB_IMPORT, "std0830"),
            new Preset("eams_urp", "树维 EAMS / URP 教务（内置浏览器导入）",
                    "https://eams.example.edu.cn", MODE_WEB_IMPORT, "std0800"),
            new Preset("custom", "其他任意高校（通用浏览器导入）",
                    "https://jw.example.edu.cn", MODE_WEB_IMPORT, "std0800"),
            new Preset("cas_easyui", "金智 CAS + EasyUI（自定义地址自动刷新）",
                    "https://jw.example.edu.cn", MODE_CAS_AUTO, "std14_0830")
    };

    public static final String[] SCHEDULE_NAMES = {
            "通用 12 节（08:00 起）",
            "通用 12 节（08:30 起）",
            "长时段 14 节（08:30 起）"
    };

    public static final String[] SCHEDULE_IDS = {"std0800", "std0830", "std14_0830"};

    public static Map<Integer, String> presetTimes(String id) {
        Map<Integer, String> m = new LinkedHashMap<>();
        String[] arr;
        if ("std0830".equals(id)) {
            arr = new String[]{"08:30", "09:20", "10:20", "11:10", "14:00", "14:50",
                    "15:50", "16:40", "19:00", "19:50", "20:40", "21:30"};
        } else if ("std14_0830".equals(id)) {
            arr = new String[]{"08:30", "09:20", "10:25", "11:15", "13:45", "14:40", "15:30",
                    "16:30", "17:20", "19:00", "19:50", "20:45", "21:35", "22:25"};
        } else {
            arr = new String[]{"08:00", "08:55", "10:00", "10:55", "14:00", "14:55",
                    "16:00", "16:55", "19:00", "19:55", "20:50", "21:45"};
        }
        for (int i = 0; i < arr.length; i++) m.put(i + 1, arr[i]);
        return m;
    }

    public static String mode(Context c) {
        return Prefs.sp(c).getString("school_mode", MODE_WEB_IMPORT);
    }

    public static void setMode(Context c, String mode) {
        Prefs.sp(c).edit().putString("school_mode", mode).apply();
    }

    public static String schoolName(Context c) {
        return Prefs.sp(c).getString("school_name", "我的大学");
    }

    public static void setSchoolName(Context c, String name) {
        Prefs.sp(c).edit().putString("school_name", name).apply();
    }

    public static String customUrl(Context c) {
        String v = Prefs.sp(c).getString("school_url", "");
        if (v == null) return "";
        v = v.trim();
        if (v.equals("http://") || v.equals("https://") || v.equals("https:///")) return "";
        return v;
    }

    public static void setCustomUrl(Context c, String url) {
        Prefs.sp(c).edit().putString("school_url", url == null ? "" : url.trim()).apply();
    }

    /** 可选：自定义统一认证登录页地址（留空则自动探测教务网址的 302 跳转地址） */
    public static String casUrl(Context c) {
        String v = Prefs.sp(c).getString("cas_url", "");
        return v == null ? "" : v.trim();
    }

    public static void setCasUrl(Context c, String url) {
        Prefs.sp(c).edit().putString("cas_url", url == null ? "" : url.trim()).apply();
    }

    public static boolean isCasAuto(Context c) {
        return MODE_CAS_AUTO.equals(mode(c));
    }

    private SchoolProfile() {
    }
}
