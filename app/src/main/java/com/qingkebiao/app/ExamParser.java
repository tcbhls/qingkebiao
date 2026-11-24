package com.qingkebiao.app;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 多高校考试安排解析（兼容正方、强智、金智 EMAP/EasyUI、树维、URP 与通用表格） */
public class ExamParser {

    public static List<Exam> parse(String html) {
        List<Exam> out = new ArrayList<>();
        if (html == null || html.isEmpty()) return out;

        for (String t : Html.tables(html)) {
            if (!(t.contains("考试") || t.contains("考场") || t.contains("座位"))) continue;
            String[][] g = Html.grid(t);
            if (g.length < 2) continue;

            int headerRow = -1;
            Map<String, Integer> idx = new HashMap<>();
            for (int r = 0; r < Math.min(g.length, 4); r++) {
                Map<String, Integer> m = new HashMap<>();
                for (int c = 0; c < g[r].length; c++) {
                    String h = Html.text(g[r][c]).replaceAll("\\s+", "");
                    if (!h.isEmpty()) m.put(h, c);
                }
                boolean hasTime = find(m, "考试时间", "考试日期", "时间", "日期") >= 0;
                boolean hasName = find(m, "课程名称", "考试名称", "课程", "科目") >= 0;
                if (hasTime && hasName) {
                    headerRow = r;
                    idx = m;
                    break;
                }
            }
            if (headerRow < 0) continue;

            int cDate = find(idx, "考试日期", "日期");
            int cTime = find(idx, "考试时间", "时间");
            int cName = find(idx, "课程名称", "考试名称", "课程", "科目");
            int cRoom = find(idx, "考试地点", "考场", "教室", "地点");
            int cSeat = find(idx, "座位号", "座位");

            for (int r = headerRow + 1; r < g.length; r++) {
                String date = cDate >= 0 && cDate < g[r].length ? Html.text(g[r][cDate]) : "";
                String time = cTime >= 0 && cTime < g[r].length ? Html.text(g[r][cTime]) : "";
                String combinedTime = (cDate >= 0 && cDate != cTime && !date.isEmpty())
                        ? (date + " " + time).trim() : time.trim();
                String name = cName >= 0 && cName < g[r].length ? Html.text(g[r][cName]) : "";
                if (name.isEmpty() && combinedTime.isEmpty()) continue;
                if (name.contains("课程名称") || name.contains("考试名称")) continue;

                Exam e = new Exam();
                e.name = name.trim();
                e.timeRaw = combinedTime;
                e.room = cRoom >= 0 && cRoom < g[r].length ? Html.text(g[r][cRoom]).trim() : "";
                e.seat = cSeat >= 0 && cSeat < g[r].length ? Html.text(g[r][cSeat]).trim() : "";
                e.startMillis = parseTime(combinedTime);
                out.add(e);
            }
            if (!out.isEmpty()) break;
        }
        return out;
    }

    /**
     * 通用考试 JSON 解析：兼容新正方 items、EasyUI rows、金智 EMAP 嵌套 datas.<any>.rows。
     */
    public static List<Exam> parseDataListJson(String body) {
        List<Exam> out = new ArrayList<>();
        if (body == null) return out;
        String b = body.trim();
        if (b.startsWith("\uFEFF")) b = b.substring(1);
        if (!b.startsWith("{") && !b.startsWith("[")) return out;
        try {
            List<Object> rows = KbParser.extractRowArray(Json.parse(b));
            if (rows == null) return out;

            for (Object ro : rows) {
                Map<String, Object> o = Json.asMap(ro);
                if (o == null) continue;
                String name = Json.str(o, "kcmc", "kskcmc", "kcm", "ksmc", "kcmcstr",
                        "courseName", "kcmc_show");
                if (name == null) continue;

                Exam e = new Exam();
                e.name = name.trim();
                if (e.name.contains("<")) e.name = Html.text(e.name);

                String room = Json.str(o, "kscd", "kscdmc", "cdmc", "jasmc", "jxcdmc",
                        "ksdd", "kcdd", "room", "classroomName");
                if (room != null) e.room = Html.text(room).trim();

                String seat = Json.str(o, "zwh", "kczwh", "zw", "seat", "seatNo");
                if (seat != null) e.seat = Html.text(seat).trim();

                String rq = Json.str(o, "ksrq", "ksrqstr", "rq", "date", "examDate");
                String sj = Json.str(o, "kssj", "kssjstr", "sj", "time", "examTime", "kssjms");
                if (rq != null && sj != null && sj.contains(rq)) {
                    e.timeRaw = sj.trim();
                } else {
                    e.timeRaw = ((rq == null ? "" : rq) + (sj == null ? "" : " " + sj)).trim();
                }
                e.startMillis = parseTime(e.timeRaw);
                // 避免把普通课表 JSON 误认成考试：必须带有日期时间戳或明确考场/座位字段
                if (e.timeRaw.isEmpty() && e.room.isEmpty()) continue;
                if (e.startMillis <= 0 && e.seat.isEmpty() && e.timeRaw.isEmpty()) continue;
                out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** 通用多高校考试提取入口 */
    public static List<Exam> parseUniversal(List<String> jsonBlobs, List<String> htmlBlobs) {
        List<Exam> best = new ArrayList<>();
        if (jsonBlobs != null) {
            for (String j : jsonBlobs) {
                if (j == null || j.trim().isEmpty()) continue;
                List<Exam> r = parseDataListJson(j);
                if (r.size() > best.size()) best = r;
            }
        }
        if (htmlBlobs != null) {
            for (String h : htmlBlobs) {
                if (h == null || h.trim().isEmpty()) continue;
                List<Exam> r = parse(h);
                if (r.size() > best.size()) best = r;
            }
        }
        return best;
    }

    private static int find(Map<String, Integer> map, String... keys) {
        for (String k : keys) {
            if (map.containsKey(k)) return map.get(k);
        }
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            for (String k : keys) {
                if (e.getKey().contains(k)) return e.getValue();
            }
        }
        return -1;
    }

    /**
     * 解析各种日期时间：
     * "2026-12-28 14:30-16:30" / "2026/12/28 09:00" / "2026年12月28日 09:00" / "12月28日 14:30"
     */
    public static long parseTime(String raw) {
        if (raw == null || raw.isEmpty()) return 0L;
        String s = raw.replace('－', '-').replace('／', '/').replace('/', '-')
                .replace('.', '-').replace('年', '-').replace('月', '-').replace("日", " ");
        Matcher m = Pattern.compile("(\\d{4})\\s*-\\s*(\\d{1,2})\\s*-\\s*(\\d{1,2})").matcher(s);
        int y, mo, d;
        if (m.find()) {
            y = Integer.parseInt(m.group(1));
            mo = Integer.parseInt(m.group(2));
            d = Integer.parseInt(m.group(3));
        } else {
            Matcher m2 = Pattern.compile("(\\d{1,2})\\s*-\\s*(\\d{1,2})").matcher(s);
            if (!m2.find()) return 0L;
            y = Calendar.getInstance().get(Calendar.YEAR);
            mo = Integer.parseInt(m2.group(1));
            d = Integer.parseInt(m2.group(2));
        }
        if (mo < 1 || mo > 12 || d < 1 || d > 31) return 0L;
        Matcher tm = Pattern.compile("(\\d{1,2})\\s*[:：]\\s*(\\d{2})").matcher(s);
        int hh = 8, mm = 0;
        if (tm.find()) {
            hh = Integer.parseInt(tm.group(1));
            mm = Integer.parseInt(tm.group(2));
        }
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, mo - 1, d, hh, mm, 0);
        return c.getTimeInMillis();
    }
}
