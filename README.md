# 轻课表 · QingKeBiao

> 轻量、开源、零第三方依赖的**高校通用安卓课程表 & 考试倒计时桌面小部件**。

![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)
![APK Size](https://img.shields.io/badge/APK-97%20KB-2563EB)
![Zero Dependencies](https://img.shields.io/badge/Dependencies-Zero-10B981)
![License](https://img.shields.io/badge/License-MIT-71717A)

打开手机第一眼就能看到今天及未来 7 天的课程与教室，或直接在桌面铺一张整周彩色网格课表，无需反复打开教务网站或微信小程序。

---

## 核心特性

### 1. 双桌面小部件 · 实时换肤与透明度调节
- **「轻课表 · 日程」**（4×4）：现代日程卡片，顶部显示日期、周次胶囊与刷新按钮；展示未来 7 天课程，左侧固定同课同色竖条，右侧圆角教室胶囊，底部可选显示最近一场考试倒计时。
- **「轻课表 · 周课表」**（4×4 / 5×4 自由拉伸）：整周彩色网格，按控件真实内容区比例与有效物理密度动态绘制（零变形、高分屏锐利清晰），支持点击右上角 `‹ / ›` 翻周、点击周次回到本周；周末有课自动展开 7 列。
- **外观自由定制**：支持「浅色磨砂」「深色磨砂」「全透明」三种主题与 `0–100%` 背景不透明度调节，修改后桌面小部件即时生效。

### 2. 四重提取引擎 · 适配国内绝大多数高校教务
只需在内置浏览器中登录自己学校的教务系统、打开课表或考试页面并点击底部按钮，即可自动提取：

1. **全框架 AJAX 嗅探**：通过 `MutationObserver` 与递归钩子穿透多层动态 `iframe` / `frame`，实时截获页面 XHR / Fetch 返回的 JSON。
2. **全学期自动补全拉取**：识别按周过滤的接口参数（如 `zc=13`、`week=13`），自动复用当前浏览器登录态 Cookie 拉取不带周次过滤的全学期数据——**导入一次管整个学期，无需每周重新导入**。
3. **前端组件直读 + DOM 视觉几何反推**：自动读取 EasyUI `datagrid` / FullCalendar 运行时数据，并对无标准表格的 `div` 绝对定位日历视图按星期列头与节次标尺坐标反推课程卡片，实现所见即所得。
4. **多教务格式解析与手动补录兜底**：兼容主流教务 HTML / JS / JSON，并在 App 内提供手动添加与删除课程兜底纯小程序课表高校。

| 教务系统 / 数据形式 | 课表 | 考试 | 关键兼容特性 |
| --- | :---: | :---: | --- |
| **新正方教务（`jwglxt`）** | ✅ | ✅ | 截获 `kbList` / `items` JSON，解析单双周与座位号 |
| **强智教务（`jsxsd`）** | ✅ | ✅ | 识别大节行头（`01-02节` / `第一大节`）、同格多课 `-----` 分隔与 `[01-02节]` |
| **金智 / EMAP 教务** | ✅ | ✅ | 解包嵌套 `datas.<action>.rows`、大写键名与 `01` 位图周次 |
| **EasyUI 教务（`getDataList`）** | ✅ | ✅ | 自动补拉全学期并合并重复周次；可选配置通用 CAS 后台定时刷新 |
| **树维教务（`EAMS`）** | ✅ | ✅ | 解析页面内联 `<script>` 中的 `TaskActivity` 与 53 位 `01` 位图周次 |
| **新 URP 综合教务** | ✅ | ✅ | 解析 `dateList → selectCourseList → timeAndPlaceList` 与 `classWeek` 位图 |
| **老正方（`xskbcx.aspx`） / 青果** | ✅ | ✅ | 支持 `<br><br>` 切分多课与 `周一第1,2节{第1-16周\|单周}` |
| **日历视图 / 通用 HTML 表格** | ✅ | ✅ | 兼容短表头（`一 11/23`）、列表式课表、日期与时间分列的考试表 |
| **手动添课 / 删课** | ✅ | — | 针对无网页教务或旁听课程随时手动维护 |

内置浏览器同时针对校园网环境做了兼容优化：
- 开启 SSO / CAS 跨域第三方 Cookie；
- 自动将 `window.open` / `target="_blank"` 菜单页重定向到当前窗口；
- 遇到校内自签名 SSL 证书时弹窗确认后可继续访问；
- 支持一键切换电脑版 / 手机版 UA，提供后退与刷新控制。

### 3. 灵活的作息与隐私设计
- **作息与开学周**：内置 3 套常见高校作息预设（12 节 08:00 起 / 12 节 08:30 起 / 14 节 08:30 起），支持逐节自定义 1–14 节上课时间与第 1 周周一日期。
- **纯本地、零依赖**：无任何后端服务器、统计 SDK 或广告，所有数据仅存于本机应用私有目录；仅依赖 Android 系统 API，不使用 Gradle / AndroidX / OkHttp，整包仅 **97 KB**。

---

## 快速上手

1. **安装 APK**：直接下载本仓库中的 [dist/qingkebiao-v3.1.apk](dist/qingkebiao-v3.1.apk)（Android 8.0+）安装，或按下方步骤本地构建。
2. **导入课表与考试**：
   - 打开「轻课表」→「导入与设置」，填写学校名称与教务系统网址 → 点击**「打开内置浏览器登录并一键提取」**；
   - 在网页中完成登录并点开「个人课表」→ 点击底部**「一键提取课表」**（弹窗会明确告知识别到的周次范围）；
   - 打开「考试安排」页面 → 点击底部**「一键提取考试」**。
3. **添加到桌面**：长按手机桌面空白处 → 小组件 → 添加「轻课表 · 日程」或「轻课表 · 周课表」，可在 App 内随时微调主题与透明度。

---

## 常见问题（FAQ）

- **需要每周重新导入一次课表吗？**  
  不需要。只要导入时弹窗提示「已包含全学期周次（第 X-Y 周）」，所有课程的起止周与单双周都已保存在本机，小部件会每周自动按自然日历切换。若个别学校默认视图仅渲染单周且未提供全学期接口，可在网页内将周次切换为「全部」或切到「列表展示 / 总课表」后再提取一次。
- **为什么安装包不到 100 KB？**  
  项目完全使用标准 Android 原生 API 与手写轻量 HTML/JSON 解析器，零第三方库依赖，因此体积小、启动快、耗电极低。
- **升级覆盖安装后桌面小部件没有变化？**  
  部分国产桌面启动器在布局变更后不会自动重建旧实例，删掉旧小部件再重新拖放一次即可。

---

## 本地构建与测试（无需 Android Studio）

采用 `aapt2 → javac → d8 → zipalign → apksigner` 极简脚本链，在 Windows PowerShell 中执行以下 3 条命令即可完成依赖拉取、构建签名与全部 57 项解析器单元测试：

```powershell
# 1. 首次运行：自动下载 JDK 17 与 Android SDK Build-Tools 到 .toolchain/
powershell -ExecutionPolicy Bypass -File .\tools\bootstrap-toolchain.ps1

# 2. 清理并构建签名 APK（输出至 dist/qingkebiao-v3.1.apk）
powershell -ExecutionPolicy Bypass -File .\tools\build.ps1 -Clean

# 3. 运行多高校解析器、周次与时区回归测试（57 passed）
powershell -ExecutionPolicy Bypass -File .\tools\test-parser.ps1
```

---

## 项目结构

```text
qingkebiao/
├─ app/src/main/
│  ├─ AndroidManifest.xml          应用与双小部件注册（com.qingkebiao.app）
│  ├─ java/com/qingkebiao/app/
│  │  ├─ MainActivity.java         三板块主界面（课表 / 考试 / 导入与设置 + 手动添删课）
│  │  ├─ ImportWebActivity.java    多高校通用内置浏览器（iframe 嗅探 + 全学期补拉 + DOM 几何提取）
│  │  ├─ WidgetProvider.java       「轻课表 · 日程」4×4 卡片小部件
│  │  ├─ WeekWidgetProvider.java   「轻课表 · 周课表」Canvas 彩色网格小部件
│  │  ├─ KbParser.java             多高校课表解析引擎（正方/强智/金智/树维/URP/视觉几何/通用）
│  │  ├─ ExamParser.java           多高校考试解析引擎
│  │  ├─ Html.java / Json.java     零依赖 HTML 表格网格与 JSON 解析器
│  │  ├─ Theme.java                12 色柔和调色盘与小部件主题配置
│  │  ├─ SchoolProfile.java        多教务预设与作息时间表配置
│  │  ├─ Prefs.java                本地持久化与本地自然日周次/倒计时算法
│  │  ├─ Cas.java / Jw.java / Menu.java / Discover.java / Repo.java   通用 CAS+EasyUI 后台刷新
│  │  └─ Http.java / Scheduler.java / RefreshReceiver.java / BootReceiver.java
│  └─ res/                         小部件布局、矢量图标与半透明圆角资源
├─ app/src/test/                   多高校虚构夹具与单元测试（57 项断言）
├─ dist/qingkebiao-v3.1.apk        预编译安装包（97 KB）
├─ tools/                          纯相对路径构建与测试脚本
├─ .gitignore                      忽略工具链、构建产物与签名私钥
└─ LICENSE                         MIT License
```

---

## License

本项目基于 [MIT License](LICENSE) 开源。
