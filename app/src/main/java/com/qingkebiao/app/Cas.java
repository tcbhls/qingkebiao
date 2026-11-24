package com.qingkebiao.app;

import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 通用金智统一身份认证（authserver / CAS）自动登录。
 * 不硬编码任何学校地址：由用户配置教务网址（及可选 CAS 登录页 URL，留空则自动探测 302 跳转）。
 */
public class Cas {

    private static final String AES_CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678";
    private static final SecureRandom RND = new SecureRandom();

    public static class Result {
        public boolean ok;
        public String message = "";
        public String ticket = "";
        public String finalUrl = "";
        public String landingUrl = "";
        public String landingHtml = "";
    }

    private static String randomString(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(AES_CHARS.charAt(RND.nextInt(AES_CHARS.length())));
        }
        return sb.toString();
    }

    /** 标准金智 authserver encryptAES(password, salt) */
    public static String encryptPassword(String password, String salt) throws Exception {
        if (salt == null || salt.isEmpty()) return password;
        String plain = randomString(64) + password;
        byte[] key = salt.getBytes("UTF-8");
        byte[] iv = randomString(16).getBytes("UTF-8");
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] out = c.doFinal(plain.getBytes("UTF-8"));
        return android.util.Base64.encodeToString(out, android.util.Base64.NO_WRAP);
    }

    /**
     * 执行通用金智 CAS 登录。
     * @param jwUrl    教务系统入口 URL（如 https://jw.example.edu.cn）
     * @param casUrl   可选 CAS 登录页 URL；若留空则先访问 jwUrl 并跟随跳转自动探测
     */
    public static Result login(Http h, String jwUrl, String casUrl, String user, String pass) {
        Result r = new Result();
        try {
            h.clearCookies();
            String loginPageUrl = (casUrl == null ? "" : casUrl.trim());
            Http.Resp page;

            if (!loginPageUrl.isEmpty()) {
                page = h.getFollowing(loginPageUrl);
                if (page != null && page.finalUrl != null && !page.finalUrl.isEmpty()) {
                    loginPageUrl = page.finalUrl;
                }
            } else {
                // 自动跟随教务入口 302 跳转寻找统一认证页
                if (jwUrl == null || jwUrl.trim().isEmpty()) {
                    r.message = "请先填写教务系统网址";
                    return r;
                }
                page = h.getFollowing(jwUrl.trim());
                if (page == null) {
                    r.message = "无法连接教务系统";
                    return r;
                }
                loginPageUrl = (page.finalUrl != null && !page.finalUrl.isEmpty()) ? page.finalUrl : jwUrl.trim();
                // 有些教务首页有独立「统一认证登录」链接（如 /new/ssoLogin）
                if (!hasCasForm(page.body)) {
                    String ssoLink = firstGroup(page.body, "href=[\"']([^\"']*(?:ssoLogin|authserver|cas/login)[^\"']*)[\"']");
                    if (ssoLink != null && !ssoLink.isEmpty()) {
                        Http.Resp p2 = h.getFollowing(Http.absolutize(loginPageUrl, ssoLink));
                        if (p2 != null && p2.code == 200) {
                            page = p2;
                            loginPageUrl = (p2.finalUrl != null && !p2.finalUrl.isEmpty()) ? p2.finalUrl : loginPageUrl;
                        }
                    }
                }
            }

            if (page == null || page.code != 200) {
                r.message = "打不开统一认证登录页（HTTP " + (page == null ? -1 : page.code) + "）";
                return r;
            }
            String execution = firstGroup(page.body, "id=\"execution\"[^>]*value=\"([^\"]*)\"");
            if (execution == null) execution = firstGroup(page.body, "name=\"execution\"[^>]*value=\"([^\"]*)\"");
            String salt = firstGroup(page.body, "id=\"pwdEncryptSalt\"[^>]*value=\"([^\"]+)\"");
            if (execution == null) {
                r.message = "未识别到金智 CAS 登录表单，建议使用「内置浏览器一键导入」模式";
                return r;
            }

            String encPass = (salt != null && !salt.isEmpty()) ? encryptPassword(pass, salt) : pass;
            StringBuilder body = new StringBuilder();
            body.append("username=").append(enc(user));
            body.append("&password=").append(enc(encPass));
            body.append("&captcha=");
            body.append("&_eventId=submit");
            body.append("&cllt=userNameLogin");
            body.append("&dllt=generalLogin");
            body.append("&lt=");
            body.append("&execution=").append(enc(execution));
            body.append("&rmShown=1");

            Http.Resp post = h.post(loginPageUrl, body.toString());
            if (!post.isRedirect() || post.location == null || post.location.isEmpty()) {
                String msg = firstGroup(post.body, "id=\"showErrorTip\"[^>]*>\\s*<span[^>]*>\\s*([^<]{2,160})<");
                if (msg == null) msg = firstGroup(post.body, "id=\"msg\"[^>]*>\\s*([^<]{2,160})<");
                if (msg == null) msg = firstGroup(post.body, "class=\"auth_error\"[^>]*>\\s*([^<]{2,160})<");
                r.message = (msg == null ? "登录失败（HTTP " + post.code + "）" : msg.trim());
                if (post.body.contains("captchaSwitch")) {
                    String need = firstGroup(post.body, "needCaptcha\\s*=\\s*\"([^\"]*)\"");
                    if (need != null && !need.isEmpty()) {
                        r.message += "；当前要求验证码，请改用「内置浏览器导入」";
                    }
                }
                return r;
            }

            String ticketUrl = Http.absolutize(loginPageUrl, post.location);
            Matcher tm = Pattern.compile("ticket=([^&]+)").matcher(ticketUrl);
            if (tm.find()) r.ticket = tm.group(1);

            Http.Resp land = h.getFollowing(ticketUrl);
            if (land != null) {
                r.landingUrl = land.finalUrl == null || land.finalUrl.isEmpty() ? ticketUrl : land.finalUrl;
                r.landingHtml = land.body == null ? "" : land.body;
                String js = firstGroup(r.landingHtml,
                        "(?:top|window)\\.location(?:\\.href)?\\s*=\\s*[\"']([^\"']+)[\"']");
                if (js != null && !js.equals("/")) {
                    Http.Resp land2 = h.getFollowing(Http.absolutize(r.landingUrl, js));
                    if (land2 != null && land2.body != null && !land2.body.isEmpty()) {
                        r.landingUrl = land2.finalUrl == null || land2.finalUrl.isEmpty()
                                ? r.landingUrl : land2.finalUrl;
                        r.landingHtml = land2.body;
                    }
                }
            }
            r.finalUrl = r.landingUrl;

            if (r.ticket.isEmpty() && !post.isRedirect()) {
                r.message = "未拿到 CAS 票据";
                return r;
            }
            r.ok = true;
            r.message = "登录成功";
            return r;
        } catch (Exception e) {
            r.message = "登录异常：" + e.getClass().getSimpleName() + " " + e.getMessage();
            return r;
        }
    }

    private static boolean hasCasForm(String body) {
        if (body == null) return false;
        return body.contains("id=\"pwdEncryptSalt\"")
                || body.contains("id='pwdEncryptSalt'")
                || body.contains("name=\"execution\"")
                || body.contains("name='execution'")
                || body.contains("id=\"execution\"")
                || body.contains("id='execution'");
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static String firstGroup(String src, String regex) {
        if (src == null) return null;
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(src);
        if (m.find()) return m.group(1).trim();
        return null;
    }
}
