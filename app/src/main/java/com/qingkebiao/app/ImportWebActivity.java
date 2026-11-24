package com.qingkebiao.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 任意高校通用浏览器导入页（四重提取引擎）：
 * 1) 递归穿透所有动态创建的子 iframe/frame 实时嗅探 XHR / Fetch JSON（含 MutationObserver + load 监听）
 * 2) 提取时主动读取前端组件运行时内存（EasyUI datagrid / FullCalendar clientEvents）
 * 3) DOM 视觉几何提取器：按星期列头与节次标尺屏幕坐标反推任意 DIV/日历/表格课程卡片（所见即所得）
 * 4) 采集全部同源框架渲染后 HTML 交由多教务解析器兜底（强智/正方/树维 JS/列表/考试）
 */
public class ImportWebActivity extends Activity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_SCHOOL = "school";

    private EditText etUrl;
    private WebView webView;
    private ProgressBar progressBar;
    private Button btnUa;
    private boolean desktopUa = true;

    private final List<String> capturedJson = Collections.synchronizedList(new ArrayList<String>());

    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final String SNIFFER_JS =
            "(function(){"
                    + "function emit(tr, url){"
                    + "  try{"
                    + "    if(url){"
                    + "      var topW = window.top || window;"
                    + "      topW.__qkb_urls = topW.__qkb_urls || [];"
                    + "      if(topW.__qkb_urls.indexOf(url) < 0){"
                    + "        topW.__qkb_urls.push(url);"
                    + "        if(topW.__qkb_urls.length > 60) topW.__qkb_urls.shift();"
                    + "      }"
                    + "    }"
                    + "    var br = window.QkbBridge || (window.top && window.top.QkbBridge);"
                    + "    if(br) br.onJson(tr);"
                    + "  }catch(e){}"
                    + "}"
                    + "function absUrl(win, u){"
                    + "  try{ return new win.URL(u, win.location.href).href; }catch(e){ return String(u||''); }"
                    + "}"
                    + "function hookOne(win){"
                    + "  if(!win) return;"
                    + "  try{"
                    + "    if(!win.__qkb_hooked){"
                    + "      win.__qkb_hooked = true;"
                    + "      var origOpen = win.XMLHttpRequest && win.XMLHttpRequest.prototype.open;"
                    + "      var origSend = win.XMLHttpRequest && win.XMLHttpRequest.prototype.send;"
                    + "      if(origOpen && origSend){"
                    + "        win.XMLHttpRequest.prototype.open = function(m, u){ this.__qkb_u = absUrl(win, u); return origOpen.apply(this, arguments); };"
                    + "        win.XMLHttpRequest.prototype.send = function(){"
                    + "          var self = this;"
                    + "          this.addEventListener('load', function(){"
                    + "            try{"
                    + "              var t = self.responseText;"
                    + "              if(t && t.length > 20 && t.length < 1500000){"
                    + "                var tr = t.trim();"
                    + "                if(tr.charAt(0)==='{' || tr.charAt(0)==='['){ emit(tr, self.__qkb_u); }"
                    + "              }"
                    + "            }catch(e){}"
                    + "          });"
                    + "          return origSend.apply(this, arguments);"
                    + "        };"
                    + "      }"
                    + "      if(win.fetch){"
                    + "        var origFetch = win.fetch;"
                    + "        win.fetch = function(input){"
                    + "          var reqUrl = typeof input === 'string' ? absUrl(win, input) : (input && input.url ? absUrl(win, input.url) : '');"
                    + "          return origFetch.apply(this, arguments).then(function(resp){"
                    + "            try{"
                    + "              resp.clone().text().then(function(t){"
                    + "                if(t && t.length > 20 && t.length < 1500000){"
                    + "                  var tr = t.trim();"
                    + "                  if(tr.charAt(0)==='{' || tr.charAt(0)==='['){ emit(tr, reqUrl); }"
                    + "                }"
                    + "              });"
                    + "            }catch(e){}"
                    + "            return resp;"
                    + "          });"
                    + "        };"
                    + "      }"
                    + "      if(win.MutationObserver && win.document && win.document.documentElement){"
                    + "        var mo = new win.MutationObserver(function(){ walkHook(win, 0); });"
                    + "        mo.observe(win.document.documentElement, {childList:true, subtree:true});"
                    + "      }"
                    + "    }"
                    + "  }catch(e){}"
                    + "}"
                    + "function walkHook(win, depth){"
                    + "  if(!win || depth > 5) return;"
                    + "  hookOne(win);"
                    + "  try{"
                    + "    var doc = win.document;"
                    + "    if(doc){"
                    + "      var ifs = doc.querySelectorAll('iframe, frame');"
                    + "      for(var i=0;i<ifs.length;i++){"
                    + "        var el = ifs[i];"
                    + "        if(!el.__qkb_l){"
                    + "          el.__qkb_l = true;"
                    + "          el.addEventListener('load', function(){ try{ walkHook(this.contentWindow, depth+1); }catch(e){} });"
                    + "        }"
                    + "        try{ if(el.contentWindow) walkHook(el.contentWindow, depth+1); }catch(e){}"
                    + "      }"
                    + "    }"
                    + "    var wf = win.frames || [];"
                    + "    for(var j=0;j<wf.length;j++){"
                    + "      try{ walkHook(wf[j], depth+1); }catch(e){}"
                    + "    }"
                    + "  }catch(e){}"
                    + "}"
                    + "walkHook(window, 0);"
                    + "if(!window.__qkb_timer){ window.__qkb_timer = setInterval(function(){ walkHook(window, 0); }, 250); }"
                    + "})();";

    /**
     * 综合采集脚本：遍历所有同源框架，返回 { htmls: [...], jsons: [...] }
     * 其中 jsons 包含：EasyUI datagrid 运行时行、FullCalendar clientEvents、以及 DOM 视觉几何反推课程。
     */
    private static final String COLLECT_BUNDLE_JS =
            "(function(){"
                    + "var htmls = [];"
                    + "var jsons = [];"
                    + "var seen = [];"
                    + "function norm(s){ return (s||'').replace(/\\u00a0/g,' ').replace(/\\s+/g,' ').trim(); }"
                    + "function parseDow(t){"
                    + "  t = norm(t);"
                    + "  if(!t || t.length > 22) return -1;"
                    + "  var map = {'一':1,'二':2,'三':3,'四':4,'五':5,'六':6,'日':7,'天':7};"
                    + "  var m = t.match(/^(?:星期|周)([一二三四五六日天])(?:\\s*[\\(（]?\\d{1,2}[\\/\\-.]\\d{1,2}[\\)）]?)?$/);"
                    + "  if(m) return map[m[1]];"
                    + "  m = t.match(/^([一二三四五六日天])(?:\\s*[\\(（]?\\d{1,2}[\\/\\-.]\\d{1,2}[\\)）]?)?$/);"
                    + "  if(m) return map[m[1]];"
                    + "  var low = t.toLowerCase();"
                    + "  if(/^mon/.test(low)) return 1; if(/^tue/.test(low)) return 2;"
                    + "  if(/^wed/.test(low)) return 3; if(/^thu/.test(low)) return 4;"
                    + "  if(/^fri/.test(low)) return 5; if(/^sat/.test(low)) return 6;"
                    + "  if(/^sun/.test(low)) return 7;"
                    + "  return -1;"
                    + "}"
                    + "function parseNodeRange(t){"
                    + "  t = norm(t).replace(/\\d{1,2}[:：]\\d{2}\\s*[-~至]?\\s*(?:\\d{1,2}[:：]\\d{2})?/g,' ');"
                    + "  if(!t || t.length > 22) return null;"
                    + "  var m = t.match(/^第?\\s*0?(\\d{1,2})\\s*[-~至,，]\\s*0?(\\d{1,2})\\s*节?$/);"
                    + "  if(m){ var a=+m[1],b=+m[2]; if(a>=1&&b>=a&&b<=16) return [a,b]; }"
                    + "  m = t.match(/^第\\s*0?(\\d{1,2})\\s*节$/);"
                    + "  if(m){ var n=+m[1]; if(n>=1&&n<=16) return [n,n]; }"
                    + "  m = t.match(/^0?(\\d{1,2})\\s*节$/);"
                    + "  if(m){ var n2=+m[1]; if(n2>=1&&n2<=16) return [n2,n2]; }"
                    + "  var cn={'一':1,'二':2,'三':3,'四':4,'五':5,'六':6,'七':7,'八':8,'九':9,'十':10,'十一':11,'十二':12};"
                    + "  m = t.match(/^第([一二三四五六七])大节$/);"
                    + "  if(m && cn[m[1]]){ var k=cn[m[1]]; return [k*2-1,k*2]; }"
                    + "  m = t.match(/^第([一二三四五六七八九十]{1,2})节$/);"
                    + "  if(m && cn[m[1]]) return [cn[m[1]],cn[m[1]]];"
                    + "  return null;"
                    + "}"
                    + "function extractRuntime(win){"
                    + "  try{"
                    + "    var $ = win.jQuery || win.$;"
                    + "    if($){"
                    + "      $('.datagrid-f').each(function(){"
                    + "        try{"
                    + "          var rows = $(this).datagrid('getRows');"
                    + "          if(rows && rows.length > 0) jsons.push(JSON.stringify({rows: rows}));"
                    + "        }catch(e){}"
                    + "      });"
                    + "      if($.fn && $.fn.fullCalendar){"
                    + "        $('.fc').each(function(){"
                    + "          try{"
                    + "            var evs = $(this).fullCalendar('clientEvents');"
                    + "            if(evs && evs.length > 0){"
                    + "              var arr = [];"
                    + "              for(var i=0;i<evs.length;i++){"
                    + "                var ev = evs[i];"
                    + "                arr.push({title: ev.title||'', start: ev.start ? (ev.start.format ? ev.start.format() : String(ev.start)) : '', end: ev.end ? (ev.end.format ? ev.end.format() : String(ev.end)) : ''});"
                    + "              }"
                    + "              jsons.push(JSON.stringify(arr));"
                    + "            }"
                    + "          }catch(e){}"
                    + "        });"
                    + "      }"
                    + "    }"
                    + "  }catch(e){}"
                    + "}"
                    + "function extractVisual(doc){"
                    + "  try{"
                    + "    var all = doc.querySelectorAll('th, td, div, span, li');"
                    + "    var dowCands = [], nodeCands = [];"
                    + "    for(var i=0;i<all.length;i++){"
                    + "      var el = all[i];"
                    + "      if(el.children && el.children.length > 3) continue;"
                    + "      var r = el.getBoundingClientRect();"
                    + "      if(!r || r.width < 14 || r.height < 10 || r.width > 420 || r.height > 120) continue;"
                    + "      var txt = norm(el.innerText || el.textContent);"
                    + "      if(!txt) continue;"
                    + "      var d = parseDow(txt);"
                    + "      if(d > 0){ dowCands.push({el:el, dow:d, l:r.left, r:r.right, cx:(r.left+r.right)/2, t:r.top, b:r.bottom, w:r.width}); continue; }"
                    + "      var nr = parseNodeRange(txt);"
                    + "      if(nr){ nodeCands.push({el:el, s:nr[0], e:nr[1], l:r.left, r:r.right, t:r.top, b:r.bottom, h:r.height}); }"
                    + "    }"
                    + "    var bestCols = [];"
                    + "    for(var i=0;i<dowCands.length;i++){"
                    + "      var base = dowCands[i];"
                    + "      var grp = {};"
                    + "      for(var j=0;j<dowCands.length;j++){"
                    + "        var c = dowCands[j];"
                    + "        if(Math.abs(c.t - base.t) <= 26){"
                    + "          if(!grp[c.dow] || c.w > grp[c.dow].w) grp[c.dow] = c;"
                    + "        }"
                    + "      }"
                    + "      var arr = []; for(var k in grp) arr.push(grp[k]);"
                    + "      if(arr.length > bestCols.length) bestCols = arr;"
                    + "    }"
                    + "    if(bestCols.length < 5) return;"
                    + "    bestCols.sort(function(a,b){ return a.l - b.l; });"
                    + "    var bestRows = [];"
                    + "    for(var i=0;i<nodeCands.length;i++){"
                    + "      var b2 = nodeCands[i];"
                    + "      var rgrp = {};"
                    + "      for(var j=0;j<nodeCands.length;j++){"
                    + "        var n = nodeCands[j];"
                    + "        if(Math.abs(n.l - b2.l) <= 35 && n.t >= bestCols[0].b - 25){"
                    + "          var key = n.s + '-' + n.e;"
                    + "          if(!rgrp[key] || n.h > rgrp[key].h) rgrp[key] = n;"
                    + "        }"
                    + "      }"
                    + "      var rarr = []; for(var k2 in rgrp) rarr.push(rgrp[k2]);"
                    + "      if(rarr.length > bestRows.length) bestRows = rarr;"
                    + "    }"
                    + "    if(bestRows.length < 4) return;"
                    + "    bestRows.sort(function(a,b){ return a.t - b.t; });"
                    + "    var avgColW = 0; for(var i=0;i<bestCols.length;i++) avgColW += bestCols[i].w; avgColW /= bestCols.length;"
                    + "    var avgRowH = 0; for(var i=0;i<bestRows.length;i++) avgRowH += bestRows[i].h; avgRowH /= bestRows.length;"
                    + "    var gL = bestCols[0].l - 8, gR = bestCols[bestCols.length-1].r + 8;"
                    + "    var gT = bestRows[0].t - 8, gB = bestRows[bestRows.length-1].b + 12;"
                    + "    var cards = [];"
                    + "    var pool = doc.querySelectorAll('div, td, a, li');"
                    + "    for(var i=0;i<pool.length;i++){"
                    + "      var el = pool[i];"
                    + "      var r = el.getBoundingClientRect();"
                    + "      if(!r || r.width < avgColW*0.42 || r.width > avgColW*1.28) continue;"
                    + "      if(r.height < avgRowH*0.55 || r.height > avgRowH*6.5) continue;"
                    + "      if(r.left < gL || r.right > gR || r.top < gT || r.bottom > gB) continue;"
                    + "      var raw = (el.innerText || el.textContent || '').trim();"
                    + "      if(raw.length < 2) continue;"
                    + "      if(parseDow(raw) > 0 || parseNodeRange(raw)) continue;"
                    + "      var cx = (r.left + r.right) / 2;"
                    + "      var col = null, bestDist = 99999;"
                    + "      for(var c=0;c<bestCols.length;c++){"
                    + "        var dist = Math.abs(cx - bestCols[c].cx);"
                    + "        if(dist < bestDist && dist <= avgColW*0.65){ bestDist = dist; col = bestCols[c]; }"
                    + "      }"
                    + "      if(!col) continue;"
                    + "      var sNode = 99, eNode = 0;"
                    + "      for(var rr=0;rr<bestRows.length;rr++){"
                    + "        var row = bestRows[rr];"
                    + "        var ov = Math.min(r.bottom, row.b) - Math.max(r.top, row.t);"
                    + "        if(ov >= Math.min(row.h, r.height)*0.32){"
                    + "          if(row.s < sNode) sNode = row.s;"
                    + "          if(row.e > eNode) eNode = row.e;"
                    + "        }"
                    + "      }"
                    + "      if(eNode < sNode) continue;"
                    + "      var lines = [];"
                    + "      var split = raw.split(/[\\r\\n]+/);"
                    + "      for(var li=0;li<split.length;li++){ var ln = norm(split[li]); if(ln) lines.push(ln); }"
                    + "      if(lines.length === 0) continue;"
                    + "      cards.push({el:el, xq:col.dow, ksjc:sNode, jsjc:eNode, lines:lines, sig:lines.join('|'), area:r.width*r.height});"
                    + "    }"
                    + "    var keep = [];"
                    + "    var seenSlot = {};"
                    + "    for(var i=0;i<cards.length;i++){"
                    + "      var a = cards[i], drop = false;"
                    + "      for(var j=0;j<cards.length;j++){"
                    + "        if(i===j) continue;"
                    + "        var b = cards[j];"
                    + "        if(a.el.contains(b.el) && b.sig.length >= a.sig.length * 0.8){ drop = true; break; }"
                    + "        if(b.el.contains(a.el) && a.sig.length < b.sig.length * 0.75){ drop = true; break; }"
                    + "      }"
                    + "      if(drop) continue;"
                    + "      var sk = a.xq+'|'+a.ksjc+'|'+a.jsjc+'|'+a.lines[0];"
                    + "      if(seenSlot[sk]) continue;"
                    + "      seenSlot[sk] = true;"
                    + "      keep.push({xq:a.xq, ksjc:a.ksjc, jsjc:a.jsjc, lines:a.lines});"
                    + "    }"
                    + "    if(keep.length > 0) jsons.push(JSON.stringify({__qkb_visual:true, rows:keep}));"
                    + "  }catch(e){}"
                    + "}"
                    + "var frameUrls = [];"
                    + "var detectedTerms = [];"
                    + "function addTerm(v){ if(v && /^\\d{4,8}$/.test(v) && detectedTerms.indexOf(v)<0) detectedTerms.push(v); }"
                    + "function walkWin(win, depth){"
                    + "  if(!win || depth > 5) return;"
                    + "  try{"
                    + "    var doc = win.document;"
                    + "    if(!doc || seen.indexOf(doc) >= 0) return;"
                    + "    seen.push(doc);"
                    + "    try{ if(win.location && win.location.href) frameUrls.push(win.location.href); }catch(e){}"
                    + "    try{"
                    + "      var sels = doc.querySelectorAll('select[name*=\"xnxq\"], select[id*=\"xnxq\"], input[name*=\"xnxq\"]');"
                    + "      for(var si=0;si<sels.length;si++){ addTerm((sels[si].value||'').trim()); }"
                    + "    }catch(e){}"
                    + "    extractRuntime(win);"
                    + "    extractVisual(doc);"
                    + "    if(doc.documentElement && doc.documentElement.outerHTML){"
                    + "      htmls.push(doc.documentElement.outerHTML);"
                    + "    }"
                    + "    var ifs = doc.querySelectorAll('iframe, frame');"
                    + "    for(var i=0;i<ifs.length;i++){"
                    + "      try{ if(ifs[i].contentWindow) walkWin(ifs[i].contentWindow, depth+1); }catch(e){}"
                    + "    }"
                    + "    var wf = win.frames || [];"
                    + "    for(var j=0;j<wf.length;j++){"
                    + "      try{ walkWin(wf[j], depth+1); }catch(e){}"
                    + "    }"
                    + "  }catch(e){}"
                    + "}"
                    + "walkWin(window, 0);"
                    + "function buildFullTermUrls(){"
                    + "  var cands = [];"
                    + "  function addC(u){ if(u && cands.indexOf(u)<0 && cands.length<6) cands.push(u); }"
                    + "  var allU = ((window.top && window.top.__qkb_urls) || []).concat(frameUrls);"
                    + "  for(var i=0;i<allU.length;i++){"
                    + "    var u = allU[i] || '';"
                    + "    var tm = u.match(/xnxqdm=(\\d{4,8})/i); if(tm) addTerm(tm[1]);"
                    + "  }"
                    + "  var term = detectedTerms.length > 0 ? detectedTerms[0] : '';"
                    + "  for(var i=0;i<allU.length;i++){"
                    + "    var u = allU[i] || '';"
                    + "    if(/[?&](?:zc|week|zhouci|kzc)=\\d+/i.test(u)){"
                    + "      var stripped = u.replace(/([?&])(?:zc|week|zhouci|kzc)=\\d+&?/ig, '$1').replace(/[?&]$/, '');"
                    + "      if(/[?&]rows=\\d+/i.test(stripped)) stripped = stripped.replace(/([?&]rows=)\\d+/i, '$1500');"
                    + "      else if(/getDataList/i.test(stripped)) stripped += (stripped.indexOf('?')>=0?'&':'?') + 'page=1&rows=500';"
                    + "      addC(stripped);"
                    + "    }"
                    + "    var act = u.match(/^(https?:\\/\\/[^?]+\\/)([A-Za-z0-9_]+)!(?:getKbRq|xskbList2?|getXsgrbkList|xsgrkbMain|getBjkbList|bjkbMain|getDataList)\\.action/i);"
                    + "    if(act){"
                    + "      var ep = act[1] + act[2] + '!getDataList.action';"
                    + "      addC(term ? (ep + '?xnxqdm=' + term + '&page=1&rows=500') : (ep + '?page=1&rows=500'));"
                    + "    }"
                    + "  }"
                    + "  if(cands.length === 0){"
                    + "    for(var h=0;h<htmls.length;h++){"
                    + "      var hm = htmls[h].match(/([A-Za-z0-9_]+)!(?:getDataList|xskbList2?|getKbRq)\\.action/i);"
                    + "      if(hm){"
                    + "        var origin = window.location.origin || '';"
                    + "        var ep2 = origin + '/' + hm[1] + '!getDataList.action';"
                    + "        addC(term ? (ep2 + '?xnxqdm=' + term + '&page=1&rows=500') : (ep2 + '?page=1&rows=500'));"
                    + "        break;"
                    + "      }"
                    + "    }"
                    + "  }"
                    + "  return cands;"
                    + "}"
                    + "return JSON.stringify({htmls: htmls, jsons: jsons, expandUrls: buildFullTermUrls()});"
                    + "})();";

    private int dp(float v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String school = getIntent().getStringExtra(EXTRA_SCHOOL);
        setTitle((school == null || school.isEmpty() ? "教务导入" : school) + " · 浏览器导入");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        // 顶部地址与导航栏
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(dp(6), dp(6), dp(6), dp(6));
        top.setBackgroundColor(0xFFF4F6F9);
        top.setGravity(Gravity.CENTER_VERTICAL);

        Button btnBack = miniBtn("‹");
        top.addView(btnBack, new LinearLayout.LayoutParams(dp(36), dp(36)));

        Button btnReload = miniBtn("↻");
        top.addView(btnReload, new LinearLayout.LayoutParams(dp(36), dp(36)));

        etUrl = new EditText(this);
        etUrl.setHint("输入学校教务网址（如 https://jw.example.edu.cn）");
        etUrl.setTextSize(12.5f);
        etUrl.setSingleLine(true);
        etUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        etUrl.setImeOptions(EditorInfo.IME_ACTION_GO);
        String initUrl = getIntent().getStringExtra(EXTRA_URL);
        if (initUrl == null || initUrl.trim().isEmpty()) initUrl = SchoolProfile.customUrl(this);
        etUrl.setText(initUrl);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        top.addView(etUrl, ulp);

        Button btnGo = miniBtn("前往");
        top.addView(btnGo, new LinearLayout.LayoutParams(dp(48), dp(36)));

        btnUa = miniBtn("电脑");
        top.addView(btnUa, new LinearLayout.LayoutParams(dp(48), dp(36)));

        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        TextView tip = new TextView(this);
        tip.setText("步骤：在下方网页登录学校教务 → 打开「个人课表」或「考试安排」→ 点击底部按钮提取");
        tip.setTextSize(11.5f);
        tip.setTextColor(0xFF1D4ED8);
        tip.setBackgroundColor(0xFFEFF6FF);
        tip.setPadding(dp(12), dp(6), dp(12), dp(6));
        root.addView(tip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        WebSettings st = webView.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setDatabaseEnabled(true);
        st.setBuiltInZoomControls(true);
        st.setDisplayZoomControls(false);
        st.setUseWideViewPort(true);
        st.setLoadWithOverviewMode(true);
        st.setSupportMultipleWindows(true);
        st.setJavaScriptCanOpenWindowsAutomatically(true);
        if (Build.VERSION.SDK_INT >= 21) {
            st.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        st.setUserAgentString(DESKTOP_UA);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) {
            cm.setAcceptThirdPartyCookies(webView, true);
        }
        webView.addJavascriptInterface(new Bridge(), "QkbBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                final WebView temp = new WebView(ImportWebActivity.this);
                temp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                        String u = req.getUrl() == null ? "" : req.getUrl().toString();
                        if (u.startsWith("http://") || u.startsWith("https://")) webView.loadUrl(u);
                        temp.destroy();
                        return true;
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, String url) {
                        if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                            webView.loadUrl(url);
                        }
                        temp.destroy();
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(temp);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(ImportWebActivity.this)
                        .setMessage(message)
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                result.confirm();
                            }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface d) {
                                result.cancel();
                            }
                        })
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(ImportWebActivity.this)
                        .setMessage(message)
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                result.confirm();
                            }
                        })
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                result.cancel();
                            }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface d) {
                                result.cancel();
                            }
                        })
                        .show();
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl() == null ? "" : request.getUrl().toString();
                return !(u.startsWith("http://") || u.startsWith("https://"));
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (url != null && url.startsWith("http") && !etUrl.hasFocus()) etUrl.setText(url);
                view.evaluateJavascript(SNIFFER_JS, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && url.startsWith("http") && !etUrl.hasFocus()) etUrl.setText(url);
                view.evaluateJavascript(SNIFFER_JS, null);
            }

            @Override
            public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
                new AlertDialog.Builder(ImportWebActivity.this)
                        .setTitle("证书提示")
                        .setMessage("该校教务网站 SSL 证书未被系统默认信任（高校内网较常见），是否继续访问？")
                        .setPositiveButton("继续访问", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                handler.proceed();
                            }
                        })
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                handler.cancel();
                            }
                        })
                        .show();
            }
        });

        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(dp(10), dp(8), dp(10), dp(10));
        bottom.setBackgroundColor(0xFFFFFFFF);
        bottom.setElevation(dp(4));

        Button btnKb = new Button(this);
        btnKb.setText("一键提取课表");
        btnKb.setTextColor(Color.WHITE);
        btnKb.setBackgroundColor(0xFF2563EB);
        btnKb.setAllCaps(false);
        LinearLayout.LayoutParams blp1 = new LinearLayout.LayoutParams(0, dp(42), 1f);
        blp1.rightMargin = dp(6);
        bottom.addView(btnKb, blp1);

        Button btnExam = new Button(this);
        btnExam.setText("一键提取考试");
        btnExam.setTextColor(0xFF18181B);
        btnExam.setBackgroundColor(0xFFE4E4E7);
        btnExam.setAllCaps(false);
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(0, dp(42), 1f);
        blp2.leftMargin = dp(6);
        bottom.addView(btnExam, blp2);

        root.addView(bottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (webView.canGoBack()) webView.goBack();
                else finish();
            }
        });
        btnReload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webView.reload();
            }
        });
        btnGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadEnteredUrl();
            }
        });
        etUrl.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    loadEnteredUrl();
                    return true;
                }
                return false;
            }
        });
        btnUa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                desktopUa = !desktopUa;
                btnUa.setText(desktopUa ? "电脑" : "手机");
                webView.getSettings().setUserAgentString(desktopUa ? DESKTOP_UA : null);
                webView.reload();
            }
        });
        btnKb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                extractCurrentPage(true);
            }
        });
        btnExam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                extractCurrentPage(false);
            }
        });

        if (initUrl != null && initUrl.length() > 10 && initUrl.startsWith("http")) {
            webView.loadUrl(initUrl);
        }
    }

    private Button miniBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12f);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private void loadEnteredUrl() {
        String u = etUrl.getText().toString().trim();
        if (u.isEmpty()) return;
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        etUrl.setText(u);
        etUrl.clearFocus();
        SchoolProfile.setCustomUrl(this, u);
        capturedJson.clear();
        webView.loadUrl(u);
    }

    private void extractCurrentPage(final boolean isCourse) {
        Toast.makeText(this, isCourse ? "正在分析课表（自动尝试拉取全学期）…" : "正在分析当前页考试…",
                Toast.LENGTH_SHORT).show();
        webView.evaluateJavascript(COLLECT_BUNDLE_JS, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                final List<String> htmls = new ArrayList<>();
                final List<String> jsons = new ArrayList<>();
                final List<String> expandUrls = new ArrayList<>();
                synchronized (capturedJson) {
                    jsons.addAll(capturedJson);
                }
                decodeBundle(value, htmls, jsons, expandUrls);

                if (isCourse && !expandUrls.isEmpty()) {
                    // 利用当前 WebView 的登录 Cookie 在后台线程补拉一次全学期数据（不带单周过滤参数）
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            List<String> extra = fetchExpandUrls(expandUrls);
                            jsons.addAll(extra);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    finishExtract(true, jsons, htmls);
                                }
                            });
                        }
                    }).start();
                } else {
                    finishExtract(isCourse, jsons, htmls);
                }
            }
        });
    }

    /** 复用浏览器当前登录 Cookie，快速补拉全学期 JSON（单条超时自动忽略，不影响兜底结果） */
    private List<String> fetchExpandUrls(List<String> urls) {
        List<String> out = new ArrayList<>();
        CookieManager cm = CookieManager.getInstance();
        for (String u : urls) {
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(3000);
                c.setReadTimeout(4000);
                c.setRequestProperty("User-Agent", DESKTOP_UA);
                c.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01");
                c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
                String cookieHeader = cm.getCookie(u);
                if (cookieHeader != null && !cookieHeader.isEmpty()) {
                    c.setRequestProperty("Cookie", cookieHeader);
                }
                if (c.getResponseCode() == 200) {
                    java.io.InputStream is = c.getInputStream();
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0 && bo.size() < 1500000) bo.write(buf, 0, n);
                    is.close();
                    String body = new String(bo.toByteArray(), "UTF-8").trim();
                    if (body.length() > 20 && (body.startsWith("{") || body.startsWith("["))) {
                        out.add(body);
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return out;
    }

    private void finishExtract(boolean isCourse, List<String> jsons, List<String> htmls) {
        if (isCourse) {
            KbResult res = KbParser.parseUniversal(jsons, htmls);
            if (!res.courses.isEmpty()) {
                int minW = 99, maxW = 0, withWeeks = 0;
                for (Course c : res.courses) {
                    if (c.weeks != null && c.weeks.length > 0) {
                        withWeeks++;
                        for (int w : c.weeks) {
                            if (w < minW) minW = w;
                            if (w > maxW) maxW = w;
                        }
                    }
                }
                boolean isFullTerm = (withWeeks > 0 && maxW > minW);
                String scopeDesc = isFullTerm
                        ? ("已包含全学期周次（第 " + minW + "-" + maxW + " 周），整学期自动随日期切换，无需每周重新导入。")
                        : (withWeeks > 0
                        ? ("当前仅识别到第 " + minW + " 周的数据。建议在网页里点开「个人课表 / 总课表」或把周次选为「全部」再提取一次，即可一次导入全学期。")
                        : ("当前识别为无周次快照（适合当周查看）。如需全学期单双周自动切换，请在网页把周次改为「全部」或切到「列表展示」后再提取。"));

                Prefs.saveCourses(ImportWebActivity.this, res.courses, res.nodeTime);
                Prefs.saveStatus(ImportWebActivity.this, System.currentTimeMillis(),
                        "已导入 " + res.courses.size() + " 门课"
                                + (isFullTerm ? "（全学期 第" + minW + "-" + maxW + "周）" : "（单周视图）"));
                WidgetProvider.updateAll(ImportWebActivity.this);
                showDoneDialog("课表导入成功",
                        "共识别到 " + res.courses.size() + " 门课程，已自动同步到桌面小部件。\n\n"
                                + scopeDesc + "\n\n"
                                + "如需导入考试，可留在本页打开考试安排后再点「一键提取考试」。");
            } else {
                saveFailDiag(jsons, htmls);
                showFailDialog("未识别到课表",
                        "请确认已在网页中点开「我的课表 / 个人课表」并等课表内容渲染完成后再点击提取。\n"
                                + "（已采集 " + jsons.size() + " 段数据、" + htmls.size() + " 个页面框架）\n"
                                + "提示：若页面含「列表展示」按钮，也可点开列表展示后再提取。");
            }
        } else {
            List<Exam> exams = ExamParser.parseUniversal(jsons, htmls);
            if (!exams.isEmpty()) {
                Prefs.saveExams(ImportWebActivity.this, exams);
                WidgetProvider.updateAll(ImportWebActivity.this);
                showDoneDialog("考试导入成功",
                        "共识别到 " + exams.size() + " 场考试，已同步到 App「考试」页与桌面小部件。");
            } else {
                saveFailDiag(jsons, htmls);
                showFailDialog("未识别到考试安排",
                        "请先在网页里打开「考试安排 / 考试信息」页面并等表格加载完，再点「一键提取考试」。\n"
                                + "（已采集 " + jsons.size() + " 段数据、" + htmls.size() + " 个页面框架）");
            }
        }
    }

    private void decodeBundle(String jsVal, List<String> htmls, List<String> jsons, List<String> expandUrls) {
        if (jsVal == null || jsVal.isEmpty() || "null".equals(jsVal)) return;
        try {
            Object outer = Json.parse(jsVal);
            String jsonStr = outer instanceof String ? (String) outer : jsVal;
            Map<String, Object> obj = Json.asMap(Json.parse(jsonStr));
            if (obj == null) return;
            List<Object> hArr = Json.asList(obj.get("htmls"));
            if (hArr != null) {
                for (Object o : hArr) if (o instanceof String) htmls.add((String) o);
            }
            List<Object> jArr = Json.asList(obj.get("jsons"));
            if (jArr != null) {
                for (Object o : jArr) if (o instanceof String) jsons.add((String) o);
            }
            List<Object> eArr = Json.asList(obj.get("expandUrls"));
            if (eArr != null) {
                for (Object o : eArr) if (o instanceof String) expandUrls.add((String) o);
            }
        } catch (Exception ignored) {
        }
    }

    private void saveFailDiag(List<String> jsons, List<String> htmls) {
        StringBuilder sb = new StringBuilder();
        sb.append("importUrl=").append(etUrl.getText()).append('\n');
        sb.append("jsonCount=").append(jsons.size()).append(" htmlCount=").append(htmls.size()).append('\n');
        for (int i = 0; i < Math.min(jsons.size(), 6); i++) {
            String j = jsons.get(i);
            sb.append("--- JSON ").append(i).append(" len=").append(j.length()).append(" ---\n");
            sb.append(j.length() > 600 ? j.substring(0, 600) : j).append('\n');
        }
        Repo.dump(this, "import-last.txt", sb.toString());
    }

    private void showDoneDialog(String title, String msg) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("完成返回", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setResult(RESULT_OK);
                        finish();
                    }
                })
                .setNegativeButton("留在本页继续", null)
                .show();
    }

    private void showFailDialog(String title, String msg) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("知道了", null)
                .show();
    }

    private final class Bridge {
        @JavascriptInterface
        public void onJson(String text) {
            if (text == null || text.isEmpty()) return;
            synchronized (capturedJson) {
                LinkedHashSet<String> set = new LinkedHashSet<>(capturedJson);
                set.remove(text);
                set.add(text);
                capturedJson.clear();
                List<String> tmp = new ArrayList<>(set);
                int start = Math.max(0, tmp.size() - 40);
                for (int i = start; i < tmp.size(); i++) capturedJson.add(tmp.get(i));
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
