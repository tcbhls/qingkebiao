package com.qingkebiao.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 本地存储：配置、课表、考试、周次与小部件尺寸 */
public class Prefs {

    private static final String SP = "qingkebiao_prefs";

    public static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    public static String user(Context c) {
        return sp(c).getString("user", "");
    }

    public static String pass(Context c) {
        return sp(c).getString("pass", "");
    }

    public static void saveAccount(Context c, String u, String p) {
        sp(c).edit().putString("user", u).putString("pass", p).apply();
    }

    public static String term(Context c) {
        return sp(c).getString("term", "");
    }

    public static void saveTerm(Context c, String t) {
        sp(c).edit().putString("term", t).apply();
    }

    public static String kbUrl(Context c) {
        return sp(c).getString("kbUrl", "");
    }

    public static void saveKbUrl(Context c, String u) {
        sp(c).edit().putString("kbUrl", u).apply();
    }

    public static String examUrl(Context c) {
        return sp(c).getString("examUrl", "");
    }

    public static void saveExamUrl(Context c, String u) {
        sp(c).edit().putString("examUrl", u).apply();
    }

    public static String manualKb(Context c) {
        return sp(c).getString("manualKb", "");
    }

    public static String manualExam(Context c) {
        return sp(c).getString("manualExam", "");
    }

    public static void saveManualUrls(Context c, String kb, String exam) {
        sp(c).edit().putString("manualKb", kb).putString("manualExam", exam).apply();
    }

    public static long lastRefresh(Context c) {
        return sp(c).getLong("lastRefresh", 0L);
    }

    public static String lastMessage(Context c) {
        return sp(c).getString("lastMessage", "");
    }

    public static void saveStatus(Context c, long ts, String msg) {
        sp(c).edit().putLong("lastRefresh", ts).putString("lastMessage", msg).apply();
    }

    public static String termStart(Context c) {
        return sp(c).getString("termStart", "");
    }

    public static void saveTermStart(Context c, String d) {
        sp(c).edit().putString("termStart", d).apply();
    }

    public static void saveCourses(Context c, List<Course> list, Map<Integer, String> nodeTime) {
        JSONArray arr = new JSONArray();
        try {
            for (Course k : list) {
                JSONObject o = new JSONObject();
                o.put("n", k.name);
                o.put("t", k.teacher);
                o.put("r", k.room);
                o.put("d", k.dayOfWeek);
                o.put("s", k.startNode);
                o.put("e", k.endNode);
                o.put("w", k.weeksRaw);
                JSONArray ws = new JSONArray();
                for (int w : k.weeks) ws.put(w);
                o.put("wl", ws);
                arr.put(o);
            }
            SharedPreferences.Editor ed = sp(c).edit().putString("courses", arr.toString());
            if (nodeTime != null && !nodeTime.isEmpty() && !sp(c).getBoolean("customNodeTime", false)) {
                JSONObject nt = new JSONObject();
                for (Map.Entry<Integer, String> e : nodeTime.entrySet()) {
                    nt.put(String.valueOf(e.getKey()), e.getValue());
                }
                ed.putString("nodeTime", nt.toString());
            }
            ed.apply();
        } catch (Exception ignored) {
        }
    }

    public static void addCourse(Context c, Course course) {
        List<Course> list = courses(c);
        list.add(course);
        saveCourses(c, list, null);
    }

    public static void removeCourse(Context c, Course target) {
        List<Course> list = courses(c);
        for (int i = 0; i < list.size(); i++) {
            Course k = list.get(i);
            if (k.dayOfWeek == target.dayOfWeek
                    && k.startNode == target.startNode
                    && k.endNode == target.endNode
                    && k.name.equals(target.name)
                    && k.room.equals(target.room)) {
                list.remove(i);
                break;
            }
        }
        saveCourses(c, list, null);
    }

