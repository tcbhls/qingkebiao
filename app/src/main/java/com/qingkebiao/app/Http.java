package com.qingkebiao.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 极简 Cookie 感知 HTTP 客户端（手动处理 302 跳转与 CAS 票据） */
public class Http {

    public static class Resp {
        public int code;
        public String body = "";
        public String location = "";
        public String contentType = "";
        public String finalUrl = "";
        public Map<String, String> headers = new LinkedHashMap<>();

        public boolean isRedirect() {
            return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
        }
    }

    public static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final Map<String, Map<String, String>> jar = new LinkedHashMap<>();

    public void clearCookies() {
        jar.clear();
    }

    public String cookieHeader(String url) {
        String host = hostOf(url);
        Map<String, String> m = jar.get(host);
        if (m == null || m.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    private static String hostOf(String url) {
        try {
            java.net.URI u = new java.net.URI(url);
            String h = u.getHost();
            return h == null ? "" : h.toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    private void storeCookies(String url, Map<String, List<String>> respHeaders) {
        List<String> set = respHeaders.get("Set-Cookie");
        if (set == null) set = respHeaders.get("set-cookie");
        if (set == null) return;
        String host = hostOf(url);
        Map<String, String> m = jar.get(host);
        if (m == null) {
            m = new LinkedHashMap<>();
            jar.put(host, m);
        }
        for (String raw : set) {
            String pair = raw.split(";", 2)[0];
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String name = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
                m.remove(name);
            } else {
                m.put(name, value);
            }
        }
    }

    public Resp get(String url) {
        return request("GET", url, null, null);
    }

    public Resp post(String url, String formBody) {
        return request("POST", url, formBody, "application/x-www-form-urlencoded; charset=UTF-8");
    }

    public Resp request(String method, String url, String body, String contentType) {
        java.net.HttpURLConnection c = null;
        Resp r = new Resp();
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setRequestMethod(method);
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            String cookie = cookieHeader(url);
            if (cookie != null) c.setRequestProperty("Cookie", cookie);
            if (body != null) {
                c.setDoOutput(true);
                if (contentType != null) c.setRequestProperty("Content-Type", contentType);
                byte[] bs = body.getBytes("UTF-8");
                c.setFixedLengthStreamingMode(bs.length);
                java.io.OutputStream os = c.getOutputStream();
                os.write(bs);
                os.flush();
                os.close();
            }
            r.code = c.getResponseCode();
            storeCookies(url, c.getHeaderFields());
            String loc = c.getHeaderField("Location");
            if (loc != null) r.location = loc;
            String ct = c.getHeaderField("Content-Type");
            if (ct != null) r.contentType = ct;

            java.io.InputStream is;
            try {
                is = c.getInputStream();
            } catch (Exception e) {
                is = c.getErrorStream();
            }
            if (is != null) {
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                is.close();
                String charset = "UTF-8";
                if (ct != null && ct.toLowerCase().contains("gbk")) charset = "GBK";
                r.body = new String(bo.toByteArray(), charset);
            }
        } catch (Exception e) {
            r.code = -1;
            r.body = "网络错误: " + e.getClass().getSimpleName() + " " + e.getMessage();
        } finally {
            if (c != null) c.disconnect();
        }
        return r;
    }

    /** 跟随跳转，最多 10 跳 */
    public Resp getFollowing(String url) {
        String cur = url;
        Resp last = null;
        for (int i = 0; i < 10; i++) {
            last = get(cur);
            if (!last.isRedirect() || last.location == null || last.location.isEmpty()) {
                last.finalUrl = cur;
                return last;
            }
            cur = absolutize(cur, last.location);
        }
        if (last != null && last.finalUrl.isEmpty()) last.finalUrl = cur;
        return last;
    }

    public static String absolutize(String base, String loc) {
        if (loc == null) return base;
        if (loc.startsWith("http://") || loc.startsWith("https://")) return loc;
        try {
            java.net.URI b = new java.net.URI(base);
            java.net.URI r = b.resolve(loc);
            return r.toString();
        } catch (Exception e) {
            return loc;
        }
    }

    public static String originOf(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        try {
            java.net.URI u = new java.net.URI(url.trim());
            String scheme = u.getScheme();
            String host = u.getHost();
            if (scheme == null || host == null) return "";
            int port = u.getPort();
            return scheme + "://" + host + (port > 0 ? ":" + port : "");
        } catch (Exception e) {
            return "";
        }
    }

    public static List<String> findAll(String src, String regex, int group) {
        List<String> out = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(src);
        while (m.find()) out.add(m.group(group));
        return out;
    }

    public static String find1(String src, String regex, int group) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(src);
        if (m.find()) return m.group(group);
        return null;
    }
}
