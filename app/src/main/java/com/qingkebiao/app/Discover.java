package com.qingkebiao.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 深度发现器：从教务系统落地页广度优先探测 frame/iframe 与内联链接 */
public class Discover {

    public static class Found {
        public String url = "";
        public String html = "";
        public String tree = "";
        public String allLinks = "";
    }

    private static final int MAX_PAGES = 40;
    private static final int MAX_DEPTH = 3;

    public static boolean selfLikeKb(String html) {
        return likeKb(html);
    }

    private static boolean likeKb(String html) {
        if (html == null) return false;
        if (html.contains("kbcontent") || html.contains("kbtable") || html.contains("TaskActivity")) return true;
        int d = 0;
        for (String s : new String[]{"星期一", "星期二", "星期三", "星期四", "星期五", "星期六",
                "周一", "周二", "周三", "周四", "周五", "周六"}) {
            if (html.contains(s)) d++;
        }
        if (d >= 3 && html.contains("<table")) return true;
        return html.contains("<table") && (html.contains("节次") || html.contains("上课时间"));
    }

    private static boolean likeExam(String html) {
        if (html == null || !html.contains("<table")) return false;
        return html.contains("考试时间") || html.contains("考场") || html.contains("考试地点");
    }

    private static int scoreLink(String text, String href, String[] textKeys, String[] pathKeys) {
        int score = 0;
        String t = text == null ? "" : text;
        String h = href == null ? "" : href.toLowerCase();
        for (String k : textKeys) if (t.contains(k)) score += 30;
        for (String k : pathKeys) if (h.contains(k)) score += 12;
        if (h.contains("/new/")) score += 4;
        if (h.contains("logout") || h.contains("exit") || h.contains("login")) score -= 40;
        if (h.contains("javascript") || h.endsWith("#")) score -= 50;
        return score;
    }

