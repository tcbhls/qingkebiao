package com.qingkebiao.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 迷你 JSON 解析器（零第三方依赖，使全部解析逻辑可在标准 JVM 上直接单元测试）。
 * 产出结构：Map<String,Object> / List<Object> / String / Double / Boolean / null
 */
public final class Json {

    public static Object parse(String s) {
        if (s == null) return null;
        P p = new P(s);
        p.ws();
        return p.value();
    }

    public static Map<String, Object> asMap(Object o) {
        if (o instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            return m;
        }
        return null;
    }

    public static List<Object> asList(Object o) {
        if (o instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> l = (List<Object>) o;
            return l;
        }
        return null;
    }

    /** 取第一个非空字符串字段（优先精确匹配，其次大小写不敏感匹配以兼容 EMAP 大写字段） */
    public static String str(Map<String, Object> m, String... keys) {
        if (m == null || keys == null) return null;
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) {
                String s = formatScalar(v);
                if (s != null) return s;
            }
        }
        for (String k : keys) {
            for (Map.Entry<String, Object> e : m.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(k) && e.getValue() != null) {
                    String s = formatScalar(e.getValue());
                    if (s != null) return s;
                }
            }
        }
        return null;
    }

    private static String formatScalar(Object v) {
        if (v instanceof Double) {
            double d = (Double) v;
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        return s;
    }

    private static final class P {
        private final String s;
        private int i;

        P(String s) {
            this.s = s;
        }

        void ws() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\uFEFF') i++;
                else break;
            }
        }

        Object value() {
            ws();
            if (i >= s.length()) return null;
            char c = s.charAt(i);
            if (c == '{') return obj();
            if (c == '[') return arr();
            if (c == '"' || c == '\'') return string();
            if (c == 't' || c == 'f') {
                if (s.startsWith("true", i)) {
                    i += 4;
                    return Boolean.TRUE;
                }
                if (s.startsWith("false", i)) {
                    i += 5;
                    return Boolean.FALSE;
                }
            }
            if (s.startsWith("null", i)) {
                i += 4;
                return null;
            }
            return number();
        }

        Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            ws();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return m;
            }
            while (i < s.length()) {
                ws();
                String k;
                if (i < s.length() && (s.charAt(i) == '"' || s.charAt(i) == '\'')) k = string();
                else {
                    int st = i;
                    while (i < s.length() && s.charAt(i) != ':' && s.charAt(i) != '}') i++;
                    k = s.substring(st, i).trim();
                }
                ws();
                if (i < s.length() && s.charAt(i) == ':') i++;
                Object v = value();
                m.put(k, v);
                ws();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                if (i < s.length() && s.charAt(i) == '}') {
                    i++;
                    break;
                }
                break;
            }
            return m;
        }

        List<Object> arr() {
            List<Object> l = new ArrayList<>();
            i++; // [
            ws();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return l;
            }
            while (i < s.length()) {
                Object v = value();
                l.add(v);
                ws();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                if (i < s.length() && s.charAt(i) == ']') {
                    i++;
                    break;
                }
                break;
            }
            return l;
        }

        String string() {
            char q = s.charAt(i);
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '\\') {
                    i++;
                    if (i >= s.length()) break;
                    char e = s.charAt(i);
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            if (i + 4 < s.length()) {
                                try {
                                    sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                                    i += 4;
                                } catch (Exception ignored) {
                                }
                            }
                            break;
                        default: sb.append(e);
                    }
                    i++;
                    continue;
                }
                if (c == q) {
                    i++;
                    break;
                }
                sb.append(c);
                i++;
            }
            return sb.toString();
        }

        Double number() {
            int st = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') i++;
                else break;
            }
            if (st == i) {
                i++;
                return null;
            }
            try {
                return Double.valueOf(s.substring(st, i));
            } catch (Exception e) {
                return null;
            }
        }
    }

    private Json() {
    }
}
