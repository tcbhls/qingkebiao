package com.qingkebiao.app;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 通用金智 CAS + EasyUI 自动后台刷新编排（基于用户填写的教务地址动态发现，无硬编码学校域名） */
public class Repo {

    public static class Result {
        public boolean ok;
        public String message = "";
        public int courses;
        public int exams;
    }

    private static String usable(String url) {
        if (url == null) return "";
        String u = url.trim();
        if (u.isEmpty() || u.contains("ssoLogin")) return "";
        return u;
    }

    public static Result refresh(Context ctx) {
        Result res = new Result();
        String jwUrl = SchoolProfile.customUrl(ctx);
        String casUrl = SchoolProfile.casUrl(ctx);
        String user = Prefs.user(ctx);
        String pass = Prefs.pass(ctx);

        if (jwUrl.isEmpty()) return fail(ctx, res, "请先填写学校教务系统网址");
        if (user.isEmpty() || pass.isEmpty()) return fail(ctx, res, "还没填学号和密码");

        String baseUrl = Http.originOf(jwUrl);
        if (baseUrl.isEmpty()) return fail(ctx, res, "教务网址格式有误，需以 http:// 或 https:// 开头");

        Http h = new Http();
        Cas.Result lr = Cas.login(h, jwUrl, casUrl, user, pass);
        if (!lr.ok) return fail(ctx, res, lr.message);
        if (lr.landingHtml == null || lr.landingHtml.isEmpty()) {
            return fail(ctx, res, "登录成功但落地页为空");
        }
        String landOrigin = Http.originOf(lr.landingUrl);
        if (!landOrigin.isEmpty()) baseUrl = landOrigin;

        List<Menu.Item> menu = Menu.fetch(h, baseUrl);
        dump(ctx, "menu.txt", Menu.dump(menu));
        String menuKb = Menu.pickKb(baseUrl, menu);
        String menuExam = Menu.pickExam(baseUrl, menu);

        // ---------- 课表 ----------
        String manualKb = usable(Prefs.manualKb(ctx));
        String savedKb = !manualKb.isEmpty() ? manualKb : usable(Prefs.kbUrl(ctx));

        String kbUrl = "";
        String kbHtml = "";

        if (!menuKb.isEmpty()) {
            Http.Resp r = h.getFollowing(menuKb);
            if (r != null && r.body != null && !Jw.isLoginPage(r.body)) {
                kbUrl = r.finalUrl == null || r.finalUrl.isEmpty() ? menuKb : r.finalUrl;
                kbHtml = r.body;
            }
        }

        String rootUrl = lr.landingUrl;
        String rootHtml = lr.landingHtml;
        if (kbHtml.isEmpty() && !savedKb.isEmpty()) {
            Http.Resp r = h.getFollowing(savedKb);
            if (r != null && r.body != null && !Jw.isLoginPage(r.body)) {
                String fu = r.finalUrl == null || r.finalUrl.isEmpty() ? savedKb : r.finalUrl;
                if (Discover.selfLikeKb(r.body)) {
                    kbUrl = fu;
                    kbHtml = r.body;
                } else {
                    rootUrl = fu;
                    rootHtml = r.body;
                }
            }
        }

        if (kbHtml.isEmpty()) {
            Discover.Found f = Discover.find(h, rootUrl, rootHtml, Jw.KB_TEXT, Jw.KB_PATH, true);
            dump(ctx, "discover-kb-tree.txt", f.tree);
            if (!f.url.isEmpty()) {
                kbUrl = f.url;
                kbHtml = f.html;
            }
        }

        if (kbHtml.isEmpty()) {
            return fail(ctx, res, "已登录但未自动定位到课表页，建议改用「内置浏览器一键导入」模式");
        }

        KbResult kb = KbParser.parse(kbHtml);

        // 尝试常见 EasyUI datagrid JSON 接口
        if (kb.courses.isEmpty()) {
            String term = extractTerm(kbHtml);
            if (term == null || term.isEmpty()) term = Prefs.term(ctx);
            if (term == null) term = "";
            int week = Prefs.currentWeek(ctx);
            if (!term.isEmpty()) Prefs.saveTerm(ctx, term);

            String ctrl = extractActionController(kbUrl);
            if (ctrl.isEmpty()) ctrl = "xsgrkbcx";
            String prefix = extractPathPrefix(kbUrl);

            StringBuilder dlog = new StringBuilder();
            dlog.append("base=").append(baseUrl).append(" term=").append(term).append(" week=").append(week).append('\n');

            String ep = baseUrl + prefix + ctrl + "!getDataList.action";
            String[] dl = term.isEmpty()
                    ? new String[]{ep}
                    : new String[]{
                    ep + "?xnxqdm=" + term + "&page=1&rows=500&sort=kxh&order=asc",
                    ep + "?xnxqdm=" + term + "&zc=" + week + "&page=1&rows=500&sort=kxh&order=asc",
                    ep + "?xnxqdm=" + term + "&zc=" + week,
                    ep};
            for (String u : dl) {
                Http.Resp r = h.getFollowing(u);
                if (r == null || r.body == null) continue;
                String b = r.body.trim();
                KbResult kr = KbParser.parseDataList(b);
                if (kr.courses.isEmpty()) kr = KbParser.parse(b);
                dlog.append("  ").append(u).append(" -> ").append(r.code)
                        .append(" courses=").append(kr.courses.size()).append('\n');
                if (!kr.courses.isEmpty() && kb.courses.isEmpty()) {
                    kb = kr;
                    kbHtml = b;
                    kbUrl = u;
                    break;
                }
            }
            dump(ctx, "kb-data.txt", dlog.toString());
        }

        if (kb.courses.isEmpty()) {
            return fail(ctx, res, "未从当前教务接口解析出课程，建议切换到「内置浏览器一键导入」模式");
        }
        if (manualKb.isEmpty() && !kbUrl.isEmpty()) Prefs.saveKbUrl(ctx, kbUrl);

        java.util.regex.Matcher tm = java.util.regex.Pattern.compile("xnxqdm=(\\d{4,8})")
                .matcher(kbHtml + " " + kbUrl);
        if (tm.find()) Prefs.saveTerm(ctx, tm.group(1));

        // ---------- 考试（可选） ----------
        String manualExam = usable(Prefs.manualExam(ctx));
        String savedExam = !manualExam.isEmpty() ? manualExam : usable(Prefs.examUrl(ctx));

        List<Exam> exams = new ArrayList<>();
        String examHtml = "";
        String examUrl = "";

        String eterm = Prefs.term(ctx);
        if (eterm == null) eterm = "";
        String eCtrl = extractActionController(menuExam);
        if (eCtrl.isEmpty()) eCtrl = "xsksap";
        String ePrefix = extractPathPrefix(menuExam);
        String eEp = baseUrl + ePrefix + eCtrl + "!getDataList.action";

        String[] edl = eterm.isEmpty()
                ? new String[]{eEp}
                : new String[]{eEp + "?xnxqdm=" + eterm + "&page=1&rows=300", eEp + "?xnxqdm=" + eterm, eEp};
        for (String u : edl) {
            Http.Resp r = h.getFollowing(u);
            if (r == null || r.body == null) continue;
            List<Exam> got = ExamParser.parseDataListJson(r.body.trim());
            if (!got.isEmpty()) {
                exams = got;
                examUrl = u;
                break;
            }
        }

        if (exams.isEmpty() && !menuExam.isEmpty()) {
            Http.Resp r = h.getFollowing(menuExam);
            if (r != null && r.body != null && !Jw.isLoginPage(r.body)) {
                examHtml = r.body;
                examUrl = r.finalUrl == null || r.finalUrl.isEmpty() ? menuExam : r.finalUrl;
            }
        }
        if (exams.isEmpty() && examHtml.isEmpty() && !savedExam.isEmpty()) {
            Http.Resp r = h.getFollowing(savedExam);
            if (r != null && r.body != null && !Jw.isLoginPage(r.body)) {
                examHtml = r.body;
                examUrl = r.finalUrl == null || r.finalUrl.isEmpty() ? savedExam : r.finalUrl;
            }
        }
        if (exams.isEmpty() && !examHtml.isEmpty()) {
            exams = ExamParser.parse(examHtml);
            if (manualExam.isEmpty() && !examUrl.isEmpty()) Prefs.saveExamUrl(ctx, examUrl);
        }

        Prefs.saveCourses(ctx, kb.courses, kb.nodeTime);
        Prefs.saveExams(ctx, exams);

        res.ok = true;
        res.courses = kb.courses.size();
        res.exams = exams.size();
        res.message = "已更新：" + res.courses + " 门课程"
                + (res.exams > 0 ? "、" + res.exams + " 场考试" : "");
        Prefs.saveStatus(ctx, System.currentTimeMillis(), res.message);
        Log.i("qingkebiao", res.message);
        return res;
    }