    public static void saveNodeTime(Context c, Map<Integer, String> nodeTime, boolean markCustom) {
        try {
            JSONObject nt = new JSONObject();
            for (Map.Entry<Integer, String> e : nodeTime.entrySet()) {
                nt.put(String.valueOf(e.getKey()), e.getValue());
            }
            sp(c).edit()
                    .putString("nodeTime", nt.toString())
                    .putBoolean("customNodeTime", markCustom)
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public static List<Course> courses(Context c) {
        List<Course> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("courses", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Course k = new Course();
                k.name = o.optString("n");
                k.teacher = o.optString("t");
                k.room = o.optString("r");
                k.dayOfWeek = o.optInt("d", 1);
                k.startNode = o.optInt("s", 1);
                k.endNode = o.optInt("e", 1);
                k.weeksRaw = o.optString("w");
                JSONArray ws = o.optJSONArray("wl");
                if (ws != null) {
                    k.weeks = new int[ws.length()];
                    for (int j = 0; j < ws.length(); j++) k.weeks[j] = ws.optInt(j);
                }
                out.add(k);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static Map<Integer, String> nodeTime(Context c) {
        Map<Integer, String> m = new LinkedHashMap<>();
        try {
            JSONObject o = new JSONObject(sp(c).getString("nodeTime", "{}"));
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                m.put(Integer.parseInt(k), o.optString(k));
            }
        } catch (Exception ignored) {
        }
        if (m.isEmpty()) {
            return SchoolProfile.presetTimes("std0800");
        }
        return m;
    }

    /** 单节课默认按 45 分钟推算下课时间，用于显示「08:00-08:45」 */
    public static String nodeEnd(String start) {
        if (start == null || start.length() != 5) return "";
        try {
            int h = Integer.parseInt(start.substring(0, 2));
            int mm = Integer.parseInt(start.substring(3, 5)) + 45;
            h += mm / 60;
            mm %= 60;
            return String.format("%02d:%02d", h % 24, mm);
        } catch (Exception e) {
            return "";
        }
    }

    public static void saveExams(Context c, List<Exam> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Exam e : list) {
                JSONObject o = new JSONObject();
                o.put("n", e.name);
                o.put("r", e.room);
                o.put("s", e.seat);
                o.put("tr", e.timeRaw);
                o.put("ts", e.startMillis);
                arr.put(o);
            }
            sp(c).edit().putString("exams", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static List<Exam> exams(Context c) {
        List<Exam> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("exams", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Exam e = new Exam();
                e.name = o.optString("n");
                e.room = o.optString("r");
                e.seat = o.optString("s");
                e.timeRaw = o.optString("tr");
                e.startMillis = o.optLong("ts", 0L);
                out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static Exam nextExam(Context c) {
        long now = System.currentTimeMillis();
        Exam best = null;
        for (Exam e : exams(c)) {
            if (e.startMillis <= 0) continue;
            if (e.startMillis + 3L * 3600 * 1000 < now) continue;
            if (best == null || e.startMillis < best.startMillis) best = e;
        }
        if (best != null) return best;
        for (Exam e : exams(c)) {
            if (e.startMillis <= 0 && !e.name.isEmpty()) return e;
        }
        return null;
    }

    public static Calendar firstMonday(Context c) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        String manual = termStart(c);
        if (manual != null && manual.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
            String[] p = manual.split("-");
            cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            return cal;
        }
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        if (month >= 8) cal.set(year, Calendar.SEPTEMBER, 1);
        else if (month <= 1) cal.set(year - 1, Calendar.SEPTEMBER, 1);
        else cal.set(year, Calendar.FEBRUARY, 23);
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }
        return cal;
    }

    /** 按本地自然日计算 targetDay 属于第几周（1..30），规避 UTC+8 跨日提前跳周 */
    public static int weekBetween(Calendar firstMonday, Calendar targetDay) {
        Calendar a = (Calendar) firstMonday.clone();
        a.set(Calendar.HOUR_OF_DAY, 12);
        a.set(Calendar.MINUTE, 0);
        a.set(Calendar.SECOND, 0);
        a.set(Calendar.MILLISECOND, 0);

        Calendar b = (Calendar) targetDay.clone();
        b.set(Calendar.HOUR_OF_DAY, 12);
        b.set(Calendar.MINUTE, 0);
        b.set(Calendar.SECOND, 0);
        b.set(Calendar.MILLISECOND, 0);

        long diffDays = Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86400000.0);
        int w = (int) Math.floor(diffDays / 7.0) + 1;
        return Math.max(1, Math.min(30, w));
    }

    /** 按本地自然日计算距离目标时间戳还有几天 */
    public static long daysUntil(long targetMillis, long nowMillis) {
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(nowMillis);
        a.set(Calendar.HOUR_OF_DAY, 12);
        a.set(Calendar.MINUTE, 0);
        a.set(Calendar.SECOND, 0);
        a.set(Calendar.MILLISECOND, 0);

        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(targetMillis);
        b.set(Calendar.HOUR_OF_DAY, 12);
        b.set(Calendar.MINUTE, 0);
        b.set(Calendar.SECOND, 0);
        b.set(Calendar.MILLISECOND, 0);

        return Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86400000.0);
    }

    public static int currentWeek(Context c) {
        return weekBetween(firstMonday(c), Calendar.getInstance());
    }

    public static String todayLabel() {
        Calendar c = Calendar.getInstance();
        String[] dow = {"日", "一", "二", "三", "四", "五", "六"};
        return new SimpleDateFormat("M月d日", Locale.CHINA).format(new Date())
                + " 周" + dow[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    public static int todayDow() {
        int d = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        return d == Calendar.SUNDAY ? 7 : d - 1;
    }

    public static String fmtTime(long ms) {
        return new SimpleDateFormat("M-d HH:mm", Locale.CHINA).format(new Date(ms));
    }

    private static final String[] WEEK_CN = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    /** 计算距离今天 offset 天的日期短标签 */
    public static String dayLabel(int todayDow, int offset) {
        if (offset == 0) return "今天";
        if (offset == 1) return "明天";
        if (offset == 2) return "后天";
        int idx = ((todayDow - 1) + offset) % 7;
        if (idx < 0) idx += 7;
        return WEEK_CN[idx];
    }

    // ---------- 小部件实测尺寸 ----------

    public static void saveWidgetSize(Context c, int widgetId, int wDp, int hDp) {
        sp(c).edit()
                .putString("wsize_" + widgetId, Math.max(1, wDp) + "," + Math.max(1, hDp))
                .apply();
    }

    public static int[] widgetSize(Context c, int widgetId) {
        String s = sp(c).getString("wsize_" + widgetId, "");
        if (s != null && s.contains(",")) {
            String[] p = s.split(",");
            try {
                int w = Integer.parseInt(p[0].trim());
                int h = Integer.parseInt(p[1].trim());
                if (w > 60 && h > 60) return new int[]{w, h};
            } catch (Exception ignored) {
            }
        }
        return new int[]{250, 250};
    }
}
