package com.qingkebiao.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多高校通用课表解析引擎：
 * 1) 新正方教务（jwglxt kbList JSON）
 * 2) 金智 EMAP（datas.*.rows + 01 位图周次）与 EasyUI datagrid JSON（rows + jcdm/jxcdmc + 周次合并）
 * 3) 新 URP 综合教务（dateList -> selectCourseList -> timeAndPlaceList）
 * 4) 树维 EAMS（内联 JS TaskActivity + index = day*unitCount+unit）
 * 5) 强智 jsxsd / 青果 / 老正方 HTML 网格表（支持大节行头 1-2节、多课 ----- 与 <br><br> 分隔）
 * 6) 通用列表式 HTML 表格与 FullCalendar JSON 事件
 */
public class KbParser {

    private static final String[] CN_NUM = {
            "一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二", "十三", "十四"
    };

    /** 解析 HTML 页面（依次尝试树维 JS、列表式表格、网格式表格） */
    public static KbResult parse(String html) {
        KbResult res = new KbResult();
        if (html == null || html.isEmpty()) return res;

        // 0) 树维 EAMS 内联 JS（TaskActivity）
        if (html.contains("TaskActivity")) {
            KbResult td = parseTreeDimScript(html);
            if (!td.courses.isEmpty()) {
                fillWeekHint(html, td);
                return td;
            }
        }

        // 1) 列表式（表头含「课程名称 + 上课时间」）最明确，先试它
        parseList(html, res);
        if (!res.courses.isEmpty()) {
            fillWeekHint(html, res);
            return res;
        }

        fillWeekHint(html, res);

        String best = null;
        int bestScore = -1;
        for (String t : Html.tables(html)) {
            int score = 0;
            String low = t.toLowerCase();
            if (low.contains("id=\"kbtable\"") || low.contains("id='kbtable'")
                    || low.contains("id=\"table1\"") || low.contains("id='table1'")) score += 100;
            score += 10 * count(t, "星期");
            score += 8 * count(t, "周一") + 8 * count(t, "周二") + 8 * count(t, "周三");
            // 兼容日历表头无「周/星期」前缀（如 "一 11/23", "二 11/24", "第01节"）
            if (t.contains("第01节") || t.contains("第1节")) score += 12;
            score += 5 * count(t, "kbcontent");
            if (t.contains("节") || t.contains("课程")) score += 3;
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        if (best == null || bestScore < 10) return res;

        String[][] g = Html.grid(best);
        if (g.length < 2) return res;

        // 1) 表头行 + 列 -> 星期
        int headerRow = -1;
        Map<Integer, Integer> colToDow = new HashMap<>();
        for (int r = 0; r < Math.min(g.length, 5); r++) {
            Map<Integer, Integer> m = new HashMap<>();
            for (int c = 0; c < g[r].length; c++) {
                int dow = dowOf(Html.text(g[r][c]));
                if (dow > 0) m.put(c, dow);
            }
            if (m.size() >= 3) {
                headerRow = r;
                colToDow = m;
                break;
            }
        }
        if (headerRow < 0) {
            boolean firstColIsNode = false;
            if (g.length > 1) {
                int ok = 0;
                for (int r = 1; r < g.length && r < 12; r++) {
                    if (nodeRangeOf(Html.text(g[r][0]))[0] > 0) ok++;
                }
                firstColIsNode = ok >= 2;
            }
            if (!firstColIsNode) {
                parseList(html, res);
                return res;
            }
            headerRow = 0;
            for (int c = 1; c <= 7; c++) colToDow.put(c, c);
        }

        // 2) 每行的起始/结束节次（兼容单节「第一节」和大节「第一大节 01-02节」「1-2节」）
        Map<Integer, Integer> rowStart = new HashMap<>();
        Map<Integer, Integer> rowEnd = new HashMap<>();
        int seq = 0;
        for (int r = headerRow + 1; r < g.length; r++) {
            // 有些表首列是「上午/下午/晚上」，第 2 列才是节次号；合并前两列（不含星期列）检查
            StringBuilder headBuf = new StringBuilder();
            for (int c = 0; c < g[r].length && !colToDow.containsKey(c) && c < 3; c++) {
                String t = Html.text(g[r][c]);
                if (!t.isEmpty()) {
                    if (headBuf.length() > 0) headBuf.append(' ');
                    headBuf.append(t);
                }
            }
            String head = headBuf.toString();
            int[] nr = nodeRangeOf(head);
            int ns, ne;
            if (nr[0] > 0) {
                ns = nr[0];
                ne = nr[1];
                seq = ne;
            } else {
                seq++;
                ns = seq;
                ne = seq;
            }
            rowStart.put(r, ns);
            rowEnd.put(r, ne);

            Matcher tm = Pattern.compile("(\\d{1,2})[:：](\\d{2})").matcher(head);
            if (tm.find() && !res.nodeTime.containsKey(ns)) {
                res.nodeTime.put(ns, String.format("%02d:%s",
                        Integer.parseInt(tm.group(1)), tm.group(2)));
            }
        }

        // 3) 每列按连续相同内容聚合，并切分同格多门课
        for (Map.Entry<Integer, Integer> e : colToDow.entrySet()) {
            int col = e.getKey();
            int dow = e.getValue();
            int r = headerRow + 1;
            while (r < g.length) {
                String cell = col < g[r].length ? g[r][col] : "";
                String norm = Html.text(cell).replaceAll("\\s+", "");
                if (norm.isEmpty()) {
                    r++;
                    continue;
                }
                int rEnd = r;
                while (rEnd + 1 < g.length) {
                    String nx = col < g[rEnd + 1].length ? g[rEnd + 1][col] : "";
                    if (Html.text(nx).replaceAll("\\s+", "").equals(norm)) rEnd++;
                    else break;
                }
                int start = rowStart.containsKey(r) ? rowStart.get(r) : r;
                int end = rowEnd.containsKey(rEnd) ? rowEnd.get(rEnd) : start;
                for (String block : Html.blocks(cell)) {
                    Course c = parseBlock(block, dow, start, end);
                    if (c != null && !c.name.isEmpty()) res.courses.add(c);
                }
                r = rEnd + 1;
            }
        }

        if (!res.courses.isEmpty()) res.courses = mergeWeeks(res.courses);
        else parseList(html, res);
        return res;
    }

    private static void fillWeekHint(String html, KbResult res) {
        Matcher wk = Pattern.compile("第\\s*(\\d{1,2})\\s*周").matcher(html);
        if (wk.find()) res.weekHint = wk.group(1);
    }

    /**
     * 树维教务（EAMS）内联 JS 课表解析：
     *   activity = new TaskActivity(teacherId, teacherName, courseId, courseName, roomId, roomName, validWeeks, ...);
     *   index = 0 * unitCount + 1;
     *   table0.activities[index][...] = activity;
     */
    public static KbResult parseTreeDimScript(String html) {
        KbResult res = new KbResult();
        if (html == null || !html.contains("TaskActivity")) return res;

        // 匹配每个 TaskActivity 及其后到下一个 TaskActivity 之间的 index 赋值
        Pattern pAct = Pattern.compile(
                "new\\s+TaskActivity\\s*\\(([^;]{10,600})\\)", Pattern.DOTALL);
        Matcher mAct = pAct.matcher(html);
        List<int[]> spans = new ArrayList<>();
        List<String> argsList = new ArrayList<>();
        while (mAct.find()) {
            spans.add(new int[]{mAct.start(), mAct.end()});
            argsList.add(mAct.group(1));
        }
        if (spans.isEmpty()) return res;

        Pattern pIdx = Pattern.compile("index\\s*=\\s*(\\d+)\\s*\\*\\s*unitCount\\s*\\+\\s*(\\d+)");
        for (int i = 0; i < spans.size(); i++) {
            int segStart = spans.get(i)[1];
            int segEnd = (i + 1 < spans.size()) ? spans.get(i + 1)[0] : Math.min(html.length(), segStart + 1200);
            String seg = html.substring(segStart, segEnd);

            List<String> args = splitJsArgs(argsList.get(i));
            if (args.size() < 7) continue;

            String teacher = stripQuotes(args.get(1));
            String name = stripQuotes(args.get(3));
            // 树维课程名常带 "(课程代码)"，去掉尾部纯编号括号更清爽
            name = name.replaceAll("\\([A-Za-z0-9._-]+\\)$", "").trim();
            String room = stripQuotes(args.get(5));
            String validWeeks = stripQuotes(args.get(6));
            if (name.isEmpty()) continue;

            int[] weeks = parseWeekBitmap(validWeeks, true);

            // 同一 TaskActivity 可能绑定多个 (day, unit)，按天合并连续节次
            Map<Integer, TreeSet<Integer>> dayNodes = new LinkedHashMap<>();
            Matcher mIdx = pIdx.matcher(seg);
            while (mIdx.find()) {
                int day0 = toI(mIdx.group(1)); // 0=周一 .. 6=周日
                int unit0 = toI(mIdx.group(2)); // 0=第1节 ..
                int dow = day0 + 1;
                int node = unit0 + 1;
                if (dow >= 1 && dow <= 7 && node >= 1 && node <= 20) {
                    TreeSet<Integer> ns = dayNodes.get(dow);
                    if (ns == null) {
                        ns = new TreeSet<>();
                        dayNodes.put(dow, ns);
                    }
                    ns.add(node);
                }
            }
            for (Map.Entry<Integer, TreeSet<Integer>> e : dayNodes.entrySet()) {
                int dow = e.getKey();
                List<Integer> list = new ArrayList<>(e.getValue());
                int k = 0;
                while (k < list.size()) {
                    int s = list.get(k);
                    int en = s;
                    while (k + 1 < list.size() && list.get(k + 1) == en + 1) {
                        en = list.get(++k);
                    }
                    Course c = new Course();
                    c.name = name;
                    c.teacher = teacher;
                    c.room = room;
                    c.dayOfWeek = dow;
                    c.startNode = s;
                    c.endNode = en;
                    c.weeks = weeks.clone();
                    c.weeksRaw = weeks.length > 0 ? (weeks[0] + "-" + weeks[weeks.length - 1] + "周") : "";
                    res.courses.add(c);
                    k++;
                }
            }
        }
        if (!res.courses.isEmpty()) res.courses = mergeWeeks(res.courses);
        return res;
    }

    private static List<String> splitJsArgs(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (quote != 0) {
                if (ch == '\\' && i + 1 < s.length()) {
                    cur.append(ch).append(s.charAt(++i));
                } else {
                    if (ch == quote) quote = 0;
                    cur.append(ch);
                }
            } else {
                if (ch == '"' || ch == '\'') {
                    quote = ch;
                    cur.append(ch);
                } else if (ch == ',') {
                    out.add(cur.toString().trim());
                    cur.setLength(0);
                } else {
                    cur.append(ch);
                }
            }
        }
        if (cur.length() > 0) out.add(cur.toString().trim());
        return out;
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() >= 2 && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'")))) {
            t = t.substring(1, t.length() - 1);
        }
        return Html.decode(t).trim();
    }

    /**
     * 列表式课表：表头含「课程名称 / 上课时间 / 上课地点 / 教师 / 周次」等列，一行一门课。
     */
    public static void parseList(String html, KbResult res) {
        if (html == null) return;
        for (String t : Html.tables(html)) {
            String[][] g = Html.grid(t);
            if (g.length < 2) continue;

            int hr = -1;
            Map<String, Integer> idx = new HashMap<>();
            for (int r = 0; r < Math.min(g.length, 4); r++) {
                Map<String, Integer> m = new HashMap<>();
                for (int c = 0; c < g[r].length; c++) {
                    String h = Html.text(g[r][c]).replaceAll("\\s+", "");
                    if (!h.isEmpty() && h.length() <= 12) m.put(h, c);
                }
                if (findCol(m, "课程名称", "课程名", "课程") >= 0 && m.size() >= 3) {
                    hr = r;
                    idx = m;
                    break;
                }
            }
            if (hr < 0) continue;

            int cName = findCol(idx, "课程名称", "课程名", "课程");
            int cTime = findCol(idx, "上课时间", "时间", "节次");
            int cRoom = findCol(idx, "上课地点", "地点", "教室", "场地");
            int cTea = findCol(idx, "任课教师", "教师", "老师");
            int cWeek = findCol(idx, "周次", "起止周", "上课周");

            for (int r = hr + 1; r < g.length; r++) {
                String name = cName >= 0 && cName < g[r].length ? Html.text(g[r][cName]) : "";
                name = name.replaceAll("\\s+", " ").trim();
                if (name.isEmpty() || name.contains("课程名称")) continue;

                Course c = new Course();
                c.name = name;
                if (cRoom >= 0 && cRoom < g[r].length) c.room = Html.text(g[r][cRoom]).trim();
                if (cTea >= 0 && cTea < g[r].length) c.teacher = Html.text(g[r][cTea]).trim();
                if (cWeek >= 0 && cWeek < g[r].length) {
                    c.weeksRaw = Html.text(g[r][cWeek]).trim();
                    c.weeks = parseWeeks(c.weeksRaw);
                }
                String timeTxt = cTime >= 0 && cTime < g[r].length ? Html.text(g[r][cTime]) : "";
                int dow = dowOf(timeTxt);
                if (dow <= 0) dow = dowOf(c.weeksRaw);
                if (dow <= 0) dow = dowOf(name);
                if (dow <= 0) continue;

                int[] nr = extractNodeRangeFromText(timeTxt);
                int s = nr[0] > 0 ? nr[0] : 1;
                int e2 = nr[1] >= s ? nr[1] : s;
                c.dayOfWeek = dow;
                c.startNode = s;
                c.endNode = e2;

                if (c.weeks.length == 0) c.weeks = parseWeeksFromScheduleLine(timeTxt);
                res.courses.add(c);
            }
            if (!res.courses.isEmpty()) return;
        }
    }

    /**
     * 新 URP 综合教务 JSON 解析：
     * {"dateList":[{"selectCourseList":[{"courseName":"...","attendClassTeacher":"...",
     *   "timeAndPlaceList":[{"classDay":1,"classSessions":3,"continuingSession":2,
     *   "classWeek":"111111111111111100000000","teachingBuildingName":"博学楼","classroomName":"201"}]}]}]}
     */
    public static KbResult parseUrpJson(String body) {
        KbResult res = new KbResult();
        if (body == null || !body.contains("timeAndPlaceList")) return res;
        try {
            Object parsed = Json.parse(body.trim());
            Map<String, Object> root = Json.asMap(parsed);
            if (root == null) return res;
            List<Object> dateList = Json.asList(root.get("dateList"));
            List<Object> courseItems = new ArrayList<>();
            if (dateList != null) {
                for (Object dl : dateList) {
                    Map<String, Object> dm = Json.asMap(dl);
                    if (dm == null) continue;
                    List<Object> scl = Json.asList(dm.get("selectCourseList"));
                    if (scl != null) courseItems.addAll(scl);
                }
            }
            if (courseItems.isEmpty()) {
                List<Object> direct = Json.asList(root.get("selectCourseList"));
                if (direct != null) courseItems.addAll(direct);
            }
            for (Object ci : courseItems) {
                Map<String, Object> cm = Json.asMap(ci);
                if (cm == null) continue;
                String name = Json.str(cm, "courseName", "kcmc");
                if (name == null) continue;
                String teacher = Json.str(cm, "attendClassTeacher", "teacherName", "jsxm");
                if (teacher == null) teacher = "";
                teacher = teacher.replace("*", "").trim();

                List<Object> tpl = Json.asList(cm.get("timeAndPlaceList"));
                if (tpl == null) continue;
                for (Object tpObj : tpl) {
                    Map<String, Object> tp = Json.asMap(tpObj);
                    if (tp == null) continue;
                    int day = pickInt(tp, "classDay", "xq", "xqj");
                    int start = pickInt(tp, "classSessions", "ksjc");
                    int span = pickInt(tp, "continuingSession", "skcd");
                    if (day < 1 || day > 7 || start < 1) continue;
                    int end = span > 0 ? (start + span - 1) : start;

                    Course c = new Course();
                    c.name = name.trim();
                    c.teacher = teacher;
                    c.dayOfWeek = day;
                    c.startNode = start;
                    c.endNode = Math.max(start, end);

                    String bld = Json.str(tp, "teachingBuildingName", "jxlmc");
                    String cls = Json.str(tp, "classroomName", "jsmc", "cdmc");
                    String room = ((bld == null ? "" : bld) + (cls == null ? "" : cls)).trim();
                    c.room = room;

                    String cw = Json.str(tp, "classWeek", "skzc");
                    if (cw != null && isBitmap(cw)) {
                        c.weeks = parseWeekBitmap(cw, false);
                    } else {
                        String wd = Json.str(tp, "weekDescription", "zcd");
                        if (wd != null) {
                            c.weeksRaw = wd;
                            c.weeks = parseWeeks(wd);
                        }
                    }
                    res.courses.add(c);
                }
            }
            if (!res.courses.isEmpty()) res.courses = mergeWeeks(res.courses);
        } catch (Exception ignored) {
        }
        return res;
    }

    /**
     * FullCalendar 风格 JSON 事件解析
     */
    public static KbResult parseJsonEvents(String body) {
        KbResult res = new KbResult();
        if (body == null) return res;
        String b = body.trim();
        if (b.startsWith("\uFEFF")) b = b.substring(1);
        if (!b.startsWith("[") && !b.startsWith("{")) return res;
        try {
            List<Object> arr = null;
            if (b.startsWith("{")) {
                Map<String, Object> root = Json.asMap(Json.parse(b));
                for (String k : new String[]{"data", "rows", "list", "events", "result"}) {
                    List<Object> a = Json.asList(root == null ? null : root.get(k));
                    if (a != null && !a.isEmpty()) {
                        arr = a;
                        break;
                    }
                }
            } else {
                arr = Json.asList(Json.parse(b));
            }
            if (arr == null || arr.isEmpty()) return res;

            for (Object item : arr) {
                Map<String, Object> o = Json.asMap(item);
                if (o == null) continue;

                String title = Json.str(o, "title", "name", "kcmc", "kcmcstr", "courseName", "text");
                if (title == null || title.isEmpty()) continue;

                String start = Json.str(o, "start", "startTime", "kssj", "rq", "date", "time");
                if (start == null) continue;

                long ms = parseAnyTime(start);
                if (ms <= 0) continue;

                java.util.Calendar c = java.util.Calendar.getInstance();
                c.setTimeInMillis(ms);
                int dow = c.get(java.util.Calendar.DAY_OF_WEEK);
                dow = dow == java.util.Calendar.SUNDAY ? 7 : dow - 1;

                Course k = new Course();
                k.name = title.trim();
                k.dayOfWeek = dow;
                int node = nodeFromClock(c.get(java.util.Calendar.HOUR_OF_DAY),
                        c.get(java.util.Calendar.MINUTE));
                k.startNode = node;
                k.endNode = node;

                String end = Json.str(o, "end", "endTime", "jssj");
                if (end != null) {
                    long em = parseAnyTime(end);
                    if (em > ms) {
                        java.util.Calendar c2 = java.util.Calendar.getInstance();
                        c2.setTimeInMillis(em);
                        int n2 = nodeFromClock(c2.get(java.util.Calendar.HOUR_OF_DAY),
                                c2.get(java.util.Calendar.MINUTE));
                        if (n2 >= node) k.endNode = n2;
                    }
                }

                String room = Json.str(o, "room", "place", "address", "location", "cdd", "cdmc");
                if (room != null) k.room = room.trim();
                String tea = Json.str(o, "teacher", "rkjs", "jsxm");
                if (tea != null) k.teacher = tea.trim();
                res.courses.add(k);
            }
            if (!res.courses.isEmpty()) res.courses = dedupe(res.courses);
        } catch (Exception ignored) {
        }
        return res;
    }

    private static List<Course> dedupe(List<Course> in) {
        LinkedHashMap<String, Course> map = new LinkedHashMap<>();
        for (Course c : in) {
            String key = c.dayOfWeek + "|" + c.startNode + "|" + c.endNode + "|" + c.name + "|" + c.room;
            if (!map.containsKey(key)) map.put(key, c);
        }
        return new ArrayList<>(map.values());
    }

    private static long parseAnyTime(String s) {
        if (s == null) return 0;
        String t = s.trim().replace('T', ' ').replace('/', '-');
        Matcher m = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})[ ]?(\\d{1,2})?:?(\\d{2})?").matcher(t);
        if (!m.find()) return 0;
        int y = toI(m.group(1));
        int mo = toI(m.group(2));
        int d = toI(m.group(3));
        int hh = m.group(4) == null ? 8 : toI(m.group(4));
        int mm = m.group(5) == null ? 0 : toI(m.group(5));
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.clear();
        c.set(y, mo - 1, d, hh, mm, 0);
        return c.getTimeInMillis();
    }

    /** 按上课时刻推节次（通用高校作息） */
    private static int nodeFromClock(int hour, int minute) {
        int mins = hour * 60 + minute;
        int[][] slots = {
                {8 * 60, 1}, {8 * 60 + 55, 2}, {10 * 60 + 10, 3}, {11 * 60 + 5, 4},
                {14 * 60, 5}, {14 * 60 + 55, 6}, {16 * 60 + 10, 7}, {17 * 60 + 5, 8},
                {19 * 60, 9}, {19 * 60 + 55, 10}, {20 * 60 + 50, 11}, {21 * 60 + 45, 12}
        };
        int best = 1;
        for (int[] s : slots) {
            if (mins >= s[0] - 15) best = s[1];
        }
        return best;
    }

    /**
     * 通用 JSON 列表课表解析：
     * 兼容新正方 kbList、EasyUI datagrid rows、以及金智 EMAP 嵌套 datas.<any>.rows。
     */
    public static KbResult parseDataList(String body) {
        KbResult res = new KbResult();
        if (body == null) return res;
        String b = body.trim();
        if (b.startsWith("\uFEFF")) b = b.substring(1);
        if (!b.startsWith("{") && !b.startsWith("[")) return res;
        try {
            List<Object> rows = extractRowArray(Json.parse(b));
            if (rows == null || rows.isEmpty()) return res;

            Map<String, Object> first = Json.asMap(rows.get(0));
            if (first != null) {
                StringBuilder kb = new StringBuilder();
                for (String kk : first.keySet()) {
                    if (kb.length() > 0) kb.append(',');
                    kb.append(kk);
                }
                res.rawKeys = kb.toString();
            }

            for (Object rowObj : rows) {
                Map<String, Object> o = Json.asMap(rowObj);
                if (o == null) continue;

                String name = Json.str(o, "kcmc", "kcm", "kcmcstr", "kcmcStr", "kcname", "courseName",
                        "kcmc_show", "course", "name");
                if (name == null) continue;

                Course k = new Course();
                k.name = name.trim();
                if (k.name.contains("<")) k.name = Html.text(k.name);

                String room = Json.str(o, "jxcdmc", "cdmc", "jasmc", "cdmcstr", "skdd", "skddstr",
                        "jsmc", "cddm", "room", "place", "location", "skcd", "xqmc");
                if (room != null) k.room = Html.text(room).trim();

                String tea = Json.str(o, "teaxms", "xm", "skjs", "jsxm", "rkjs", "jsname", "teacher",
                        "jsxmstr", "rkjsxm", "teaxm");
                if (tea != null) k.teacher = Html.text(tea).trim();

                String weekStr = Json.str(o, "zcd", "skzc", "zc", "zcdm", "zcstr", "zcmc", "week", "weeks");
                if (weekStr != null) {
                    k.weeksRaw = weekStr.trim();
                    if (isBitmap(k.weeksRaw)) k.weeks = parseWeekBitmap(k.weeksRaw, false);
                    else k.weeks = parseWeeks(k.weeksRaw);
                }

                int day = pickInt(o, "xq", "xqj", "skxq", "kxq", "xingqi", "weekday", "dayofweek");
                if (day <= 0) {
                    String xqmc = Json.str(o, "xqjmc", "xqmc_display", "skxq_display");
                    if (xqmc != null) day = dowOf(xqmc);
                }
                int s = pickInt(o, "ksjc", "startjc", "ksj", "jc1", "startnode");
                int e = pickInt(o, "jsjc", "endjc", "jsj", "jc2", "endnode");

                String jcdm = Json.str(o, "jcdm", "jcs", "jc", "jcdmstr");
                if (jcdm != null) {
                    int[] jr = parseJcdm(jcdm);
                    if (jr[0] > 0) {
                        s = jr[0];
                        e = jr[1];
                    }
                }

                String timeTxt = Json.str(o, "sksj", "sksjstr", "sksjmc", "kssj", "sktime",
                        "jcsj", "sj", "time", "ksjcstr");
                if (timeTxt != null) timeTxt = Html.text(timeTxt);

                if ((day <= 0 || s <= 0) && timeTxt != null && !timeTxt.isEmpty()) {
                    if (day <= 0) day = dowOf(timeTxt);
                    int[] nr = extractNodeRangeFromText(timeTxt);
                    if (nr[0] > 0) {
                        if (s <= 0) s = nr[0];
                        if (e <= 0) e = nr[1];
                    }
                    if (k.weeks.length == 0) k.weeks = parseWeeksFromScheduleLine(timeTxt);
                }
                if (day <= 0 && weekStr != null) day = dowOf(weekStr);
                if (day <= 0 || day > 7) continue;
                if (s <= 0) s = 1;
                if (e < s) e = s;

                k.dayOfWeek = day;
                k.startNode = s;
                k.endNode = e;
                res.courses.add(k);
            }
            if (!res.courses.isEmpty()) res.courses = mergeWeeks(res.courses);
        } catch (Exception ignored) {
        }
        return res;
    }

    /** 支持顶层数组、常见列表字段、以及金智 EMAP 两层嵌套 datas.<action>.rows */
    public static List<Object> extractRowArray(Object parsed) {
        if (parsed == null) return null;
        List<Object> direct = Json.asList(parsed);
        if (direct != null) return direct;
        Map<String, Object> root = Json.asMap(parsed);
        if (root == null) return null;
        for (String kk : new String[]{"kbList", "rows", "items", "datas", "data", "list", "result"}) {
            List<Object> l = Json.asList(root.get(kk));
            if (l != null && !l.isEmpty()) return l;
        }
        // 嵌套解包：{"datas": {"cxxszhxqkb": {"rows": [...]}}} 或 {"data": {"rows": [...]}}
        for (String kk : new String[]{"datas", "data", "result"}) {
            Map<String, Object> sub = Json.asMap(root.get(kk));
            if (sub == null) continue;
            for (String inner : new String[]{"rows", "items", "kbList", "list"}) {
                List<Object> l = Json.asList(sub.get(inner));
                if (l != null && !l.isEmpty()) return l;
            }
            for (Object v : sub.values()) {
                Map<String, Object> m2 = Json.asMap(v);
                if (m2 == null) continue;
                for (String inner : new String[]{"rows", "items", "kbList", "list"}) {
                    List<Object> l = Json.asList(m2.get(inner));
                    if (l != null && !l.isEmpty()) return l;
                }
            }
        }
        return null;
    }

    /** 节次代码 -> [起始节, 结束节]，兼容 "01,02" / "01-02" / "0102" / "3-4节" / "03" */
    public static int[] parseJcdm(String s) {
        if (s == null) return new int[]{-1, -1};
        List<Integer> nums = new ArrayList<>();
        Matcher m = Pattern.compile("\\d{1,2}").matcher(s);
        while (m.find() && nums.size() < 8) {
            try {
                int v = Integer.parseInt(m.group());
                if (v >= 1 && v <= 20) nums.add(v);
            } catch (Exception ignored) {
            }
        }
        if (nums.isEmpty()) return new int[]{-1, -1};
        int a = nums.get(0);
        int b = nums.get(nums.size() - 1);
        if (b < a) b = a;
        return new int[]{a, b};
    }

    /** 合并同一门课多次出现的记录，周次取并集 */
    public static List<Course> mergeWeeks(List<Course> in) {
        LinkedHashMap<String, Course> map = new LinkedHashMap<>();
        for (Course c : in) {
            String key = c.dayOfWeek + "|" + c.startNode + "|" + c.endNode + "|"
                    + c.name + "|" + c.room;
            Course ex = map.get(key);
            if (ex == null) {
                Course n = new Course();
                n.name = c.name;
                n.room = c.room;
                n.teacher = c.teacher;
                n.dayOfWeek = c.dayOfWeek;
                n.startNode = c.startNode;
                n.endNode = c.endNode;
                n.weeksRaw = c.weeksRaw;
                n.weeks = c.weeks.clone();
                map.put(key, n);
            } else {
                TreeSet<Integer> set = new TreeSet<>();
                for (int w : ex.weeks) set.add(w);
                for (int w : c.weeks) set.add(w);
                ex.weeks = new int[set.size()];
                int i = 0;
                for (int w : set) ex.weeks[i++] = w;
                if (ex.teacher.isEmpty()) ex.teacher = c.teacher;
            }
        }
        return new ArrayList<>(map.values());
    }

    private static int pickInt(Map<String, Object> o, String... keys) {
        String s = Json.str(o, keys);
        if (s == null) return -1;
        Matcher m = Pattern.compile("(\\d{1,2})").matcher(s);
        if (m.find()) return toI(m.group(1));
        return -1;
    }

    /**
     * 通用多高校提取入口：同时尝试截获的 AJAX JSON（含框架内存数据）、视觉几何提取结果与页面/iframe HTML。
     * 优先选择有效课程数最多的结果；若带完整周次的 JSON 与无周次视图结果接近，优先保留带周次的全学期课表。
     */
    public static KbResult parseUniversal(List<String> jsonBlobs, List<String> htmlBlobs) {
        KbResult best = new KbResult();
        int bestScore = 0;
        if (jsonBlobs != null) {
            for (String j : jsonBlobs) {
                if (j == null || j.trim().isEmpty()) continue;
                KbResult[] cands = {
                        parseVisualJson(j),
                        parseUrpJson(j),
                        parseDataList(j),
                        parseJsonEvents(j)
                };
                for (KbResult r : cands) {
                    int sc = scoreResult(r);
                    if (sc > bestScore) {
                        best = r;
                        bestScore = sc;
                    }
                }
            }
        }
        if (htmlBlobs != null) {
            for (String h : htmlBlobs) {
                if (h == null || h.trim().isEmpty()) continue;
                KbResult r = parse(h);
                int sc = scoreResult(r);
                if (sc > bestScore) {
                    best = r;
                    bestScore = sc;
                }
            }
        }
        return best;
    }

    private static int scoreResult(KbResult r) {
        if (r == null || r.courses.isEmpty()) return 0;
        int withWeeks = 0;
        int totalWeekHits = 0;
        for (Course c : r.courses) {
            if (c.weeks != null && c.weeks.length > 0) {
                withWeeks++;
                totalWeekHits += Math.min(c.weeks.length, 20);
            }
        }
        // 课程数基础分 + 含周次加权 + 总周次覆盖度加权：
        // 确保全学期多周 JSON 永远优先于单周 JSON 或无周次视觉快照
        return r.courses.size() * 10 + withWeeks * 5 + totalWeekHits * 2;
    }

    /**
     * 解析浏览器 DOM 视觉几何提取器产出的结构化卡片 JSON：
     * {"__qkb_visual":true,"rows":[{"xq":1,"ksjc":3,"jsjc":4,"lines":["高等数学","软件24(1)","张明","明德楼201"]}]}
     */
    public static KbResult parseVisualJson(String body) {
        KbResult res = new KbResult();
        if (body == null || !body.contains("__qkb_visual")) return res;
        try {
            Map<String, Object> root = Json.asMap(Json.parse(body.trim()));
            if (root == null) return res;
            List<Object> rows = Json.asList(root.get("rows"));
            if (rows == null || rows.isEmpty()) return res;

            for (Object item : rows) {
                Map<String, Object> o = Json.asMap(item);
                if (o == null) continue;
                int dow = pickInt(o, "xq", "dow");
                int s = pickInt(o, "ksjc", "startNode");
                int e = pickInt(o, "jsjc", "endNode");
                if (dow < 1 || dow > 7 || s < 1) continue;
                if (e < s) e = s;

                List<Object> rawLines = Json.asList(o.get("lines"));
                List<String> lines = new ArrayList<>();
                if (rawLines != null) {
                    for (Object lo : rawLines) {
                        if (lo == null) continue;
                        String t = String.valueOf(lo).trim();
                        if (!t.isEmpty()) lines.add(t);
                    }
                }
                if (lines.isEmpty()) continue;

                Course c = new Course();
                c.dayOfWeek = dow;
                c.startNode = s;
                c.endNode = e;
                c.name = lines.get(0);

                for (int i = 1; i < lines.size(); i++) {
                    String ln = lines.get(i);
                    if (ln.equals(c.name)) continue;
                    if (c.weeks.length == 0 && looksWeeks(ln)) {
                        c.weeksRaw = ln;
                        c.weeks = parseWeeksFromScheduleLine(ln);
                        continue;
                    }
                    // 跳过班级名（如 "计算机24(1)" / "机械2024级1班"）
                    if (looksClassName(ln)) continue;
                    if (c.room.isEmpty() && looksRoom(ln)) {
                        c.room = ln;
                    } else if (c.teacher.isEmpty() && looksTeacher(ln)) {
                        c.teacher = ln;
                    }
                }
                // 兜底：若还没找到教室，取最后一行非教师、非班级、非周次的文本
                if (c.room.isEmpty() && lines.size() >= 2) {
                    for (int i = lines.size() - 1; i >= 1; i--) {
                        String ln = lines.get(i);
                        if (!ln.equals(c.name) && !looksWeeks(ln) && !looksTeacher(ln) && !looksClassName(ln)) {
                            c.room = ln;
                            break;
                        }
                    }
                }
                if (!c.name.isEmpty()) res.courses.add(c);
            }
            if (!res.courses.isEmpty()) res.courses = mergeWeeks(res.courses);
        } catch (Exception ignored) {
        }
        return res;
    }

    private static boolean looksClassName(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.endsWith("班") || t.contains("级(") || t.contains("级（")) return true;
        // 常见「专业+两位数年级+(班号)」如 "机械24(1)"、"软件工程23(2..."
        return t.matches("^[\\u4e00-\\u9fa5]{2,12}\\d{2,4}[\\(（].*");
    }

    private static int findCol(Map<String, Integer> map, String... keys) {
        for (String k : keys) if (map.containsKey(k)) return map.get(k);
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            for (String k : keys) if (e.getKey().contains(k)) return e.getValue();
        }
        return -1;
    }

    private static Course parseBlock(String blockHtml, int dow, int start, int end) {
        List<String> lines = Html.lines(blockHtml);
        if (lines.isEmpty()) return null;

        Course c = new Course();
        c.dayOfWeek = dow;
        c.startNode = start;
        c.endNode = Math.max(start, end);

        int weekIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            String ln = lines.get(i);
            // 优先检查本行是否自带精确节次，如强智 "[01-02节]" 或老正方 "周一第1,2节{第1-16周}"
            int[] nr = extractNodeRangeFromText(ln);
            if (nr[0] > 0) {
                c.startNode = nr[0];
                c.endNode = nr[1];
            }
            if (weekIdx < 0 && looksWeeks(ln)) {
                weekIdx = i;
            }
        }
        if (weekIdx >= 0) {
            c.weeksRaw = lines.get(weekIdx);
            c.weeks = parseWeeksFromScheduleLine(c.weeksRaw);
        }

        c.name = lines.get(0).trim();
        Matcher nm = Pattern.compile("^(.*?)[\\(（]([0-9一二三四五六七八九十\\-－,，单双周]+)[\\)）]$").matcher(c.name);
        if (nm.matches()) {
            c.name = nm.group(1).trim();
            c.weeksRaw = nm.group(2);
            c.weeks = parseWeeks(c.weeksRaw);
        }

        for (int i = 1; i < lines.size(); i++) {
            String s = lines.get(i).trim();
            if (i == weekIdx || s.isEmpty() || s.equals(c.name)) continue;
            // 去掉常见的纯括号节次标记行，防止误认成教室
            if (s.matches("^\\[?\\d{1,2}(?:[-,，~]\\d{1,2})*节\\]?$")) continue;
            if (looksRoom(s) && c.room.isEmpty()) c.room = s;
            else if (looksTeacher(s) && c.teacher.isEmpty()) c.teacher = s;
        }
        if (c.room.isEmpty() && lines.size() >= 3) {
            for (int i = lines.size() - 1; i >= 1; i--) {
                if (i == weekIdx) continue;
                String s = lines.get(i).trim();
                if (!s.isEmpty() && !s.equals(c.name) && !looksTeacher(s)
                        && !s.matches("^\\[?\\d{1,2}(?:[-,，~]\\d{1,2})*节\\]?$")) {
                    c.room = s;
                    break;
                }
            }
        }
        return c;
    }

    /** 从任意文本提取明确节次范围（如 "[01-02节]" / "第1,2节" / "第3-4节"） */
    private static int[] extractNodeRangeFromText(String s) {
        if (s == null || s.isEmpty()) return new int[]{-1, -1};
        Matcher m1 = Pattern.compile("\\[(\\d{1,2}(?:\\s*[-,，~]\\s*\\d{1,2})*)\\s*节?\\]").matcher(s);
        if (m1.find()) return parseJcdm(m1.group(1));
        Matcher m2 = Pattern.compile("第?\\s*(\\d{1,2})\\s*[-~至]\\s*(\\d{1,2})\\s*节").matcher(s);
        if (m2.find()) {
            int a = toI(m2.group(1)), b = toI(m2.group(2));
            if (a > 0 && b >= a) return new int[]{a, b};
        }
        Matcher m3 = Pattern.compile("第\\s*(\\d{1,2}(?:\\s*[,，]\\s*\\d{1,2})+)\\s*节").matcher(s);
        if (m3.find()) return parseJcdm(m3.group(1));
        Matcher m4 = Pattern.compile("第\\s*(\\d{1,2})\\s*节").matcher(s);
        if (m4.find()) {
            int a = toI(m4.group(1));
            if (a > 0) return new int[]{a, a};
        }
        return new int[]{-1, -1};
    }

    /** 从带节次/花括号的排课行里干净抽出周次，避免把「第1,2节」「[01-02节]」吃进周次 */
    public static int[] parseWeeksFromScheduleLine(String line) {
        if (line == null || line.isEmpty()) return new int[0];
        // 老正方格式：周一第1,2节{第1-16周|单周} -> 优先取花括号内部
        Matcher brace = Pattern.compile("\\{([^}]+)\\}").matcher(line);
        if (brace.find()) {
            return parseWeeks(brace.group(1));
        }
        // 强智格式：1-16(周)[01-02节] -> 先删去 [01-02节] 和 第1,2节
        String clean = line.replaceAll("\\[[^\\]]*节[^\\]]*\\]", " ")
                .replaceAll("第?\\s*\\d{1,2}(?:\\s*[-,，~至]\\s*\\d{1,2})*\\s*节", " ");
        return parseWeeks(clean);
    }

    public static int dowOf(String s) {
        if (s == null) return -1;
        String trimmed = s.trim();
        Matcher m = Pattern.compile("(?:星期|周)([一二三四五六日天])").matcher(trimmed);
        if (m.find()) return cnDow(m.group(1));
        // 兼容日历表头「一 11/23」「二 11-24」「三(11/25)」或纯单字「一」..「日」
        Matcher mShort = Pattern.compile("^([一二三四五六日天])(?:\\s*[\\(（]?\\d{1,2}[/\\-.]\\d{1,2}[\\)）]?)?$").matcher(trimmed);
        if (mShort.find()) return cnDow(mShort.group(1));
        String low = trimmed.toLowerCase();
        if (low.contains("mon")) return 1;
        if (low.contains("tue")) return 2;
        if (low.contains("wed")) return 3;
        if (low.contains("thu")) return 4;
        if (low.contains("fri")) return 5;
        if (low.contains("sat")) return 6;
        if (low.contains("sun")) return 7;
        return -1;
    }

    private static int cnDow(String d) {
        if ("一".equals(d)) return 1;
        if ("二".equals(d)) return 2;
        if ("三".equals(d)) return 3;
        if ("四".equals(d)) return 4;
        if ("五".equals(d)) return 5;
        if ("六".equals(d)) return 6;
        return 7;
    }

    /** 解析表格行头的节次范围：兼容单节「第一节」、大节「第一大节(01-02)」「1-2节」「01,02」 */
    public static int[] nodeRangeOf(String s) {
        if (s == null || s.isEmpty()) return new int[]{-1, -1};
        // 先去掉时间段如 08:00-09:40，避免被误认成节次
        String clean = s.replaceAll("\\d{1,2}[:：]\\d{2}\\s*[-~至]?\\s*(?:\\d{1,2}[:：]\\d{2})?", " ");

        // 明确的小节范围：01-02节 / 1-2 / 01,02节
        Matcher mr = Pattern.compile("(\\d{1,2})\\s*[-－~至,，]\\s*(\\d{1,2})\\s*节?").matcher(clean);
        if (mr.find()) {
            int a = toI(mr.group(1)), b = toI(mr.group(2));
            if (a >= 1 && b >= a && b <= 20) return new int[]{a, b};
        }

        // 「第一大节」「第二大节」…（默认每大节 2 小节）
        for (int i = 0; i < Math.min(CN_NUM.length, 7); i++) {
            if (clean.contains("第" + CN_NUM[i] + "大节")) {
                int st = i * 2 + 1;
                return new int[]{st, st + 1};
            }
        }
        Matcher mb = Pattern.compile("第\\s*(\\d)\\s*大节").matcher(clean);
        if (mb.find()) {
            int k = toI(mb.group(1));
            if (k >= 1 && k <= 7) return new int[]{k * 2 - 1, k * 2};
        }

        int single = nodeOf(clean);
        if (single > 0) return new int[]{single, single};
        return new int[]{-1, -1};
    }

    public static int nodeOf(String s) {
        if (s == null) return -1;
        Matcher m = Pattern.compile("第\\s*(\\d{1,2})\\s*节").matcher(s);
        if (m.find()) return toI(m.group(1));
        for (int i = 0; i < CN_NUM.length; i++) {
            if (s.contains("第" + CN_NUM[i] + "节")) return i + 1;
        }
        Matcher m2 = Pattern.compile("^\\s*(\\d{1,2})\\s*(?:[-－]\\s*(\\d{1,2}))?\\s*节?\\s*$").matcher(s);
        if (m2.find()) return toI(m2.group(1));
        return -1;
    }

    private static int toI(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static boolean isBitmap(String s) {
        return s != null && s.length() >= 10 && s.matches("^[01]+$");
    }

    /**
     * 解析 01 位图周次：
     * - 树维 EAMS（zeroIndexed=true）：53 位串，第 0 位常占位 '0'，索引 i 即第 i 周
     * - URP / 金智 EMAP（zeroIndexed=false）：索引 i 对应第 i+1 周
     */
    public static int[] parseWeekBitmap(String bits, boolean zeroIndexed) {
        if (bits == null || bits.isEmpty()) return new int[0];
        TreeSet<Integer> set = new TreeSet<>();
        for (int i = 0; i < bits.length(); i++) {
            if (bits.charAt(i) == '1') {
                int w = zeroIndexed ? i : (i + 1);
                if (w >= 1 && w <= 30) set.add(w);
            }
        }
        int[] out = new int[set.size()];
        int k = 0;
        for (int w : set) out[k++] = w;
        return out;
    }

    private static boolean looksWeeks(String s) {
        if (s == null || !s.contains("周")) return false;
        return s.matches(".*\\d.*") || s.contains("单周") || s.contains("双周");
    }

    private static boolean looksRoom(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isEmpty() || t.length() > 24) return false;
        if (t.contains("周")) return false;
        if (t.matches("^[A-Za-z]?\\d{1,2}[-－]\\d{2,4}$")) return true;
        if (t.matches(".*(馆|场|厅|楼|室|机房|实验|中心|基地|栋|座|校区).*")) return true;
        boolean digit = t.matches(".*\\d.*");
        boolean kw = t.matches(".*(教|A区|B区|C区).*");
        return digit && kw;
    }

    private static boolean looksTeacher(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.length() < 2 || t.length() > 6) return false;
        if (t.matches(".*\\d.*")) return false;
        if (t.matches(".*(馆|场|厅|楼|室|机房|实验|中心|基地|栋|座|校区|班).*")) return false;
        return t.matches("^[\\u4e00-\\u9fa5·]{2,6}$");
    }

    /**
     * 解析各种中文/符号周次写法，逐段独立判定单双周，支持混写如 "1-7周(单),8-14周(双)"、"1-8,10-16(周)"、"1-16周"。
     */
    public static int[] parseWeeks(String s) {
        if (s == null || s.isEmpty()) return new int[0];
        String trimmed = s.trim();
        if (isBitmap(trimmed)) return parseWeekBitmap(trimmed, false);

        String t = trimmed.replace('（', '(').replace('）', ')').replace('，', ',')
                .replace('、', ',').replace(';', ',').replace('；', ',')
                .replace('－', '-').replace('~', '-').replace('至', '-');

        // 如果整个串末尾带全局「(单)/(双)」，例如 "1-5,7-15周(单)"，保留为默认奇偶过滤
        boolean globalOdd = false, globalEven = false;
        if (!t.contains(",")) {
            globalOdd = t.contains("单");
            globalEven = t.contains("双");
        } else {
            String[] segsCheck = t.split(",");
            int segWithParity = 0;
            for (String sg : segsCheck) {
                if (sg.contains("单") || sg.contains("双")) segWithParity++;
            }
            if (segWithParity == 1 && (segsCheck[segsCheck.length - 1].contains("单")
                    || segsCheck[segsCheck.length - 1].contains("双"))) {
                // 仅末段标注单/双且前面无单双字样，按整串统一奇偶处理（如 "1-7,9-15周(单)"）
                String last = segsCheck[segsCheck.length - 1];
                globalOdd = last.contains("单");
                globalEven = last.contains("双");
            }
        }

        TreeSet<Integer> set = new TreeSet<>();
        for (String seg : t.split(",")) {
            String p = seg.trim();
            if (p.isEmpty()) continue;
            boolean odd = p.contains("单") || (globalOdd && !p.contains("双"));
            boolean even = p.contains("双") || (globalEven && !p.contains("单"));

            TreeSet<Integer> local = new TreeSet<>();
            StringBuilder rest = new StringBuilder(p);
            Matcher m = Pattern.compile("(\\d{1,2})\\s*-\\s*(\\d{1,2})").matcher(p);
            while (m.find()) {
                int a = toI(m.group(1));
                int b = toI(m.group(2));
                if (a > 0 && b >= a) for (int w = a; w <= b && w <= 30; w++) local.add(w);
                for (int k = m.start(); k < m.end(); k++) rest.setCharAt(k, ' ');
            }
            Matcher m2 = Pattern.compile("(\\d{1,2})").matcher(rest.toString());
            while (m2.find()) {
                int v = toI(m2.group(1));
                if (v >= 1 && v <= 30) local.add(v);
            }
            for (int w : local) {
                if (odd && !even && w % 2 == 0) continue;
                if (even && !odd && w % 2 == 1) continue;
                set.add(w);
            }
        }

        int[] out = new int[set.size()];
        int i = 0;
        for (int w : set) out[i++] = w;
        return out;
    }

    private static int count(String hay, String needle) {
        int n = 0, i = 0;
        while ((i = hay.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }
}
