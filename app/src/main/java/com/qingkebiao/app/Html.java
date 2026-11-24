package com.qingkebiao.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 手写 HTML 工具：切表格、按 rowspan/colspan 还原成矩形网格、切分多课块与抽纯文本 */
public class Html {

    private static final Pattern TABLE = Pattern.compile("<table\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROW = Pattern.compile("<tr\\b[^>]*>(.*?)</tr>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CELL =
            Pattern.compile("<t([dh])\\b([^>]*)>(.*?)</t\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 取出所有 <table>…</table> 的整段 HTML（按嵌套深度配对） */
    public static List<String> tables(String html) {
        List<String> out = new ArrayList<>();
        if (html == null) return out;
        Matcher m = TABLE.matcher(html);
        while (m.find()) {
            int start = m.start();
            int depth = 0;
            int end = -1;
            int p = start;
            boolean opened = false;
            while (p < html.length()) {
                int no = indexOfTag(html, "table", p);
                int nc = indexOfEnd(html, "table", p);
                if (no < 0 && nc < 0) break;
                if (no >= 0 && (nc < 0 || no < nc)) {
                    depth++;
                    p = no + 6;
                    opened = true;
                } else {
                    depth--;
                    p = nc + 8;
                    if (depth == 0) {
                        end = nc + 8;
                        break;
                    }
                }
            }
            if (opened && end > start) out.add(html.substring(start, end));
        }
        return out;
    }

    private static int indexOfTag(String s, String tag, int from) {
        Matcher m = Pattern.compile("<" + tag + "\\b", Pattern.CASE_INSENSITIVE).matcher(s);
        return m.find(from) ? m.start() : -1;
    }

    private static int indexOfEnd(String s, String tag, int from) {
        Matcher m = Pattern.compile("</" + tag + "\\s*>", Pattern.CASE_INSENSITIVE).matcher(s);
        return m.find(from) ? m.start() : -1;
    }

    /** 把表格还原成矩形网格；rowspan/colspan 覆盖的位置填入同一个单元格 HTML */
    public static String[][] grid(String tableHtml) {
        List<List<String[]>> parsed = new ArrayList<>();
        Matcher r2 = ROW.matcher(tableHtml);
        while (r2.find()) {
            String inner = r2.group(1);
            List<String[]> cells = new ArrayList<>();
            Matcher cm = CELL.matcher(inner);
            while (cm.find()) {
                cells.add(new String[]{cm.group(2), cm.group(3)});
            }
            if (!cells.isEmpty()) parsed.add(cells);
        }
        if (parsed.isEmpty()) return new String[0][0];

        int nRows = parsed.size();
        int nCols = 9;
        for (List<String[]> row : parsed) {
            int sum = 0;
            for (String[] cell : row) sum += Math.max(1, intAttr(cell[0], "colspan"));
            if (sum > nCols) nCols = sum;
        }
        nCols = Math.min(nCols + 4, 40);

        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < nRows; i++) {
            List<String> r = new ArrayList<>();
            for (int j = 0; j < nCols; j++) r.add("");
            out.add(r);
        }
        boolean[][] filled = new boolean[nRows][nCols];
        int maxUsedCol = 0;

        for (int i = 0; i < nRows; i++) {
            int c = 0;
            for (String[] cell : parsed.get(i)) {
                while (c < nCols && filled[i][c]) c++;
                if (c >= nCols) break;
                int rs = Math.max(1, intAttr(cell[0], "rowspan"));
                int cs = Math.max(1, intAttr(cell[0], "colspan"));
                for (int di = 0; di < rs && i + di < nRows; di++) {
                    for (int dj = 0; dj < cs && c + dj < nCols; dj++) {
                        out.get(i + di).set(c + dj, cell[1]);
                        filled[i + di][c + dj] = true;
                        if (c + dj + 1 > maxUsedCol) maxUsedCol = c + dj + 1;
                    }
                }
                c += cs;
            }
        }

        int finalCols = Math.max(1, maxUsedCol);
        String[][] res = new String[nRows][finalCols];
        for (int i = 0; i < nRows; i++) {
            for (int j = 0; j < finalCols; j++) {
                res[i][j] = out.get(i).get(j);
            }
        }
        return res;
    }

    private static int intAttr(String attrs, String name) {
        Matcher m = Pattern.compile(name + "\\s*=\\s*[\"']?(\\d+)", Pattern.CASE_INSENSITIVE).matcher(attrs);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignored) {
            }
        }
        return 1;
    }

    /** 单元格 HTML -> 按行切好的纯文本 */
    public static List<String> lines(String cellHtml) {
        List<String> out = new ArrayList<>();
        if (cellHtml == null) return out;
        String s = cellHtml.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</div\\s*>", "\n")
                .replaceAll("(?i)<div\\b[^>]*>", "\n")
                .replaceAll("(?i)</p\\s*>", "\n")
                .replaceAll("(?i)<hr\\s*/?>", "\n")
                .replaceAll("(?i)</tr\\s*>", "\n")
                .replaceAll("(?i)</td\\s*>", "\n");
        s = s.replaceAll("<[^>]+>", "");
        s = decode(s);
        for (String line : s.split("\n")) {
            String t = line.replace('\u00A0', ' ').trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    public static String text(String cellHtml) {
        StringBuilder sb = new StringBuilder();
        for (String l : lines(cellHtml)) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(l);
        }
        return sb.toString().trim();
    }

    public static String decode(String s) {
        if (s == null) return "";
        String r = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&apos;", "'").replace("&mdash;", "—").replace("&middot;", "·");
        Matcher m = Pattern.compile("&#(x?)([0-9a-fA-F]+);").matcher(r);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            try {
                int cp = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
                m.appendReplacement(sb, new String(Character.toChars(cp)));
            } catch (Exception e) {
                m.appendReplacement(sb, "");
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 单元格里可能塞了多门课（例如单双周冲突课）：
     * 按 kbcontent div / <hr> / 强智「-----」分隔线 / 老正方「<br><br>」双换行依次切分。
     */
    public static List<String> blocks(String cellHtml) {
        List<String> seeds = new ArrayList<>();
        if (cellHtml == null) return seeds;

        // 强智网格有时同时存在简化版 kbcontent1 和完整版 kbcontent（隐藏），优先取完整 kbcontent
        Matcher mFull = Pattern.compile("<div\\b[^>]*class=[\"'][^\"']*\\bkbcontent\\b[^\"']*[\"'][^>]*>(.*?)</div\\s*>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(cellHtml);
        while (mFull.find()) {
            if (!text(mFull.group(1)).isEmpty()) seeds.add(mFull.group(1));
        }
        if (seeds.isEmpty()) {
            Matcher mAny = Pattern.compile("<div\\b[^>]*class=[\"'][^\"']*kbcontent[^\"']*[\"'][^>]*>(.*?)</div\\s*>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(cellHtml);
            while (mAny.find()) {
                if (!text(mAny.group(1)).isEmpty()) seeds.add(mAny.group(1));
            }
        }
        if (seeds.isEmpty()) seeds.add(cellHtml);

        List<String> out = new ArrayList<>();
        for (String chunk : seeds) {
            // 按 <hr>、5 个以上连续连字符（强智）、或连续两个 <br>（老正方）切分
            String[] parts = chunk.split("(?i)<hr\\s*/?>|(?:<br\\s*/?>\\s*){2,}|-{5,}");
            for (String p : parts) {
                if (!text(p).isEmpty()) out.add(p);
            }
        }
        if (out.isEmpty() && !text(cellHtml).isEmpty()) out.add(cellHtml);
        return out;
    }

    public static Map<String, Integer> headerIndex(String[] headerRow) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < headerRow.length; i++) {
            String t = text(headerRow[i]).replaceAll("\\s+", "");
            if (!t.isEmpty()) map.put(t, i);
        }
        return map;
    }
}
