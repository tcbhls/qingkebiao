import com.qingkebiao.app.Course;
import com.qingkebiao.app.Exam;
import com.qingkebiao.app.ExamParser;
import com.qingkebiao.app.KbParser;
import com.qingkebiao.app.KbResult;
import com.qingkebiao.app.Prefs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/** 多高校课表与考试解析器全量单元测试（覆盖 7 大主流教务系统与日期回归测试） */
public class ParserTest {

    static int pass = 0, fail = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            pass++;
            System.out.println("  PASS  " + name + (detail.isEmpty() ? "" : "  " + detail));
        } else {
            fail++;
            System.out.println("  FAIL  " + name + "  " + detail);
        }
    }

    static String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
    }

    static Course find(List<Course> list, String name) {
        for (Course c : list) {
            if (c.name.contains(name)) return c;
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : "app" + File.separator + "src" + File.separator + "test" + File.separator + "fixtures";

        // 1) 标准 HTML 网格表
        System.out.println("== 1. standard HTML grid ==");
        KbResult kb = KbParser.parse(read(dir + File.separator + "kb.html"));
        check("grid count == 4", kb.courses.size() == 4, "got " + kb.courses.size());
        Course math = find(kb.courses, "高等数学A");
        check("grid math", math != null && math.dayOfWeek == 1 && math.startNode == 1
                && math.endNode == 2 && "明德楼201".equals(math.room) && math.weeks.length == 16,
                String.valueOf(math));
        Course phy = find(kb.courses, "大学物理");
        check("grid physics odd weeks", phy != null && phy.dayOfWeek == 4
                && Arrays.equals(phy.weeks, new int[]{1, 3, 5, 7}),
                phy == null ? "null" : Arrays.toString(phy.weeks));
        check("grid nodeTime", "08:00".equals(kb.nodeTime.get(1)) && "14:00".equals(kb.nodeTime.get(5)),
                String.valueOf(kb.nodeTime));
        check("grid weekHint == 13", "13".equals(kb.weekHint), kb.weekHint);

        // 2) 强智 jsxsd 大节网格表（大节行头 01-02节 + 同格 ----- 单双周冲突双课）
        System.out.println("\n== 2. QiangZhi jsxsd big-slot + multi-course cell ==");
        KbResult qz = KbParser.parse(read(dir + File.separator + "kb-qz-bigslot.html"));
        check("qz count == 3", qz.courses.size() == 3, "got " + qz.courses.size());
        Course la = find(qz.courses, "线性代数");
        check("qz linear-alg nodes 1-2 odd", la != null && la.dayOfWeek == 1 && la.startNode == 1
                && la.endNode == 2 && "笃行楼301".equals(la.room) && la.weeks.length == 8
                && la.activeInWeek(1) && !la.activeInWeek(2),
                la == null ? "null" : (la + " " + Arrays.toString(la.weeks)));
        Course prob = find(qz.courses, "概率论");
        check("qz prob nodes 1-2 even", prob != null && prob.dayOfWeek == 1 && prob.startNode == 1
                && prob.endNode == 2 && "笃行楼302".equals(prob.room) && prob.weeks.length == 8
                && !prob.activeInWeek(1) && prob.activeInWeek(2),
                prob == null ? "null" : (prob + " " + Arrays.toString(prob.weeks)));
        Course net = find(qz.courses, "计算机网络");
        check("qz net nodes 3-4 skip week 9", net != null && net.dayOfWeek == 2
                && net.startNode == 3 && net.endNode == 4 && net.weeks.length == 15
                && !net.activeInWeek(9) && net.activeInWeek(10),
                net == null ? "null" : (net + " " + Arrays.toString(net.weeks)));

        // 3) 老正方 xskbcx.aspx（<br><br> 切分多课 + 周一第1,2节{第1-16周|单周}）
        System.out.println("\n== 3. Old ZhengFang xskbcx HTML ==");
        KbResult zfo = KbParser.parse(read(dir + File.separator + "kb-zf-old.html"));
        check("zf-old count == 2", zfo.courses.size() == 2, "got " + zfo.courses.size());
        Course eng = find(zfo.courses, "大学英语");
        check("zf-old eng odd weeks clean", eng != null && eng.startNode == 1 && eng.endNode == 2
                && eng.weeks.length == 8 && eng.activeInWeek(1) && !eng.activeInWeek(2)
                && "外语楼205".equals(eng.room),
                eng == null ? "null" : (eng + " " + Arrays.toString(eng.weeks)));
        Course phil = find(zfo.courses, "西方哲学史");
        check("zf-old phil even weeks clean", phil != null && phil.startNode == 1 && phil.endNode == 2
                && phil.weeks.length == 8 && !phil.activeInWeek(1) && phil.activeInWeek(2)
                && "人文楼108".equals(phil.room),
                phil == null ? "null" : (phil + " " + Arrays.toString(phil.weeks)));

        // 4) 树维 EAMS 内联 JS TaskActivity
        System.out.println("\n== 4. TreeDim EAMS TaskActivity JS ==");
        KbResult eams = KbParser.parse(read(dir + File.separator + "kb-eams-treedim.html"));
        check("eams count == 2", eams.courses.size() == 2, "got " + eams.courses.size());
        Course comp = find(eams.courses, "编译原理");
        check("eams compiler dow 3 nodes 3-4 weeks 1-16", comp != null && comp.dayOfWeek == 3
                && comp.startNode == 3 && comp.endNode == 4 && comp.weeks.length == 16
                && "理科楼301".equals(comp.room) && "钱老师".equals(comp.teacher),
                comp == null ? "null" : (comp + " " + Arrays.toString(comp.weeks)));
        Course se = find(eams.courses, "软件工程");
        check("eams se dow 5 nodes 5-6 even weeks", se != null && se.dayOfWeek == 5
                && se.startNode == 5 && se.endNode == 6 && se.weeks.length == 8
                && !se.activeInWeek(1) && se.activeInWeek(2),
                se == null ? "null" : (se + " " + Arrays.toString(se.weeks)));

        // 5) 列表式 HTML 课表
        System.out.println("\n== 5. list-style HTML table ==");
        KbResult lkb = KbParser.parse(read(dir + File.separator + "kb-list.html"));
        check("list count == 3", lkb.courses.size() == 3, "got " + lkb.courses.size());
        Course lm = find(lkb.courses, "高等数学A");
        check("list math", lm != null && lm.dayOfWeek == 1 && lm.startNode == 1 && lm.endNode == 2
                && lm.weeks.length == 16, String.valueOf(lm));

        // 6) EasyUI datagrid JSON + 周次合并
        System.out.println("\n== 6. EasyUI datagrid JSON + mergeWeeks ==");
        KbResult dl = KbParser.parseDataList(read(dir + File.separator + "datalist.json"));
        check("datalist count == 4", dl.courses.size() == 4, "got " + dl.courses.size());
        Course dc = find(dl.courses, "电路分析");
        check("datalist sksj circuit", dc != null && dc.dayOfWeek == 2 && dc.startNode == 3
                && dc.endNode == 4 && dc.weeks.length == 13, String.valueOf(dc));

        KbResult merged = KbParser.parseDataList(read(dir + File.separator + "datalist-merge.json"));
        check("merged count == 4", merged.courses.size() == 4, "got " + merged.courses.size());
        Course dm = find(merged.courses, "离散数学");
        check("merged weeks [12,13]", dm != null && Arrays.equals(dm.weeks, new int[]{12, 13})
                && dm.startNode == 1 && dm.endNode == 2 && "明德楼201".equals(dm.room),
                dm == null ? "null" : Arrays.toString(dm.weeks));
        Course single = find(merged.courses, "程序设计基础");
        check("single node 3-3", single != null && single.startNode == 3 && single.endNode == 3,
                String.valueOf(single));

        // 7) 新正方 jwglxt JSON
        System.out.println("\n== 7. New ZhengFang jwglxt JSON ==");
        KbResult zfKb = KbParser.parseUniversal(
                Arrays.asList(read(dir + File.separator + "zf-new-kb.json")), null);
        check("zf-new kb count == 2", zfKb.courses.size() == 2, "got " + zfKb.courses.size());
        Course z0 = find(zfKb.courses, "数据结构与算法");
        check("zf-new ds", z0 != null && z0.dayOfWeek == 1 && z0.startNode == 3 && z0.endNode == 4
                && "张明".equals(z0.teacher), String.valueOf(z0));

        // 8) 金智 EMAP 两层嵌套 JSON + 01 位图周次
        System.out.println("\n== 8. Kingosoft/Wisedu EMAP nested JSON ==");
        KbResult emapKb = KbParser.parseUniversal(
                Arrays.asList(read(dir + File.separator + "emap-kb.json")), null);
        check("emap kb count == 2", emapKb.courses.size() == 2, "got " + emapKb.courses.size());
        Course ai = find(emapKb.courses, "人工智能导论");
        check("emap ai", ai != null && ai.dayOfWeek == 2 && ai.startNode == 1 && ai.endNode == 2
                && ai.weeks.length == 16 && "知行楼401".equals(ai.room) && "吴教授".equals(ai.teacher),
                String.valueOf(ai));
        Course ml = find(emapKb.courses, "机器学习");
        check("emap ml odd bitmap", ml != null && ml.dayOfWeek == 4 && ml.startNode == 7 && ml.endNode == 8
                && ml.weeks.length == 8 && ml.activeInWeek(1) && !ml.activeInWeek(2),
                ml == null ? "null" : Arrays.toString(ml.weeks));

        // 9) 新 URP 综合教务 JSON
        System.out.println("\n== 9. New URP JSON ==");
        KbResult urpKb = KbParser.parseUniversal(
                Arrays.asList(read(dir + File.separator + "urp-kb.json")), null);
        check("urp kb count == 2", urpKb.courses.size() == 2, "got " + urpKb.courses.size());
        Course db = find(urpKb.courses, "数据库系统");
        check("urp db", db != null && db.dayOfWeek == 3 && db.startNode == 1 && db.endNode == 2
                && "明德楼305".equals(db.room) && "黄老师".equals(db.teacher) && db.weeks.length == 12,
                String.valueOf(db));
        Course arch = find(urpKb.courses, "计算机组成原理");
        check("urp arch 3-slot even", arch != null && arch.dayOfWeek == 5 && arch.startNode == 5
                && arch.endNode == 7 && arch.weeks.length == 6 && !arch.activeInWeek(1) && arch.activeInWeek(2),
                arch == null ? "null" : (arch + " " + Arrays.toString(arch.weeks)));

        // 10) 周次解析（含混写单双周）
        System.out.println("\n== 10. week parser ==");
        check("1-8 odd", Arrays.equals(KbParser.parseWeeks("1-8周(单)"), new int[]{1, 3, 5, 7}),
                Arrays.toString(KbParser.parseWeeks("1-8周(单)")));
        check("2-8 even", Arrays.equals(KbParser.parseWeeks("2-8周(双)"), new int[]{2, 4, 6, 8}),
                Arrays.toString(KbParser.parseWeeks("2-8周(双)")));
        check("mixed odd+even", Arrays.equals(KbParser.parseWeeks("1-5周(单),6-10周(双)"),
                new int[]{1, 3, 5, 6, 8, 10}),
                Arrays.toString(KbParser.parseWeeks("1-5周(单),6-10周(双)")));
        check("list 3,5,7", Arrays.equals(KbParser.parseWeeks("3,5,7周"), new int[]{3, 5, 7}),
                Arrays.toString(KbParser.parseWeeks("3,5,7周")));

        // 11) 考试安排（HTML 分列日期+时间、EasyUI、新正方、金智 EMAP）
        System.out.println("\n== 11. exam parsers (HTML split cols + EasyUI + ZF + EMAP) ==");
        List<Exam> exams = ExamParser.parse(read(dir + File.separator + "exam.html"));
        check("exam html count == 2", exams.size() == 2, "got " + exams.size());
        if (exams.size() == 2) {
            check("exam1 parsed", "高频电子线路".equals(exams.get(0).name)
                    && "明德楼305".equals(exams.get(0).room) && "12".equals(exams.get(0).seat)
                    && exams.get(0).startMillis > 0, exams.get(0).timeRaw);
            check("exam2 cn-date parsed", exams.get(1).startMillis > exams.get(0).startMillis,
                    exams.get(1).timeRaw);
        }

        List<Exam> ejl = ExamParser.parseDataListJson(read(dir + File.separator + "exam-datalist.json"));
        check("exam easyui count == 2", ejl.size() == 2, "got " + ejl.size());
        if (ejl.size() == 2) {
            check("exam easyui fields", "离散数学".equals(ejl.get(0).name)
                    && "明德楼103".equals(ejl.get(0).room) && ejl.get(0).startMillis > 0, ejl.get(0).timeRaw);
        }

        List<Exam> zfEx = ExamParser.parseUniversal(
                Arrays.asList(read(dir + File.separator + "zf-new-exam.json")), null);
        check("exam zf-new count == 2", zfEx.size() == 2 && "18".equals(zfEx.get(0).seat)
                && zfEx.get(1).startMillis > zfEx.get(0).startMillis, "got " + zfEx.size());

        List<Exam> emapEx = ExamParser.parseUniversal(
                Arrays.asList(read(dir + File.separator + "emap-exam.json")), null);
        check("exam emap count == 1", emapEx.size() == 1 && "人工智能导论".equals(emapEx.get(0).name)
                && "知行楼401".equals(emapEx.get(0).room) && "09".equals(emapEx.get(0).seat)
                && emapEx.get(0).startMillis > 0, "got " + emapEx.size());

        // 12) 日期偏移与东八区跨日回归测试
        System.out.println("\n== 12. dayLabel & timezone regression ==");
        check("Mon + 3 == Thu", "周四".equals(Prefs.dayLabel(1, 3)), Prefs.dayLabel(1, 3));
        check("Mon + 4 == Fri", "周五".equals(Prefs.dayLabel(1, 4)), Prefs.dayLabel(1, 4));
        check("Fri + 3 == Mon", "周一".equals(Prefs.dayLabel(5, 3)), Prefs.dayLabel(5, 3));
        check("Thu + 3 == Sun", "周日".equals(Prefs.dayLabel(4, 3)), Prefs.dayLabel(4, 3));

        Calendar mon1 = Calendar.getInstance();
        mon1.set(2026, Calendar.SEPTEMBER, 7, 0, 0, 0);
        mon1.set(Calendar.MILLISECOND, 0);

        Calendar sunMorning = Calendar.getInstance();
        sunMorning.set(2026, Calendar.SEPTEMBER, 13, 8, 30, 0);
        check("Sun 08:30 still week 1", Prefs.weekBetween(mon1, sunMorning) == 1,
                String.valueOf(Prefs.weekBetween(mon1, sunMorning)));

        Calendar sunNight = Calendar.getInstance();
        sunNight.set(2026, Calendar.SEPTEMBER, 13, 23, 55, 0);
        check("Sun 23:55 still week 1", Prefs.weekBetween(mon1, sunNight) == 1,
                String.valueOf(Prefs.weekBetween(mon1, sunNight)));

        Calendar mon2Early = Calendar.getInstance();
        mon2Early.set(2026, Calendar.SEPTEMBER, 14, 0, 5, 0);
        check("Mon 00:05 enters week 2", Prefs.weekBetween(mon1, mon2Early) == 2,
                String.valueOf(Prefs.weekBetween(mon1, mon2Early)));

        Calendar todayPm = Calendar.getInstance();
        todayPm.set(2026, Calendar.NOVEMBER, 30, 14, 0, 0);
        Calendar tmrAm = Calendar.getInstance();
        tmrAm.set(2026, Calendar.DECEMBER, 1, 8, 30, 0);
        check("Exam tomorrow morning == 1 day",
                Prefs.daysUntil(tmrAm.getTimeInMillis(), todayPm.getTimeInMillis()) == 1L,
                String.valueOf(Prefs.daysUntil(tmrAm.getTimeInMillis(), todayPm.getTimeInMillis())));

        Calendar sameDayPm = Calendar.getInstance();
        sameDayPm.set(2026, Calendar.DECEMBER, 1, 14, 40, 0);
        check("Exam today afternoon == 0 day",
                Prefs.daysUntil(sameDayPm.getTimeInMillis(), tmrAm.getTimeInMillis()) == 0L,
                String.valueOf(Prefs.daysUntil(sameDayPm.getTimeInMillis(), tmrAm.getTimeInMillis())));

        // 13) 日历视图短星期表头（如 "一 11/23"）与 DOM 视觉几何提取器测试
        System.out.println("\n== 13. short calendar header & visual geometry extractor ==");
        check("dowOf '一 11/23' == 1", KbParser.dowOf("一 11/23") == 1, String.valueOf(KbParser.dowOf("一 11/23")));
        check("dowOf '六 11/28' == 6", KbParser.dowOf("六 11/28") == 6, String.valueOf(KbParser.dowOf("六 11/28")));
        check("dowOf '日' == 7", KbParser.dowOf("日") == 7, String.valueOf(KbParser.dowOf("日")));

        String calHtml = "<html><body><table>"
                + "<tr><th></th><th>一 11/23</th><th>二 11/24</th><th>三 11/25</th><th>四 11/26</th><th>五 11/27</th></tr>"
                + "<tr><th>第01节</th><td></td><td>信号与系统<br>软件24(1)<br>陈老师<br>明德楼308</td><td></td><td></td><td></td></tr>"
                + "<tr><th>第02节</th><td></td><td>信号与系统<br>软件24(1)<br>陈老师<br>明德楼308</td><td></td><td></td><td></td></tr>"
                + "</table></body></html>";
        KbResult calRes = KbParser.parse(calHtml);
        check("short-header calendar table parsed", calRes.courses.size() == 1
                && calRes.courses.get(0).dayOfWeek == 2
                && calRes.courses.get(0).startNode == 1 && calRes.courses.get(0).endNode == 2,
                String.valueOf(calRes.courses));

        String visualJson = "{\"__qkb_visual\":true,\"rows\":["
                + "{\"xq\":1,\"ksjc\":3,\"jsjc\":4,\"lines\":[\"概率统计\",\"软件24(1)\",\"张明\",\"博学楼518\"]},"
                + "{\"xq\":2,\"ksjc\":1,\"jsjc\":2,\"lines\":[\"数字电路\",\"软件24(1)\",\"李华\",\"明德楼308\"]}"
                + "]}";
        String menuNoiseJson = "[{\"menuid\":\"1\",\"menuname\":\"课表查询\",\"menuurl\":\"/kb\"}]";
        KbResult visRes = KbParser.parseUniversal(Arrays.asList(menuNoiseJson, visualJson), null);
        check("visual extractor count == 2", visRes.courses.size() == 2, "got " + visRes.courses.size());
        Course vc0 = find(visRes.courses, "概率统计");
        check("visual skips class line & gets teacher/room", vc0 != null
                && vc0.dayOfWeek == 1 && vc0.startNode == 3 && vc0.endNode == 4
                && "张明".equals(vc0.teacher) && "博学楼518".equals(vc0.room),
                String.valueOf(vc0));

        // 验证全学期多周 JSON 永远优先于同课程数的单周 JSON 与视觉快照
        String singleWeekJson = "{\"rows\":["
                + "{\"kcmc\":\"概率统计\",\"xq\":\"1\",\"jcdm\":\"03,04\",\"jxcdmc\":\"博学楼518\",\"zc\":\"13\"},"
                + "{\"kcmc\":\"数字电路\",\"xq\":\"2\",\"jcdm\":\"01,02\",\"jxcdmc\":\"明德楼308\",\"zc\":\"13\"}"
                + "]}";
        String fullTermJson = "{\"rows\":["
                + "{\"kcmc\":\"概率统计\",\"xq\":\"1\",\"jcdm\":\"03,04\",\"jxcdmc\":\"博学楼518\",\"zc\":\"1-16\"},"
                + "{\"kcmc\":\"数字电路\",\"xq\":\"2\",\"jcdm\":\"01,02\",\"jxcdmc\":\"明德楼308\",\"zc\":\"1-16\"}"
                + "]}";
        KbResult prefFull = KbParser.parseUniversal(Arrays.asList(visualJson, singleWeekJson, fullTermJson), null);
        check("full-term JSON beats single-week & visual", prefFull.courses.size() == 2
                && prefFull.courses.get(0).weeks.length == 16,
                prefFull.courses.isEmpty() ? "empty" : ("weeks=" + prefFull.courses.get(0).weeks.length));

        System.out.println("\n========== " + pass + " passed, " + fail + " failed ==========");
        if (fail > 0) System.exit(1);
    }
}
