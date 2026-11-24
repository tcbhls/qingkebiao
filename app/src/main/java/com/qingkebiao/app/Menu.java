package com.qingkebiao.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** EasyUI 教务菜单 JSON 自动发现（按传入 baseUrl 拼接，无硬编码域名） */
public class Menu {

    public static final String MENU_PATH = "/default!getMenu.action";
    public static String lastRaw = "";

    public static class Item {
        public String id = "";
        public String name = "";
        public String parent = "";
        public String url = "";
        public String pic = "";
        public int level = 0;

        @Override
        public String toString() {
            return "L" + level + " " + name + " -> " + url;
        }
    }

    public static List<Item> fetch(Http h, String baseUrl) {
        List<Item> out = new ArrayList<>();
        if (baseUrl == null || baseUrl.isEmpty()) return out;
        String[] paths = {MENU_PATH, "/new/default!getMenu.action"};
        String body = null;
        for (String p : paths) {
            Http.Resp r = h.get(baseUrl + p);
            if (r == null || r.body == null) continue;
            String b = r.body.trim();
            if (b.startsWith("\uFEFF")) b = b.substring(1);
            if (b.contains("menuname") && b.contains("[")) {
                body = b;
                break;
            }
            if (body == null) body = b;
        }
        if (body == null) return out;
        lastRaw = body;
        parseInto(out, body);
        return out;
    }

    private static void parseInto(List<Item> out, String body) {
        String b = body;
        if (!b.startsWith("[")) {
            int s = b.indexOf('[');
            int e = b.lastIndexOf(']');
            if (s < 0 || e <= s) return;
            b = b.substring(s, e + 1);
        }
        try {
            JSONArray arr = new JSONArray(b);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Item it = new Item();
                it.id = o.optString("menuid", "");
                it.name = o.optString("menuname", "").trim();
                it.parent = o.optString("parentid", "");
                it.url = o.optString("menuurl", "").trim();
                it.pic = o.optString("menupic", "");
                String lv = o.optString("menulevel", "0");
                try {
                    it.level = Integer.parseInt(lv.replaceAll("[^0-9]", ""));
                } catch (Exception ignored) {
                }
                out.add(it);
            }
        } catch (Exception ignored) {
        }
    }

    public static String dump(List<Item> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("menuCount=").append(items.size()).append('\n');
        for (Item it : items) {
            sb.append(String.format("  L%-2d %-24s %s\n", it.level, it.name, it.url));
        }
        return sb.toString();
    }

    public static String absolute(String baseUrl, String url) {
        if (url == null) return "";
        String u = url.trim();
        if (u.isEmpty()) return "";
        if (u.startsWith("http://") || u.startsWith("https://")) return u;
        if (u.startsWith("/")) return baseUrl + u;
        return baseUrl + "/" + u;
    }

    private static boolean has(String s, String... keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }

    public static String pickKb(String baseUrl, List<Item> items) {
        String best = null;
        int bestScore = -1;
        for (Item it : items) {
            if (it.url.isEmpty()) continue;
            String n = it.name;
            String u = it.url.toLowerCase();
            int score = 0;
            if (n.contains("课表")) score += 50;
            if (n.contains("课程表")) score += 30;
            if (n.contains("我的课表")) score += 20;
            if (n.contains("学生课表")) score += 20;
            if (n.contains("周课表")) score += 15;
            if (n.contains("课表查询")) score += 15;
            if (has(n, "成绩", "考试", "选课", "评价", "重修", "辅修", "申请")) score -= 60;
            if (u.contains("kbcx")) score += 25;
            if (u.contains("xskb")) score += 25;
            if (u.contains("kcb")) score += 15;
            if (u.contains("kbtable")) score += 10;
            if (score > bestScore && score > 0) {
                bestScore = score;
                best = absolute(baseUrl, it.url);
            }
        }
        return best == null ? "" : best;
    }

    public static String pickExam(String baseUrl, List<Item> items) {
        String best = null;
        int bestScore = -1;
        for (Item it : items) {
            if (it.url.isEmpty()) continue;
            String n = it.name;
            String u = it.url.toLowerCase();
            int score = 0;
            if (n.contains("考试安排")) score += 50;
            if (n.contains("考试信息")) score += 40;
            if (n.contains("考试查询")) score += 35;
            if (n.contains("考场")) score += 30;
            if (n.contains("我的考试")) score += 30;
            if (n.contains("考表")) score += 25;
            if (n.contains("考试")) score += 10;
            if (n.contains("成绩")) score -= 60;
            if (n.contains("报名")) score -= 20;
            if (n.contains("等级")) score -= 30;
            if (n.contains("补考")) score -= 10;
            if (u.contains("xsks")) score += 25;
            if (u.contains("ksap")) score += 25;
            if (u.contains("kscx")) score += 20;
            if (u.contains("kaoshi")) score += 15;
            if (score > bestScore && score > 0) {
                bestScore = score;
                best = absolute(baseUrl, it.url);
            }
        }
        return best == null ? "" : best;
    }
}