    private static String extractActionController(String url) {
        if (url == null || url.isEmpty()) return "";
        try {
            String path = new java.net.URI(url).getPath();
            if (path == null) return "";
            String file = path.substring(path.lastIndexOf('/') + 1);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([A-Za-z0-9_]+)!").matcher(file);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String extractPathPrefix(String url) {
        if (url == null || url.isEmpty()) return "/";
        try {
            String path = new java.net.URI(url).getPath();
            if (path == null) return "/";
            int slash = path.lastIndexOf('/');
            return slash >= 0 ? path.substring(0, slash + 1) : "/";
        } catch (Exception ignored) {
            return "/";
        }
    }

    private static String extractTerm(String html) {
        if (html == null || html.isEmpty()) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("xnxqdm=(\\d{4,8})").matcher(html);
        if (m.find()) return m.group(1);
        m = java.util.regex.Pattern.compile("value=[\"'](\\d{6})[\"'][^>]*selected",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) return m.group(1);
        m = java.util.regex.Pattern.compile("selected[^>]*value=[\"'](\\d{6})[\"']",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) return m.group(1);
        return "";
    }

    private static Result fail(Context ctx, Result res, String msg) {
        res.ok = false;
        res.message = msg;
        Prefs.saveStatus(ctx, System.currentTimeMillis(), msg);
        return res;
    }

    public static void dump(Context ctx, String name, String content) {
        try {
            File dir = new File(ctx.getFilesDir(), "diag");
            if (!dir.exists()) dir.mkdirs();
            FileOutputStream fo = new FileOutputStream(new File(dir, name));
            fo.write(content.getBytes(StandardCharsets.UTF_8));
            fo.close();
        } catch (Exception ignored) {
        }
    }

    public static String readDiag(Context ctx, String name, int maxLen) {
        try {
            File f = new File(new File(ctx.getFilesDir(), "diag"), name);
            if (!f.exists()) return "";
            int len = (int) Math.min(f.length(), maxLen);
            byte[] bs = new byte[len];
            java.io.FileInputStream fi = new java.io.FileInputStream(f);
            int n = fi.read(bs);
            fi.close();
            return new String(bs, 0, Math.max(0, n), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