    public static Found find(Http h, String landingUrl, String landingHtml,
                             String[] textKeys, String[] pathKeys, boolean forKb) {
        Found out = new Found();
        StringBuilder tree = new StringBuilder();
        Set<String> allLinks = new LinkedHashSet<>();

        if (landingHtml == null || landingHtml.isEmpty()) return out;

        List<String> queue = new ArrayList<>();
        List<Integer> depths = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();

        queue.add(landingUrl);
        depths.add(0);
        visited.add(landingUrl);

        if (forKb ? likeKb(landingHtml) : likeExam(landingHtml)) {
            out.url = landingUrl;
            out.html = landingHtml;
        }

        List<Link> seeds = harvest(landingUrl, landingHtml);
        List<Link> ranked = new ArrayList<>(seeds);
        final String[] tk = textKeys;
        final String[] pk = pathKeys;
        java.util.Collections.sort(ranked, new java.util.Comparator<Link>() {
            @Override
            public int compare(Link a, Link b) {
                return Integer.compare(scoreLink(b.text, b.href, tk, pk),
                        scoreLink(a.text, a.href, tk, pk));
            }
        });

        for (Link l : ranked) {
            if (visited.size() + queue.size() > MAX_PAGES * 2) break;
            if (visited.contains(l.href)) continue;
            if (scoreLink(l.text, l.href, textKeys, pathKeys) <= 0) continue;
            queue.add(l.href);
            depths.add(1);
            visited.add(l.href);
        }

        int head = 0;
        int fetched = 0;
        while (head < queue.size() && fetched < MAX_PAGES) {
            String url = queue.get(head);
            int depth = depths.get(head);
            head++;
            fetched++;

            Http.Resp r;
            if (head == 1 && url.equals(landingUrl)) {
                r = new Http.Resp();
                r.code = 200;
                r.body = landingHtml;
            } else {
                r = h.getFollowing(url);
            }
            if (r == null || r.body == null) {
                tree.append("  [").append(depth).append("] ").append(url).append("  -> 无响应\n");
                continue;
            }
            String body = r.body;
            String finalUrl = r.finalUrl == null || r.finalUrl.isEmpty() ? url : r.finalUrl;
            boolean isLogin = Jw.isLoginPage(body);
            boolean hit = forKb ? likeKb(body) : likeExam(body);
            tree.append("  [").append(depth).append("] ")
                    .append(hit ? "★命中 " : "      ")
                    .append(body.length()).append("B ")
                    .append(isLogin ? "LOGIN " : "")
                    .append(finalUrl).append('\n');

            if (hit && out.url.isEmpty()) {
                out.url = finalUrl;
                out.html = body;
            }

            if (depth >= MAX_DEPTH) continue;

            List<Link> links = harvest(finalUrl, body);
            List<Link> more = new ArrayList<>();
            for (Link l : links) {
                allLinks.add(prettyLink(l));
                if (visited.contains(l.href)) continue;
                int sc = scoreLink(l.text, l.href, textKeys, pathKeys);
                boolean isFrame = l.text.isEmpty() && !l.href.isEmpty();
                if (sc > 0 || isFrame) more.add(l);
            }
            java.util.Collections.sort(more, new java.util.Comparator<Link>() {
                @Override
                public int compare(Link a, Link b) {
                    return Integer.compare(scoreLink(b.text, b.href, tk, pk),
                            scoreLink(a.text, a.href, tk, pk));
                }
            });
            int added = 0;
            for (Link l : more) {
                if (visited.size() > MAX_PAGES * 2) break;
                if (visited.contains(l.href)) continue;
                visited.add(l.href);
                queue.add(l.href);
                depths.add(depth + 1);
                if (++added >= 12) break;
            }
        }

        out.tree = "pagesFetched=" + fetched + "\n" + tree;
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String s : allLinks) {
            sb.append(s).append('\n');
            if (++n > 200) break;
        }
        out.allLinks = sb.toString();
        return out;
    }

    private static String prettyLink(Link l) {
        String t = l.text == null ? "" : l.text;
        if (t.length() > 30) t = t.substring(0, 30);
        return String.format("  %-30s %s", t, l.href);
    }

    public static List<Link> harvest(String pageUrl, String html) {
        List<Link> out = new ArrayList<>();
        if (html == null) return out;

        Matcher a = Pattern.compile("(?is)<a\\b[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>").matcher(html);
        while (a.find()) {
            String href = a.group(1).trim();
            if (href.isEmpty() || href.startsWith("#") || href.toLowerCase().startsWith("javascript")) continue;
            out.add(new Link(Http.absolutize(pageUrl, href), Html.text(a.group(2)).replaceAll("\\s+", "")));
        }

        Matcher f = Pattern.compile("(?is)<(?:iframe|frame)\\b[^>]*src\\s*=\\s*[\"']([^\"']+)[\"']").matcher(html);
        while (f.find()) {
            out.add(new Link(Http.absolutize(pageUrl, f.group(1).trim()), ""));
        }

        Matcher js = Pattern.compile(
                "(?:url|href|action|src)\\s*[:=]\\s*[\"'](/?[A-Za-z0-9_\\-\\./!]+(?:\\.action|\\.do|\\.jsp|\\.html)[^\"']*)[\"']")
                .matcher(html);
        while (js.find()) {
            String u = js.group(1).trim();
            if (u.startsWith("http")) out.add(new Link(u, ""));
            else out.add(new Link(Http.absolutize(pageUrl, u), ""));
        }
        return out;
    }

    public static String inlineScripts(String html, int maxLen) {
        StringBuilder sb = new StringBuilder();
        if (html == null) return sb.toString();
        Matcher m = Pattern.compile("(?is)<script\\b([^>]*)>(.*?)</script\\s*>").matcher(html);
        int n = 0;
        while (m.find()) {
            String attrs = m.group(1);
            if (attrs != null && attrs.toLowerCase().contains("src=")) continue;
            String body = m.group(2);
            if (body == null) continue;
            body = body.trim();
            if (body.isEmpty()) continue;
            sb.append("\n/* --- inline script ").append(++n).append(" --- */\n").append(body).append('\n');
            if (sb.length() > maxLen) break;
        }
        if (sb.length() == 0) sb.append("(没有内联脚本)\n");
        return sb.length() > maxLen ? sb.substring(0, maxLen) : sb.toString();
    }

    public static List<String> actionUrlList(String pageUrl, String html) {
        Set<String> set = new LinkedHashSet<>();
        if (html == null) return new ArrayList<>(set);
        for (Link l : harvest(pageUrl, html)) {
            if (l.href.matches("(?i).*(\\.action|\\.do|\\.jsp)(\\?.*)?$")) set.add(l.href);
        }
        Matcher m = Pattern.compile("([A-Za-z0-9_\\-\\./]+![A-Za-z0-9_\\-]+\\.action)").matcher(html);
        while (m.find()) set.add(Http.absolutize(pageUrl, m.group(1)));
        return new ArrayList<>(set);
    }

    public static String afterBody(String html, int max) {
        if (html == null) return "";
        String s = html;
        int i = s.toLowerCase().lastIndexOf("<body");
        if (i >= 0) {
            s = s.substring(i);
        } else {
            int t = s.indexOf("<table");
            int d = s.indexOf("<div");
            int p = t < 0 ? d : (d < 0 ? t : Math.min(t, d));
            if (p > 0) s = s.substring(p);
        }
        s = s.replaceAll("(?is)<script\\b[^>]*src=\"[^\"]*\"[^>]*>\\s*</script>", "");
        s = s.replaceAll("(?is)<link\\b[^>]*>", "");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.length() > max ? s.substring(0, max) : s;
    }

    public static class Link {
        public String href;
        public String text;

        Link(String h, String t) {
            href = h;
            text = t;
        }
    }
}
