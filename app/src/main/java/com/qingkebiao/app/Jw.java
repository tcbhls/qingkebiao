package com.qingkebiao.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 通用教务端访问与链接发现辅助（无硬编码域名） */
public class Jw {

    public static String guessTerm() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        int y = c.get(java.util.Calendar.YEAR);
        int m = c.get(java.util.Calendar.MONTH) + 1;
        if (m >= 8) return y + "-" + (y + 1) + "-1";
        if (m == 1) return (y - 1) + "-" + y + "-1";
        return (y - 1) + "-" + y + "-2";
    }

    public static class Link {
        public String href;
        public String text;

        Link(String h, String t) {
            href = h;
            text = t;
        }
    }

    /** 判定响应是否回落到了登录页 */
    public static boolean isLoginPage(String body) {
        if (body == null) return true;
        if (body.contains("name=\"passwordText\"") || body.contains("name='passwordText'")) return true;
        if (body.contains("id=\"f_account\"")) return true;
        if (body.contains("使用统一认证中心登录")) return true;
        if (body.contains("top.location=\"/\"") || body.contains("top.location = \"/\"")) return true;
        return false;
    }

    public static List<Link> harvest(String pageUrl, String html) {
        List<Link> out = new ArrayList<>();
        if (html == null) return out;

        Matcher a = Pattern.compile("(?is)<a\\b[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>").matcher(html);
        while (a.find()) {
            String href = a.group(1).trim();
            String text = Html.text(a.group(2)).replaceAll("\\s+", "");
            if (href.startsWith("javascript") || href.startsWith("#") || href.isEmpty()) continue;
            out.add(new Link(Http.absolutize(pageUrl, href), text));
        }
        Matcher f = Pattern.compile("(?is)<(?:iframe|frame)\\b[^>]*src\\s*=\\s*[\"']([^\"']+)[\"']").matcher(html);
        while (f.find()) {
            out.add(new Link(Http.absolutize(pageUrl, f.group(1).trim()), ""));
        }
        Matcher js = Pattern.compile("(?:location(?:\\.href)?\\s*=|url\\s*[:=]\\s*)[\"'](/[^\"']{3,120})[\"']").matcher(html);
        while (js.find()) {
            out.add(new Link(Http.absolutize(pageUrl, js.group(1).trim()), ""));
        }
        return out;
    }

    public static String pick(List<Link> links, String[] textKeys, String[] pathKeys) {
        String best = null;
        int bestScore = -1;
        Set<String> seen = new LinkedHashSet<>();
        for (Link l : links) {
            if (!seen.add(l.href)) continue;
            int score = 0;
            String t = l.text == null ? "" : l.text;
            for (String k : textKeys) if (t.contains(k)) score += 20;
            for (String k : pathKeys) if (l.href.toLowerCase().contains(k)) score += 8;
            if (l.href.contains("/new/")) score += 3;
            if (score > bestScore && score > 0) {
                bestScore = score;
                best = l.href;
            }
        }
        return best;
    }

    public static final String[] KB_TEXT = {"课表", "课程表", "我的课表", "周课表", "学生课表"};
    public static final String[] KB_PATH = {"kbcx", "xskb", "kcb", "kbb", "kecheng", "coursetable", "schedule"};
    public static final String[] EXAM_TEXT = {"考试安排", "考试信息", "我的考试", "考试查询", "考表"};
    public static final String[] EXAM_PATH = {"xsks", "kscx", "exam", "ksap", "kaoshi"};
}
