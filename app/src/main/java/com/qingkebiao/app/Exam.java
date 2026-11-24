package com.qingkebiao.app;

/** 考试安排条目 */
public class Exam {
    public String name = "";
    public String room = "";
    public String seat = "";
    public String timeRaw = "";
    public long startMillis = 0L; // 毫秒时间戳，0 表示尚未解析出具体日期

    public String key() {
        return name + "|" + timeRaw;
    }
}
