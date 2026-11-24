package com.qingkebiao.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻课表主界面（3 板块：课表 / 考试 / 导入与设置）。
 * 纯原生 View 构建，零第三方依赖，通用适配绝大多数高校教务系统，支持双桌面小部件实时换肤与手动补录课程。
 */
public class MainActivity extends Activity {

    private static final int REQ_IMPORT = 101;
    private static final String[] DOW_NAME = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private int activeTab = 0; // 0=课表, 1=考试, 2=导入与设置
    private int viewWeekOffset = 0;
    private boolean diagExpanded = false;

    private TextView tvSchoolBadge;
    private TextView btnTopAction;
    private TextView[] tabBtns = new TextView[3];
    private LinearLayout contentHost;

    private EditText etUser, etPass, etSchoolName, etSchoolUrl, etCasUrl, etStart, etTerm, etKbUrl, etExamUrl;
    private TextView tvStatusInline;

    private int dp(float v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().setStatusBarColor(0xFFFFFFFF);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F6FA);

        // 1) 顶部栏
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(Color.WHITE);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(18), dp(14), dp(14), dp(12));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("轻课表");
        tvTitle.setTextSize(20f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(0xFF18181B);
        titleCol.addView(tvTitle);

        tvSchoolBadge = new TextView(this);
        tvSchoolBadge.setTextSize(11.5f);
        tvSchoolBadge.setTextColor(0xFF71717A);
        titleCol.addView(tvSchoolBadge);

        topBar.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnTopAction = new TextView(this);
        btnTopAction.setText("＋ 导入");
        btnTopAction.setTextSize(12.5f);
        btnTopAction.setTypeface(Typeface.DEFAULT_BOLD);
        btnTopAction.setTextColor(0xFF2563EB);
        btnTopAction.setBackground(pillBg(0xFFEFF6FF, 0, dp(16)));
        btnTopAction.setPadding(dp(12), dp(6), dp(12), dp(6));
        btnTopAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTopActionClick();
            }
        });
        topBar.addView(btnTopAction);

        root.addView(topBar);

        // 2) 三标签切换条
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundColor(Color.WHITE);
        tabBar.setPadding(dp(14), dp(2), dp(14), dp(10));
        String[] labels = {"课表", "考试", "导入与设置"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView tb = new TextView(this);
            tb.setText(labels[i]);
            tb.setGravity(Gravity.CENTER);
            tb.setTextSize(13.5f);
            tb.setPadding(0, dp(8), 0, dp(8));
            tb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    activeTab = idx;
                    renderTab();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(8);
            tabBar.addView(tb, lp);
            tabBtns[i] = tb;
        }
        root.addView(tabBar);

        View div = new View(this);
        div.setBackgroundColor(0x14000000);
        root.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // 3) 内容滚动区
        ScrollView scroll = new ScrollView(this);
        contentHost = new LinearLayout(this);
        contentHost.setOrientation(LinearLayout.VERTICAL);
        contentHost.setPadding(dp(14), dp(12), dp(14), dp(28));
        scroll.addView(contentHost, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        Scheduler.schedule(this);
        if (Prefs.courses(this).isEmpty()) activeTab = 2;
        renderTab();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHeader();
        if (activeTab != 2) renderTab();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT && resultCode == RESULT_OK) {
            activeTab = 0;
            renderTab();
        }
    }

    private void updateHeader() {
        int curWeek = Prefs.currentWeek(this);
        tvSchoolBadge.setText(SchoolProfile.schoolName(this) + " · 第" + curWeek + "周");
        btnTopAction.setText(SchoolProfile.isCasAuto(this) ? "↻ 刷新" : "＋ 导入");
    }

    private void onTopActionClick() {
        if (SchoolProfile.isCasAuto(this)) {
            if (SchoolProfile.customUrl(this).isEmpty()
                    || Prefs.user(this).isEmpty() || Prefs.pass(this).isEmpty()) {
                activeTab = 2;
                renderTab();
                Toast.makeText(this, "请先填写教务网址与账号信息", Toast.LENGTH_SHORT).show();
            } else {
                doCasRefresh();
            }
        } else {
            openWebImport();
        }
    }

    private void renderTab() {
        updateHeader();
        for (int i = 0; i < 3; i++) {
            boolean sel = (i == activeTab);
            tabBtns[i].setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tabBtns[i].setTextColor(sel ? Color.WHITE : 0xFF52525B);
            tabBtns[i].setBackground(pillBg(sel ? 0xFF2563EB : 0xFFF1F5F9, 0, dp(10)));
        }
        contentHost.removeAllViews();
        if (activeTab == 0) buildCourseTab();
        else if (activeTab == 1) buildExamTab();
        else buildSettingsTab();
    }

    // ==================== Tab 0: 课表 ====================

    private void buildCourseTab() {
        int curWeek = Prefs.currentWeek(this);
        int showWeek = Math.max(1, Math.min(30, curWeek + viewWeekOffset));
        boolean isCur = (viewWeekOffset == 0);

        LinearLayout bar = cardLayout();
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView prev = chipBtn("‹ 上周", 0xFFF1F5F9, 0xFF3F3F46);
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewWeekOffset--;
                renderTab();
            }
        });
        bar.addView(prev);

        TextView mid = new TextView(this);
        mid.setText("第 " + showWeek + " 周" + (isCur ? " · 本周" : "（点此回本周）"));
        mid.setGravity(Gravity.CENTER);
        mid.setTextSize(14f);
        mid.setTypeface(Typeface.DEFAULT_BOLD);
        mid.setTextColor(isCur ? 0xFF18181B : 0xFF2563EB);
        mid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (viewWeekOffset != 0) {
                    viewWeekOffset = 0;
                    renderTab();
                }
            }
        });
        bar.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView next = chipBtn("下周 ›", 0xFFF1F5F9, 0xFF3F3F46);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewWeekOffset++;
                renderTab();
            }
        });
        bar.addView(next);
        contentHost.addView(bar, lpBottom(dp(12)));

        List<Course> all = Prefs.courses(this);
        if (all.isEmpty()) {
            LinearLayout emptyCard = cardLayout();
            emptyCard.setGravity(Gravity.CENTER);
            emptyCard.setPadding(dp(20), dp(36), dp(20), dp(36));

            TextView t = new TextView(this);
            t.setText("还没有课表数据");
            t.setTextSize(15f);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextColor(0xFF18181B);
            emptyCard.addView(t);

            TextView sub = new TextView(this);
            sub.setText("通过内置浏览器登录任意高校教务一键导入，或手动添加课程");
            sub.setGravity(Gravity.CENTER);
            sub.setTextSize(12.5f);
            sub.setTextColor(0xFF71717A);
            sub.setPadding(0, dp(6), 0, dp(16));
            emptyCard.addView(sub);

            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setGravity(Gravity.CENTER);

            TextView go = chipBtn("去导入课表 →", 0xFF2563EB, Color.WHITE);
            go.setPadding(dp(16), dp(10), dp(16), dp(10));
            go.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    activeTab = 2;
                    renderTab();
                }
            });
            btnRow.addView(go);

            TextView add = chipBtn("＋ 手动添课", 0xFFF1F5F9, 0xFF27272A);
            add.setPadding(dp(16), dp(10), dp(16), dp(10));
            add.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAddCourseDialog();
                }
            });
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.leftMargin = dp(10);
            btnRow.addView(add, alp);

            emptyCard.addView(btnRow);
            contentHost.addView(emptyCard);
            return;
        }

        Map<Integer, String> times = Prefs.nodeTime(this);
        Calendar mon = Prefs.firstMonday(this);
        mon.add(Calendar.DAY_OF_MONTH, (showWeek - 1) * 7);
        int todayDow = isCur ? Prefs.todayDow() : -1;

        int shownDays = 0;
        for (int dow = 1; dow <= 7; dow++) {
            List<Course> dayList = new ArrayList<>();
            for (Course c : all) {
                if (c.dayOfWeek == dow && c.activeInWeek(showWeek)) dayList.add(c);
            }
            if (dayList.isEmpty()) continue;
            shownDays++;
            Collections.sort(dayList, new Comparator<Course>() {
                @Override
                public int compare(Course a, Course b) {
                    return Integer.compare(a.startNode, b.startNode);
                }
            });

            Calendar dc = (Calendar) mon.clone();
            dc.add(Calendar.DAY_OF_MONTH, dow - 1);
            String dateLabel = (dc.get(Calendar.MONTH) + 1) + "月" + dc.get(Calendar.DAY_OF_MONTH) + "日";
            boolean isToday = (dow == todayDow);

            LinearLayout dayHdr = new LinearLayout(this);
            dayHdr.setOrientation(LinearLayout.HORIZONTAL);
            dayHdr.setGravity(Gravity.CENTER_VERTICAL);
            dayHdr.setPadding(dp(4), dp(4), dp(4), dp(6));

            TextView dt = new TextView(this);
            dt.setText(DOW_NAME[dow - 1] + "  " + dateLabel);
            dt.setTextSize(13f);
            dt.setTypeface(Typeface.DEFAULT_BOLD);
            dt.setTextColor(isToday ? 0xFF2563EB : 0xFF52525B);
            dayHdr.addView(dt);

            if (isToday) {
                TextView badge = chipBtn("今天", 0xFFDBEAFE, 0xFF1D4ED8);
                badge.setTextSize(10.5f);
                badge.setPadding(dp(7), dp(2), dp(7), dp(2));
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                blp.leftMargin = dp(8);
                dayHdr.addView(badge, blp);
            }
            contentHost.addView(dayHdr);

            LinearLayout card = cardLayout();
            card.setPadding(dp(12), dp(6), dp(12), dp(6));
            for (int i = 0; i < dayList.size(); i++) {
                Course c = dayList.get(i);
                if (i > 0) {
                    View sep = new View(this);
                    sep.setBackgroundColor(0x10000000);
                    card.addView(sep, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
                }
                card.addView(buildCourseRow(c, times));
            }
            contentHost.addView(card, lpBottom(dp(12)));
        }

        if (shownDays == 0) {
            LinearLayout empty = cardLayout();
            empty.setPadding(dp(16), dp(28), dp(16), dp(28));
            empty.setGravity(Gravity.CENTER);
            TextView tv = new TextView(this);
            tv.setText("第 " + showWeek + " 周没有安排课程");
            tv.setTextSize(14f);
            tv.setTextColor(0xFF71717A);
            empty.addView(tv);
            contentHost.addView(empty, lpBottom(dp(12)));
        }

        Button btnAdd = secondaryBtn("＋ 手动添加一门课程（点击已有课程可删除）");
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddCourseDialog();
            }
        });
        contentHost.addView(btnAdd);
    }

    private View buildCourseRow(final Course c, Map<Integer, String> times) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCourseDetailDialog(c);
            }
        });

        View bar = new View(this);
        bar.setBackground(pillBg(Theme.accentColor(c.name), 0, dp(3)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(4), dp(36));
        blp.rightMargin = dp(10);
        row.addView(bar, blp);

        LinearLayout timeCol = new LinearLayout(this);
        timeCol.setOrientation(LinearLayout.VERTICAL);
        String st = times.get(c.startNode);
        String en = Prefs.nodeEnd(times.get(c.endNode));
        TextView t1 = new TextView(this);
        t1.setText(st == null ? ("第" + c.startNode + "节") : st);
        t1.setTextSize(13f);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        t1.setTextColor(0xFF18181B);
        timeCol.addView(t1);

        TextView t2 = new TextView(this);
        String pStr = (c.startNode == c.endNode) ? (c.startNode + "节") : (c.startNode + "-" + c.endNode + "节");
        t2.setText(en.isEmpty() ? pStr : (en + " · " + pStr));
        t2.setTextSize(10.5f);
        t2.setTextColor(0xFF71717A);
        timeCol.addView(t2);
        row.addView(timeCol, new LinearLayout.LayoutParams(dp(74), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        TextView nm = new TextView(this);
        nm.setText(c.name);
        nm.setTextSize(14.5f);
        nm.setTypeface(Typeface.DEFAULT_BOLD);
        nm.setTextColor(0xFF18181B);
        infoCol.addView(nm);

        if (c.teacher != null && !c.teacher.isEmpty()) {
            TextView tea = new TextView(this);
            tea.setText(c.teacher);
            tea.setTextSize(11.5f);
            tea.setTextColor(0xFF71717A);
            infoCol.addView(tea);
        }
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ilp.leftMargin = dp(6);
        ilp.rightMargin = dp(6);
        row.addView(infoCol, ilp);

        if (c.room != null && !c.room.isEmpty()) {
            TextView rm = chipBtn(c.room, Theme.blockBg(c.name, false), Theme.blockText(c.name, false));
            rm.setTextSize(11.5f);
            rm.setPadding(dp(8), dp(4), dp(8), dp(4));
            row.addView(rm);
        }
        return row;
    }

    private void showCourseDetailDialog(final Course c) {
        StringBuilder sb = new StringBuilder();
        sb.append("时间：").append(DOW_NAME[Math.max(0, Math.min(6, c.dayOfWeek - 1))])
                .append(" 第 ").append(c.startNode).append("-").append(c.endNode).append(" 节\n");
        if (!c.room.isEmpty()) sb.append("教室：").append(c.room).append('\n');
        if (!c.teacher.isEmpty()) sb.append("教师：").append(c.teacher).append('\n');
        if (c.weeks != null && c.weeks.length > 0) {
            sb.append("周次：共 ").append(c.weeks.length).append(" 周（第 ")
                    .append(c.weeks[0]).append("-").append(c.weeks[c.weeks.length - 1]).append(" 周）");
        } else {
            sb.append("周次：全学期");
        }
        new AlertDialog.Builder(this)
                .setTitle(c.name)
                .setMessage(sb.toString())
                .setPositiveButton("关闭", null)
                .setNegativeButton("删除此课", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Prefs.removeCourse(MainActivity.this, c);
                        WidgetProvider.updateAll(MainActivity.this);
                        renderTab();
                        Toast.makeText(MainActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showAddCourseDialog() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), dp(8));

        final EditText fName = fieldInput("如：数据结构与算法", InputType.TYPE_CLASS_TEXT, "");
        final EditText fDow = fieldInput("1-7（1=周一，7=周日）", InputType.TYPE_CLASS_NUMBER, "1");
        final EditText fNodes = fieldInput("如：1-2 或 3-4", InputType.TYPE_CLASS_TEXT, "1-2");
        final EditText fRoom = fieldInput("如：博学楼201（选填）", InputType.TYPE_CLASS_TEXT, "");
        final EditText fTea = fieldInput("如：张明（选填）", InputType.TYPE_CLASS_TEXT, "");
        final EditText fWeeks = fieldInput("如：1-16周 或 1-15周(单)", InputType.TYPE_CLASS_TEXT, "1-16周");

        box.addView(fieldLabel("课程名称")); box.addView(fName);
        box.addView(fieldLabel("星期几（1~7）")); box.addView(fDow);
        box.addView(fieldLabel("节次范围")); box.addView(fNodes);
        box.addView(fieldLabel("上课教室")); box.addView(fRoom);
        box.addView(fieldLabel("任课教师")); box.addView(fTea);
        box.addView(fieldLabel("上课周次")); box.addView(fWeeks);
        sv.addView(box);

        new AlertDialog.Builder(this)
                .setTitle("手动添加课程")
                .setView(sv)
                .setPositiveButton("添加", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = fName.getText().toString().trim();
                        if (name.isEmpty()) {
                            Toast.makeText(MainActivity.this, "请填写课程名称", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        int dow = 1;
                        try {
                            dow = Math.max(1, Math.min(7, Integer.parseInt(fDow.getText().toString().trim())));
                        } catch (Exception ignored) {
                        }
                        int[] nr = KbParser.parseJcdm(fNodes.getText().toString().trim());
                        Course c = new Course();
                        c.name = name;
                        c.dayOfWeek = dow;
                        c.startNode = nr[0] > 0 ? nr[0] : 1;
                        c.endNode = nr[1] >= c.startNode ? nr[1] : c.startNode;
                        c.room = fRoom.getText().toString().trim();
                        c.teacher = fTea.getText().toString().trim();
                        c.weeksRaw = fWeeks.getText().toString().trim();
                        c.weeks = KbParser.parseWeeks(c.weeksRaw);
                        Prefs.addCourse(MainActivity.this, c);
                        WidgetProvider.updateAll(MainActivity.this);
                        renderTab();
                        Toast.makeText(MainActivity.this, "已添加：" + c.name, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== Tab 1: 考试 ====================

    private void buildExamTab() {
        List<Exam> es = Prefs.exams(this);
        if (es.isEmpty()) {
            LinearLayout empty = cardLayout();
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(36), dp(20), dp(36));
            TextView t = new TextView(this);
            t.setText("暂无考试安排");
            t.setTextSize(15f);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextColor(0xFF18181B);
            empty.addView(t);
            TextView sub = new TextView(this);
            sub.setText("在内置浏览器中打开学校「考试安排」页面后点击「一键提取考试」即可同步");
            sub.setGravity(Gravity.CENTER);
            sub.setTextSize(12.5f);
            sub.setTextColor(0xFF71717A);
            sub.setPadding(0, dp(6), 0, 0);
            empty.addView(sub);
            contentHost.addView(empty);
            return;
        }

        List<Exam> upcoming = new ArrayList<>();
        List<Exam> past = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Exam e : es) {
            if (e.startMillis > 0 && e.startMillis + 3L * 3600 * 1000 < now) past.add(e);
            else upcoming.add(e);
        }
        Comparator<Exam> cmp = new Comparator<Exam>() {
            @Override
            public int compare(Exam a, Exam b) {
                long x = a.startMillis <= 0 ? Long.MAX_VALUE : a.startMillis;
                long y = b.startMillis <= 0 ? Long.MAX_VALUE : b.startMillis;
                return Long.compare(x, y);
            }
        };
        Collections.sort(upcoming, cmp);
        Collections.sort(past, cmp);

        if (!upcoming.isEmpty()) {
            contentHost.addView(sectionTitle("即将考试（" + upcoming.size() + " 场）"));
            for (Exam e : upcoming) contentHost.addView(buildExamCard(e, false), lpBottom(dp(10)));
        }
        if (!past.isEmpty()) {
            contentHost.addView(sectionTitle("已结束（" + past.size() + " 场）"));
            for (Exam e : past) contentHost.addView(buildExamCard(e, true), lpBottom(dp(8)));
        }
    }

    private View buildExamCard(Exam e, boolean ended) {
        LinearLayout card = cardLayout();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        if (ended) card.setAlpha(0.55f);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(e.name);
        name.setTextSize(15f);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(0xFF18181B);
        left.addView(name);

        StringBuilder meta = new StringBuilder();
        if (!e.timeRaw.isEmpty()) meta.append(e.timeRaw);
        else if (e.startMillis > 0) meta.append(Prefs.fmtTime(e.startMillis));
        if (!e.room.isEmpty()) meta.append("  ·  ").append(e.room);
        if (!e.seat.isEmpty()) meta.append("  座位 ").append(e.seat);

        TextView sub = new TextView(this);
        sub.setText(meta.toString());
        sub.setTextSize(12f);
        sub.setTextColor(0xFF52525B);
        sub.setPadding(0, dp(4), 0, 0);
        left.addView(sub);

        card.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        String badgeTxt;
        int bg, fg;
        if (ended) {
            badgeTxt = "已结束";
            bg = 0xFFF1F5F9;
            fg = 0xFF71717A;
        } else if (e.startMillis > 0) {
            long days = Math.max(0, Prefs.daysUntil(e.startMillis, System.currentTimeMillis()));
            badgeTxt = days == 0 ? "今天" : (days + " 天后");
            bg = days <= 7 ? 0xFFFEE2E2 : 0xFFEFF6FF;
            fg = days <= 7 ? 0xFFDC2626 : 0xFF2563EB;
        } else {
            badgeTxt = "待定";
            bg = 0xFFF1F5F9;
            fg = 0xFF52525B;
        }
        TextView badge = chipBtn(badgeTxt, bg, fg);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = dp(10);
        card.addView(badge, blp);
        return card;
    }

    // ==================== Tab 2: 导入与设置 ====================

    private void buildSettingsTab() {
        // ---- Card 1: 学校与课表导入 ----
        contentHost.addView(sectionTitle("学校与课表导入"));
        LinearLayout cSchool = cardLayout();
        cSchool.setPadding(dp(14), dp(12), dp(14), dp(14));

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView tvMode = new TextView(this);
        tvMode.setText("当前模式：" + (SchoolProfile.isCasAuto(this)
                ? "金智 CAS 自动刷新" : "内置浏览器一键导入（通用）"));
        tvMode.setTextSize(13.5f);
        tvMode.setTypeface(Typeface.DEFAULT_BOLD);
        tvMode.setTextColor(0xFF18181B);
        modeRow.addView(tvMode, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnSwitch = chipBtn("切换教务类型", 0xFFEFF6FF, 0xFF2563EB);
        btnSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSchoolPicker();
            }
        });
        modeRow.addView(btnSwitch);
        cSchool.addView(modeRow);

        cSchool.addView(fieldLabel("学校名称（显示在周课表顶部）"));
        etSchoolName = fieldInput("例如：星辰大学", InputType.TYPE_CLASS_TEXT,
                SchoolProfile.schoolName(this));
        cSchool.addView(etSchoolName);

        cSchool.addView(fieldLabel("教务系统网址"));
        etSchoolUrl = fieldInput("https://jw.example.edu.cn",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
                SchoolProfile.customUrl(this));
        cSchool.addView(etSchoolUrl);

        if (SchoolProfile.isCasAuto(this)) {
            cSchool.addView(fieldLabel("统一认证登录页（选填，留空自动探测）"));
            etCasUrl = fieldInput("如 https://authserver.example.edu.cn/authserver/login",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
                    SchoolProfile.casUrl(this));
            cSchool.addView(etCasUrl);

            cSchool.addView(fieldLabel("学号 / 账号"));
            etUser = fieldInput("输入学号", InputType.TYPE_CLASS_TEXT, Prefs.user(this));
            cSchool.addView(etUser);

            cSchool.addView(fieldLabel("统一认证密码"));
            etPass = fieldInput("输入密码",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, Prefs.pass(this));
            cSchool.addView(etPass);

            Button btnGo = primaryBtn("保存并立即自动拉取");
            btnGo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveAllSchoolFields();
                    doCasRefresh();
                }
            });
            cSchool.addView(btnGo, lpTop(dp(12)));

            Button btnWebAlt = secondaryBtn("或使用内置浏览器登录提取（遇验证码推荐）");
            btnWebAlt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveAllSchoolFields();
                    openWebImport();
                }
            });
            cSchool.addView(btnWebAlt, lpTop(dp(8)));
        } else {
            Button btnWeb = primaryBtn("打开内置浏览器登录并一键提取");
            btnWeb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveAllSchoolFields();
                    openWebImport();
                }
            });
            cSchool.addView(btnWeb, lpTop(dp(12)));

            TextView compatTip = new TextView(this);
            compatTip.setText("已兼容新/老正方、强智、金智 EMAP/EasyUI、树维 EAMS、新 URP、青果及通用 HTML 表格与 AJAX JSON 教务系统。");
            compatTip.setTextSize(11.5f);
            compatTip.setTextColor(0xFF71717A);
            compatTip.setPadding(0, dp(8), 0, 0);
            cSchool.addView(compatTip);
        }

        tvStatusInline = new TextView(this);
        tvStatusInline.setTextSize(12f);
        tvStatusInline.setTextColor(0xFF71717A);
        tvStatusInline.setPadding(0, dp(10), 0, 0);
        updateInlineStatus();
        cSchool.addView(tvStatusInline);
        contentHost.addView(cSchool, lpBottom(dp(14)));

        // ---- Card 2: 桌面小部件外观 ----
        contentHost.addView(sectionTitle("桌面小部件外观（修改后实时同步）"));
        LinearLayout cTheme = cardLayout();
        cTheme.setPadding(dp(14), dp(12), dp(14), dp(14));

        cTheme.addView(fieldLabel("卡片主题"));
        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] tNames = {"浅色磨砂", "深色磨砂", "全透明"};
        final int curMode = Theme.widgetMode(this);
        for (int i = 0; i < 3; i++) {
            final int m = i;
            boolean sel = (curMode == m);
            TextView tb = chipBtn(tNames[i], sel ? 0xFF2563EB : 0xFFF1F5F9, sel ? Color.WHITE : 0xFF3F3F46);
            tb.setGravity(Gravity.CENTER);
            tb.setPadding(0, dp(9), 0, dp(9));
            tb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Theme.setWidgetMode(MainActivity.this, m);
                    WidgetProvider.updateAll(MainActivity.this);
                    renderTab();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(8);
            themeRow.addView(tb, lp);
        }
        cTheme.addView(themeRow, lpTop(dp(4)));

        final int curOp = Theme.widgetOpacity(this);
        final TextView tvOp = fieldLabel("背景不透明度：" + curOp + "%");
        cTheme.addView(tvOp);

        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(curOp);
        sb.setEnabled(curMode != Theme.MODE_TRANSPARENT);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvOp.setText("背景不透明度：" + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Theme.setWidgetOpacity(MainActivity.this, seekBar.getProgress());
                WidgetProvider.updateAll(MainActivity.this);
            }
        });
        cTheme.addView(sb);

        CheckBox cbWeekend = new CheckBox(this);
        cbWeekend.setText("周课表小部件始终显示周六、周日（关闭则有课时才显示）");
        cbWeekend.setTextSize(12.5f);
        cbWeekend.setTextColor(0xFF3F3F46);
        cbWeekend.setChecked(Theme.showWeekend(this));
        cbWeekend.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Theme.setShowWeekend(MainActivity.this, isChecked);
                WidgetProvider.updateAll(MainActivity.this);
            }
        });
        cTheme.addView(cbWeekend, lpTop(dp(6)));

        CheckBox cbExam = new CheckBox(this);
        cbExam.setText("日程小部件底部显示最近一场考试倒计时");
        cbExam.setTextSize(12.5f);
        cbExam.setTextColor(0xFF3F3F46);
        cbExam.setChecked(Theme.showExamInWidget(this));
        cbExam.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Theme.setShowExamInWidget(MainActivity.this, isChecked);
                WidgetProvider.updateAll(MainActivity.this);
            }
        });
        cTheme.addView(cbExam, lpTop(dp(2)));

        TextView wHint = new TextView(this);
        wHint.setText("桌面长按空白处 → 小组件 → 可自由添加「轻课表 · 日程」和「轻课表 · 周课表」两个小部件。");
        wHint.setTextSize(11.5f);
        wHint.setTextColor(0xFF71717A);
        wHint.setPadding(0, dp(8), 0, 0);
        cTheme.addView(wHint);
        contentHost.addView(cTheme, lpBottom(dp(14)));

        // ---- Card 3: 学期与作息时间 ----
        contentHost.addView(sectionTitle("学期与作息时间"));
        LinearLayout cTime = cardLayout();
        cTime.setPadding(dp(14), dp(12), dp(14), dp(14));

        cTime.addView(fieldLabel("第 1 周的周一（留空自动推算，格式 2026-09-07）"));
        LinearLayout startRow = new LinearLayout(this);
        startRow.setOrientation(LinearLayout.HORIZONTAL);
        startRow.setGravity(Gravity.CENTER_VERTICAL);
        etStart = fieldInput("留空自动", InputType.TYPE_CLASS_TEXT, Prefs.termStart(this));
        startRow.addView(etStart, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView btnSaveDate = chipBtn("应用", 0xFFEFF6FF, 0xFF2563EB);
        btnSaveDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String s = etStart.getText().toString().trim();
                Prefs.saveTermStart(MainActivity.this, s.matches("\\d{4}-\\d{1,2}-\\d{1,2}") ? s : "");
                WidgetProvider.updateAll(MainActivity.this);
                updateHeader();
                Toast.makeText(MainActivity.this, "已更新当前周次：第 " + Prefs.currentWeek(MainActivity.this) + " 周",
                        Toast.LENGTH_SHORT).show();
            }
        });
        startRow.addView(btnSaveDate);
        cTime.addView(startRow);

        cTime.addView(fieldLabel("作息预设与自定义"));
        LinearLayout schRow = new LinearLayout(this);
        schRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] shortSch = {"通用 08:00", "通用 08:30", "14节 08:30"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView sbBtn = chipBtn(shortSch[i], 0xFFF1F5F9, 0xFF3F3F46);
            sbBtn.setGravity(Gravity.CENTER);
            sbBtn.setPadding(0, dp(8), 0, dp(8));
            sbBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.saveNodeTime(MainActivity.this,
                            SchoolProfile.presetTimes(SchoolProfile.SCHEDULE_IDS[idx]), true);
                    WidgetProvider.updateAll(MainActivity.this);
                    Toast.makeText(MainActivity.this, "已应用：" + SchoolProfile.SCHEDULE_NAMES[idx],
                            Toast.LENGTH_SHORT).show();
                    renderTab();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(6);
            schRow.addView(sbBtn, lp);
        }
        cTime.addView(schRow, lpTop(dp(4)));

        Button btnEditSlots = secondaryBtn("逐节编辑上课时间（1-14节）");
        btnEditSlots.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showScheduleEditor();
            }
        });
        cTime.addView(btnEditSlots, lpTop(dp(8)));
        contentHost.addView(cTime, lpBottom(dp(14)));

        // ---- Card 4: 高级诊断（默认折叠） ----
        LinearLayout cDiag = cardLayout();
        cDiag.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView tvDiagToggle = new TextView(this);
        tvDiagToggle.setText(diagExpanded ? "高级选项与诊断 ▾" : "高级选项与诊断 ▸");
        tvDiagToggle.setTextSize(13f);
        tvDiagToggle.setTextColor(0xFF71717A);
        tvDiagToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                diagExpanded = !diagExpanded;
                renderTab();
            }
        });
        cDiag.addView(tvDiagToggle);

        if (diagExpanded) {
            cDiag.addView(fieldLabel("学期代码（留空自动识别）"));
            etTerm = fieldInput("如 202601", InputType.TYPE_CLASS_TEXT, Prefs.term(this));
            cDiag.addView(etTerm);

            cDiag.addView(fieldLabel("手工课表页 URL（一般留空）"));
            etKbUrl = fieldInput("留空自动", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
                    Prefs.manualKb(this));
            cDiag.addView(etKbUrl);

            cDiag.addView(fieldLabel("手工考试页 URL（一般留空）"));
            etExamUrl = fieldInput("留空自动", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
                    Prefs.manualExam(this));
            cDiag.addView(etExamUrl);

            Button btnSaveAdv = secondaryBtn("保存高级参数");
            btnSaveAdv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.saveTerm(MainActivity.this, etTerm.getText().toString().trim());
                    Prefs.saveManualUrls(MainActivity.this,
                            etKbUrl.getText().toString().trim(),
                            etExamUrl.getText().toString().trim());
                    Toast.makeText(MainActivity.this, "已保存", Toast.LENGTH_SHORT).show();
                }
            });
            cDiag.addView(btnSaveAdv, lpTop(dp(8)));

            Button btnCopyDiag = secondaryBtn("复制诊断信息到剪贴板（不含密码）");
            btnCopyDiag.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copyDiag();
                }
            });
            cDiag.addView(btnCopyDiag, lpTop(dp(8)));
        }
        contentHost.addView(cDiag);
    }

    private void showSchoolPicker() {
        String[] items = new String[SchoolProfile.PRESETS.length];
        for (int i = 0; i < SchoolProfile.PRESETS.length; i++) {
            items[i] = SchoolProfile.PRESETS[i].name;
        }
        new AlertDialog.Builder(this)
                .setTitle("选择教务系统类型")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SchoolProfile.Preset p = SchoolProfile.PRESETS[which];
                        SchoolProfile.setMode(MainActivity.this, p.mode);
                        Prefs.saveNodeTime(MainActivity.this,
                                SchoolProfile.presetTimes(p.schedulePreset), true);
                        WidgetProvider.updateAll(MainActivity.this);
                        renderTab();
                    }
                })
                .show();
    }

    private void showScheduleEditor() {
        Map<Integer, String> cur = Prefs.nodeTime(this);
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(12), dp(18), dp(8));

        final EditText[] edits = new EditText[14];
        for (int i = 1; i <= 14; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView lbl = new TextView(this);
            lbl.setText("第 " + i + " 节开始时间");
            lbl.setTextSize(13f);
            lbl.setTextColor(0xFF3F3F46);
            row.addView(lbl, new LinearLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.WRAP_CONTENT));

            EditText et = new EditText(this);
            et.setSingleLine(true);
            et.setTextSize(14f);
            et.setHint("HH:MM");
            String v = cur.get(i);
            et.setText(v == null ? "" : v);
            row.addView(et, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            edits[i - 1] = et;
            box.addView(row);
        }
        sv.addView(box);

        new AlertDialog.Builder(this)
                .setTitle("自定义每节上课时间")
                .setView(sv)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Map<Integer, String> map = new LinkedHashMap<>();
                        for (int i = 1; i <= 14; i++) {
                            String v = edits[i - 1].getText().toString().trim();
                            if (v.matches("\\d{1,2}:\\d{2}")) {
                                if (v.length() == 4) v = "0" + v;
                                map.put(i, v);
                            }
                        }
                        if (!map.isEmpty()) {
                            Prefs.saveNodeTime(MainActivity.this, map, true);
                            WidgetProvider.updateAll(MainActivity.this);
                            Toast.makeText(MainActivity.this, "作息时间已更新", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveAllSchoolFields() {
        if (etSchoolName != null) {
            String n = etSchoolName.getText().toString().trim();
            if (!n.isEmpty()) SchoolProfile.setSchoolName(this, n);
        }
        if (etSchoolUrl != null) {
            SchoolProfile.setCustomUrl(this, etSchoolUrl.getText().toString().trim());
        }
        if (etCasUrl != null) {
            SchoolProfile.setCasUrl(this, etCasUrl.getText().toString().trim());
        }
        if (etUser != null && etPass != null) {
            Prefs.saveAccount(this, etUser.getText().toString().trim(), etPass.getText().toString());
        }
        if (etStart != null) {
            String s = etStart.getText().toString().trim();
            Prefs.saveTermStart(this, s.matches("\\d{4}-\\d{1,2}-\\d{1,2}") ? s : "");
        }
    }

    private void openWebImport() {
        Intent i = new Intent(this, ImportWebActivity.class);
        i.putExtra(ImportWebActivity.EXTRA_URL, SchoolProfile.customUrl(this));
        i.putExtra(ImportWebActivity.EXTRA_SCHOOL, SchoolProfile.schoolName(this));
        startActivityForResult(i, REQ_IMPORT);
    }

    private void doCasRefresh() {
        Toast.makeText(this, "正在登录并刷新…", Toast.LENGTH_SHORT).show();
        if (tvStatusInline != null) tvStatusInline.setText("正在登录并抓取课表与考试…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Repo.Result r;
                try {
                    r = Repo.refresh(MainActivity.this);
                } catch (Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "刷新异常：" + t.getClass().getSimpleName(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }
                try {
                    WidgetProvider.updateAll(MainActivity.this);
                } catch (Throwable ignored) {
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, r.message, Toast.LENGTH_LONG).show();
                        updateInlineStatus();
                        if (r.ok) {
                            activeTab = 0;
                            renderTab();
                        }
                    }
                });
            }
        }).start();
    }

    private void updateInlineStatus() {
        if (tvStatusInline == null) return;
        long lr = Prefs.lastRefresh(this);
        String msg = Prefs.lastMessage(this);
        int cc = Prefs.courses(this).size();
        int ec = Prefs.exams(this).size();
        tvStatusInline.setText("当前已存：" + cc + " 门课 · " + ec + " 场考试\n"
                + "上次更新：" + (lr == 0 ? "从未" : Prefs.fmtTime(lr))
                + (msg == null || msg.isEmpty() ? "" : "\n" + msg));
    }

    private void copyDiag() {
        StringBuilder sb = new StringBuilder();
        sb.append("QKB-DIAG v3.1\n");
        sb.append("mode=").append(SchoolProfile.mode(this)).append('\n');
        sb.append("school=").append(SchoolProfile.schoolName(this)).append('\n');
        sb.append("term=").append(Prefs.term(this)).append('\n');
        sb.append("week=").append(Prefs.currentWeek(this)).append('\n');
        sb.append("courses=").append(Prefs.courses(this).size()).append('\n');
        sb.append("exams=").append(Prefs.exams(this).size()).append('\n');
        sb.append("last=").append(Prefs.lastMessage(this)).append('\n');
        String kd = Repo.readDiag(this, "kb-data.txt", 2500);
        if (!kd.isEmpty()) sb.append("\n--- kb-data.txt ---\n").append(kd);
        String imp = Repo.readDiag(this, "import-last.txt", 2500);
        if (!imp.isEmpty()) sb.append("\n--- import-last.txt ---\n").append(imp);

        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("diag", sb.toString()));
        Toast.makeText(this, "已复制诊断信息", Toast.LENGTH_SHORT).show();
    }

    // ==================== UI 小工厂 ====================

    private LinearLayout cardLayout() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(pillBg(Color.WHITE, 0x0E000000, dp(16)));
        return l;
    }

    private GradientDrawable pillBg(int fillColor, int strokeColor, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        d.setCornerRadius(radiusPx);
        if (strokeColor != 0) d.setStroke(dp(1), strokeColor);
        return d;
    }

    private TextView chipBtn(String text, int bg, int fg) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12f);
        t.setTextColor(fg);
        t.setBackground(pillBg(bg, 0, dp(10)));
        t.setPadding(dp(10), dp(6), dp(10), dp(6));
        return t;
    }

    private TextView sectionTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12.5f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFF71717A);
        t.setPadding(dp(4), dp(2), 0, dp(6));
        return t;
    }

    private TextView fieldLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12f);
        t.setTextColor(0xFF71717A);
        t.setPadding(0, dp(10), 0, dp(2));
        return t;
    }

    private EditText fieldInput(String hintText, int type, String val) {
        EditText e = new EditText(this);
        e.setHint(hintText);
        e.setInputType(type);
        e.setTextSize(14.5f);
        e.setSingleLine(true);
        e.setText(val == null ? "" : val);
        return e;
    }

    private Button primaryBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14f);
        b.setAllCaps(false);
        b.setBackground(pillBg(0xFF2563EB, 0, dp(10)));
        return b;
    }

    private Button secondaryBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(0xFF27272A);
        b.setTextSize(13f);
        b.setAllCaps(false);
        b.setBackground(pillBg(0xFFF1F5F9, 0, dp(10)));
        return b;
    }

    private LinearLayout.LayoutParams lpTop(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = top;
        return lp;
    }

    private LinearLayout.LayoutParams lpBottom(int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = bottom;
        return lp;
    }
}
