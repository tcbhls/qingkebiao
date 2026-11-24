package com.qingkebiao.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 课表解析结果：课程列表 + 表格提取到的节次时间 + 页面提示周次 */
public class KbResult {
    public List<Course> courses = new ArrayList<>();
    /** 节次 -> 起始时间，如 1 -> "08:00" */
    public Map<Integer, String> nodeTime = new LinkedHashMap<>();
    public String weekHint = "";
    /** 数据行里出现过的字段名，供排查兼容问题 */
    public String rawKeys = "";
}
