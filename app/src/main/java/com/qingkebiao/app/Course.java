package com.qingkebiao.app;

/** 一门课在某个星期几、某几节、哪些周上课 */
public class Course {
    public String name = "";
    public String teacher = "";
    public String room = "";
    public int dayOfWeek = 1;   // 1=周一 .. 7=周日
    public int startNode = 1;   // 起始节次
    public int endNode = 1;     // 结束节次
    public String weeksRaw = ""; // 原始周次文本，如 "1-16周"
    public int[] weeks = new int[0];

    public boolean activeInWeek(int week) {
        if (weeks == null || weeks.length == 0) return true;
        for (int w : weeks) if (w == week) return true;
        return false;
    }

    public String key() {
        return dayOfWeek + "|" + startNode + "|" + name + "|" + room;
    }

    @Override
    public String toString() {
        return name + " " + room + " 周" + dayOfWeek + " 第" + startNode + "-" + endNode + "节 " + weeksRaw;
    }
}
