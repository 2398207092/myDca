---
name: "frontend-verify"
description: "前端改造后的全页面自动化验证。仅在三类场景触发：(1) 用户明确要求（说\"验证页面\"/\"测试前端\"/\"翻一翻页面\"等），(2) 部署到服务器/GitHub 前的提醒建议，(3) 较大前端改造完成后建议执行。小改动（单文件 CSS/文案微调）不触发，避免浪费 tokens。"
---

# 前端全页面验证

本项目（基金分红追踪器）专用的前端验证流程。改造完成后用浏览器自动登录并遍历所有核心页面，检查渲染是否正常，避免把问题留给用户。

## 触发条件（必须克制，避免浪费 tokens）

**执行场景：**
1. **用户明确要求**：用户说"验证页面"、"测试前端"、"翻一翻页面"、"自己试一试"等
2. **部署前提醒**：用户要求"部署到服务器/GitHub"时，先建议执行本验证，确认无误再部署
3. **大改造后建议**：涉及多文件、路由结构、组件生命周期、状态管理的改动完成后，主动建议执行

**不触发场景：**
- 单文件 CSS 微调、文案修改、注释增删
- 仅后端改动
- 用户明确表示不需要验证

## 前置检查

执行验证前必须确认环境就绪：

1. **后端运行检查**：`curl.exe -s http://localhost:8080/api/auth/token` 应返回 token
2. **前端运行检查**：访问 `http://localhost:5173/`，若被占用尝试 `5174`
3. 若任一服务未运行，先提示用户启动，不强行验证

## 工具优先级

**优先使用 agent-browser**（命令更丰富，支持 snapshot/network/console/viewport/截图 diff）：

```powershell
npx agent-browser open http://localhost:5173/
npx agent-browser snapshot -i
npx agent-browser network requests --type xhr,fetch
npx agent-browser set viewport 390 844   # 移动端视口（本项目是手机 APP）
```

**降级方案**：若 `agent-browser` 不可用，使用 Chrome DevTools MCP：
- `navigate_page` 导航
- `take_snapshot` 检查元素
- `list_network_requests` 排查接口

**关键：本项目是移动端 APP，必须先用 `set viewport 390 844` 切换到手机视口再跑所有布局断言。** 桌面视口下横向溢出、顶部/底部遮挡等问题测不出来。

## 验证 Checklist

## 执行模式（成本分级，控制 tokens）

| 模式 | 触发场景 | 执行的步骤 |
|------|---------|-----------|
| **快检** | 单文件 CSS/文案微调、小改动 | 1 + 2 + 3 + 6（必做项，~6 次 eval） |
| **全检** | 大改造完成、部署前 | 快检 + 4 + 5 + 1.5（含截图/空状态/基线） |

步骤成本标注：**必做·低**（每次必跑，eval 输出紧凑）/ **可选·中**（按需跑，含截图或接口拦截）/ **按需·高**（仅异常时跑）。

### 步骤 1：自动登录（必做·低）

```powershell
# 获取 token（注意：接口返回 JSON，必须解析出 data.token，不能直接用响应体）
$resp = curl.exe -s http://localhost:8080/api/auth/token
$token = ($resp | ConvertFrom-Json).data.token
# 切换到移动端视口（本项目是手机 APP）
npx agent-browser set viewport 390 844
# 打开页面并注入 token
npx agent-browser open http://localhost:5173/
npx agent-browser wait 1500
npx agent-browser eval "localStorage.setItem('fund_tracker_token', '$token'); location.hash='#/';"
npx agent-browser wait 2500
# 验证 token 已写入且未跳回登录页
npx agent-browser eval "'token_len=' + (localStorage.getItem('fund_tracker_token')||'').length + ' url=' + location.href"
```

若 `token_len` 为 0 或 url 仍是 `/login`，说明 token 提取失败或后端异常，先排查再继续。

### 步骤 1.5：建立回归基线（可选·中）

验证前先给核心页截图存档，验证后再截一次，用 `diff screenshot` 对比，发现"这次改坏了什么"：

```powershell
# 首次：截图存为基线
npx agent-browser navigate http://localhost:5173/#/
npx agent-browser wait 2500
npx agent-browser screenshot baseline-home.png
# 验证完成后再次截图，与基线对比
npx agent-browser screenshot current-home.png
npx agent-browser diff screenshot --baseline baseline-home.png current-home.png
```

> 基线图片存到项目 `page-stack/verify-baseline/` 目录。若返回像素差异巨大，检查是否为预期改动；非预期差异 → 回归问题。
>
> **注意**：基线截图必须在相同视口尺寸下拍摄，否则 diff 无意义。

### 步骤 2：遍历核心路由（必做·低，7 个）

逐个导航到以下页面，每个页面等待 2-3 秒让 API 完成，再检查关键元素是否存在。**快检模式下用轻量 eval 替代 `snapshot -i`**（可访问性树输出大、tokens 贵），只在异常时才用 `snapshot -i` 定位：

| 路由 | 页面 | 关键元素检查点 |
|------|------|---------------|
| `/` | 首页 | 标题"种树"、更新日志、Hero 卡片、分红覆盖、持仓列表、FAB 按钮、底部导航 |
| `/calendar` | 日历页 | 标题"分红日历"、日历格子(1-31)、月份切换、年度总览 tab、更新分红数据按钮 |
| `/discover` | 发现页 | 标题"资产概览"、资产历史、本周/月/年变动、持仓占比、各类资产、添加手动资产 |
| `/profile` | 我的页 | 用户信息、Pro 标签、货币切换、数据口径、数据库备份、退出登录 |
| `/holding/add` | 添加标的 | 搜索框、持仓信息表单、成本算法按钮(3个)、确认添加按钮 |
| `/metrics/settings` | 指标设置 | 实时预览、指标列表、toggle、6项限制(第7项起 disabled) |
| `/coverage` | 分红覆盖 | 标题、空状态或类目列表、去设置按钮 |

轻量元素检查模板（每页一次，替代 snapshot）：

```powershell
npx agent-browser eval "JSON.stringify({h1: !!document.querySelector('h1, h2'), main: !!document.querySelector('main'), kids: (document.querySelector('main')||{children:{length:0}}).children.length})"
```

### 步骤 3：视觉样式断言（必做·低，单次 eval 全量检查）

DOM 元素存在 ≠ 视觉正确。本步骤用一个合并脚本跑完全部视觉断言（计算样式、布局完整性、间距节奏、嵌套容器、页面结构边界），每页**只执行一次 eval**，返回一个 JSON。

**设计系统间距档位**（`tailwind.config.js`，判定基准）：

| 档位 | 值 | 典型用途 |
|------|-----|---------|
| xs | 4px | 图标与文字间隙 |
| sm | 8px | 列表内紧凑分组 |
| md | 12px | 卡片内元素间距 |
| lg | 16px | 卡片内区块间距 |
| xl | 20px | 区块内大间距 |
| section | 24px | **页面 section 之间（main 直接子元素）** |
| gutter | 16px | 页面左右留白 |

基准：main 直接子元素间距应统一 **section(24px)**；卡片列表项 ≥ **md(12px)**。

```powershell
npx agent-browser eval "(function(){
  var out = {issues: []};
  var main = document.querySelector('main');
  if (!main) return JSON.stringify({err: 'NO main'});
  var header = document.querySelector('header');
  var nav = document.querySelector('nav');
  var mainCs = getComputedStyle(main);
  var vw = window.innerWidth;
  var kids = Array.prototype.slice.call(main.children).filter(function(k){
    var cs = getComputedStyle(k);
    return cs.position !== 'fixed' && cs.position !== 'absolute' && k.getBoundingClientRect().height > 0;
  });
  var iss = out.issues;
  var hasSpaceYMain = /space-y-/.test(main.className || '');

  // 3.1 计算样式：仅当 main 用 space-y-* 时子元素 mt=0 才算类未生效；带 padding 类的元素才查 padding
  kids.forEach(function(k, i) {
    var cs = getComputedStyle(k);
    if (hasSpaceYMain && i > 0 && parseFloat(cs.marginTop) === 0) iss.push('kid#' + i + ' mt=0');
    // 注意 \b 单词边界：避免匹配 gap-sm / grid-cols-3 中的 "p-s" 子串
    if (/\bp-[a-z0-9]/.test(k.className||'') && (parseFloat(cs.paddingTop) === 0 || parseFloat(cs.paddingBottom) === 0)) iss.push('kid#' + i + ' pad_cls_dead');
  });

  // 3.2 布局完整性：相邻重叠 + 视口右溢出
  for (var i = 1; i < kids.length; i++) {
    var a = kids[i-1].getBoundingClientRect(), b = kids[i].getBoundingClientRect();
    if (b.top - a.bottom < -1) iss.push('overlap@' + i);
    if (b.right > vw + 1) iss.push('overflow@' + i);
  }

  // 3.3 间距节奏：main 子元素 gap 一致性 + mb/mt 实现方式（居中页跳过）
  var isCentered = mainCs.justifyContent === 'center' && mainCs.alignItems === 'center';
  if (kids.length >= 3 && !isCentered) {
    var gaps = [];
    for (var i = 1; i < kids.length; i++) {
      gaps.push(Math.round(kids[i].getBoundingClientRect().top - kids[i-1].getBoundingClientRect().bottom));
    }
    var pos = gaps.filter(function(g){return g > 0});
    if (pos.length) {
      var min = Math.min.apply(null, pos), max = Math.max.apply(null, pos);
      if (max - min > 8) iss.push('gap_var=' + (max-min) + 'px(' + min + '~' + max + ')');
      if (gaps.filter(function(g){return g === 0}).length) iss.push('zero_gap');
    }
    var hasSpaceY = hasSpaceYMain;
    var mb = 0, mt = 0, none = 0;
    kids.forEach(function(k){
      var c = k.className || '';
      if (/mb-/.test(c)) mb++;
      if (/mt-/.test(c)) mt++;
      if (!/mb-|mt-/.test(c) && !hasSpaceY) none++;
    });
    if (!hasSpaceY && mb > 0 && mt > 0) iss.push('mixed_margin m' + mb + 't' + mt);
    if (!hasSpaceY && none > 0) iss.push('no_spacing_cls=' + none);
    if (hasSpaceY && mb + mt > 0) iss.push('spacey+margin');
  }

  // 3.4 嵌套 space-y 容器：卡片间距 ≥ md(12px)
  var all = main.getElementsByTagName('*');
  var seen = {};
  for (var i = 0; i < all.length; i++) {
    var el = all[i], cls = el.className || '';
    if (typeof cls !== 'string' || !/space-y-/.test(cls)) continue;
    var key = (cls.match(/space-y-([a-z0-9]+)/) || [])[1];
    if (!key || seen[key]) continue;
    seen[key] = true;
    var ckids = Array.prototype.slice.call(el.children).filter(function(k){return k.getBoundingClientRect().height > 0});
    if (ckids.length < 2) continue;
    var cg = [];
    for (var j = 1; j < ckids.length; j++) {
      cg.push(Math.round(ckids[j].getBoundingClientRect().top - ckids[j-1].getBoundingClientRect().bottom));
    }
    var cmin = Math.min.apply(null, cg);
    var isCard = ckids.some(function(k){return /card|rounded/.test(k.className||'')});
    if (isCard && cmin < 12) iss.push('card_gap<' + key + '=' + cmin + 'px');
  }

  // 3.6 页面结构边界：header↔main↔bottomNav
  out.mainPaddingTop = Math.round(parseFloat(mainCs.paddingTop));
  out.mainPaddingBottom = Math.round(parseFloat(mainCs.paddingBottom));
  if (isCentered) {
    // 居中页：以 main 自身中心为参照（不能用 window.innerHeight，main 可能只占部分视口）
    var f = kids[0], l = kids[kids.length-1], mr = main.getBoundingClientRect();
    if (f && l) {
      var cTop = f.getBoundingClientRect().top, cBottom = l.getBoundingClientRect().bottom;
      var off = Math.round((cTop + cBottom - (mr.top + mr.bottom)) / 2);
      if (Math.abs(off) > 40) iss.push('center_off=' + off);
    }
  } else {
    out.headerGap = header && kids[0] ? Math.round(kids[0].getBoundingClientRect().top - header.getBoundingClientRect().bottom) : null;
    if (out.headerGap !== null && out.headerGap < 8) iss.push('header_gap=' + out.headerGap);
  }
  if (nav) {
    var navH = Math.round(nav.getBoundingClientRect().height);
    if (out.mainPaddingBottom < navH + 8) iss.push('bottom_pad=' + out.mainPaddingBottom + '<' + (navH+8));
  }

  out.kids = kids.length;
  return JSON.stringify(out);
})()"
```

判定：`issues` 数组非空 → 视觉异常。问题代码含义：
- `header_gap=N`：内容紧贴顶部导航（应≥8px），main 的 `pt-14`(56px) 恰好等于 header 高 → 改 `pt-16`(64px)
- `bottom_pad`：main paddingBottom < BottomNav 高+8，滚动到底被遮挡 → 加大 paddingBottom
- `card_gap<key=N`：卡片列表间距 <12px 低于设计档位 → 改 `space-y-md`
- `mixed_margin` / `no_spacing_cls` / `spacey+margin`：间距实现方式不统一 → 统一用 `space-y-*`
- `zero_gap` / `gap_var`：子元素间距 0 或波动 >8px → 节奏不统一
- `overlap@i` / `overflow@i`：元素重叠或溢出视口
- `kid#i mt=0`：main 用 space-y-* 但子元素 marginTop=0 → 类未生效（结合专项 3.5 检查 CSS 是否生成）
- `pad_cls_dead`：元素带 p-* padding 类但计算样式为 0 → padding 类未生效
- `center_off`：居中空状态页内容偏移可视区中心 >40px

**3.5 专项检查（按需·中）** —— 仅当上述 `issues` 出现 `mt=0`（怀疑 Tailwind 类未生成）或需要排查字号/圆角时执行，不要默认跑：

| 检查项 | JS 表达式 | 异常表现 |
|--------|----------|---------|
| space-y-* 类是否生成 CSS | 遍历 styleSheets 找 `.space-y-<key>` 选择器 | 类名存在但无对应规则 → Tailwind 配置改了未重启 Vite |
| 文字最小字号 | 关键文字 `getComputedStyle(el).fontSize` < 12px | 可读性问题 |
| 卡片圆角一致性 | 同类卡片 `borderRadius` 差异 | 视觉风格不统一 |

```powershell
npx agent-browser eval "(function(){
  var sheets = document.styleSheets, found = {};
  ['space-y-section','space-y-md','space-y-sm'].forEach(function(cls){
    found[cls] = 'NO';
    for (var i=0;i<sheets.length;i++){try{
      var rules = sheets[i].cssRules;
      for (var j=0;j<rules.length;j++){
        if ((rules[j].selectorText||'').indexOf(cls)>=0){found[cls]='YES';break}
      }
    }catch(e){}}
  });
  return JSON.stringify(found);
})()"
```

### 步骤 4：核心页截图视觉判断（可选·中，仅 3 页）

对**首页 `/`、发现页 `/discover`、我的页 `/profile`** 截图，让 AI 视觉模型判断。截图保存到 `page-stack/verify-screens/` 目录：

```powershell
npx agent-browser navigate http://localhost:5173/#/
npx agent-browser wait 2500
npx agent-browser screenshot page-home.png
npx agent-browser navigate http://localhost:5173/#/discover
npx agent-browser wait 2500
npx agent-browser screenshot page-discover.png
npx agent-browser navigate http://localhost:5173/#/profile
npx agent-browser wait 2500
npx agent-browser screenshot page-profile.png
```

> **语法注意**：截图命令是 `screenshot <路径>`（位置参数），**不是** `--path`。可用 `--full` 截整页。

**视觉判断能力检查（关键降级逻辑）**：
1. 先尝试用 Read 工具读取一张截图（如 `page-home.png`）
2. **若当前模型能读图** → 对照下方视觉判断清单逐项检查 3 张截图
3. **若模型读不了图**（报 "cannot read image"）→ **跳过视觉判断**，在结果汇总注明"⚠ 截图已保存，请人工查看"，**不要假装检查过**——这是硬约束，避免虚假通过

**视觉判断清单**（针对每张截图）：
- **间距均匀性**：相邻卡片/section 间距是否一致，无紧挨、无过大空白
- **视觉层级**：标题/正文/辅助文字字号、字重区分度是否清晰
- **对齐一致性**：左对齐/居中对齐是否统一，无明显错位
- **留白节奏**：页面上下是否有呼吸感，未被内容塞满
- **异常信号**：截断、溢出、错位、空白块、重复元素

> 为何只截 3 页：截图 + 视觉分析 tokens 消耗是计算样式的 10 倍以上。这 3 个页面覆盖了卡片列表、Hero、表单、设置项等核心布局模式，性价比最高。其他页由步骤3的计算样式/布局断言兜底。

### 步骤 5：空/加载状态专项（可选·中）

页面在**无数据时**（empty 状态）和**加载中**（loading 状态）的布局，是日常真实可见的状态，但默认验证只在"数据齐全"下跑。专项检查：

1. **空状态**：对依赖后端数据的页面（首页持仓、分红覆盖、日历），在无数据时检查空状态是否显示正常、间距是否合理。方法：临时用 `network route` 拦截接口返回空数据，或直接看当前是否本就无数据。
2. **加载状态**：刷新页面瞬间（loading 骨架屏阶段）截图，检查骨架屏布局。

```powershell
# 拦截持仓接口返回空数组，测空状态
npx agent-browser network route "/api/holdings" --body '{"code":200,"data":[]}'
npx agent-browser reload
npx agent-browser wait 2000
npx agent-browser snapshot -i  # 检查空状态文案/引导按钮是否显示
npx agent-browser network unroute "/api/holdings"  # 还原
```

> 空状态检查重点是：**空状态组件自身是否紧贴上下元素**（同样适用 3.6 的间距断言）。若空状态页面布局异常，记录问题。

### 步骤 6：二级页面返回测试（必做·低）

从任一二级页面点击返回按钮，确认能正常返回上一页。

### 步骤 7：异常排查（按需·高）

发现页面异常（白屏、加载失败、元素缺失、视觉异常）时：

1. **检查 DOM**：`npx agent-browser eval "document.querySelector('main') ? document.querySelector('main').innerHTML.substring(0, 500) : 'NO MAIN'"`
2. **检查网络请求**：`npx agent-browser network requests --type xhr,fetch`，定位失败的接口
3. **检查 console**：`npx agent-browser list_console_messages`
4. **视觉异常专项**：
   - 顶部/底部紧贴导航 → 用步骤 3.6 的页面结构断言确认，修复 main 的 paddingTop/paddingBottom
   - 间距塌陷 → 检查 Tailwind 工具类是否生成 CSS（见步骤 3.5）
   - 元素重叠 → 检查 `position/flex/grid` 布局是否冲突
   - 截图异常 → 对照步骤 4 的视觉判断清单逐项核对（若模型读不了图，交人工查看）
5. 定位根因后修复，修复后重新验证该页面

## 结果汇总

验证完成后用表格形式汇报：

```
| 页面 | 路由 | DOM | 视觉 | 结构边界 | 空状态 | 截图 | 备注 |
|------|------|-----|------|---------|--------|------|------|
| 首页 | / | ✓ | ✓ | ✗ headerGap=0 | ✓ | ✓ | 已修或待修 |
| 日历 | /calendar | ✓ | ✓ | ✓ | — | — | ... |
| 发现 | /discover | ✓ | ✓ | ✓ | ✓ | ✓ | ... |
| 我的 | /profile | ✓ | ✓ | ✗ headerGap=0 | ✓ | ✓ | 已修或待修 |
| 添加标的 | /holding/add | ✓ | ✓ | ✓ | — | — | ... |
| 指标设置 | /metrics/settings | ✓ | ✓ | ✓ | — | — | ... |
| 分红覆盖 | /coverage | ✓ | ✓ | ✓ | — | — | ... |
```

列含义：
- **DOM**：步骤2关键元素存在性
- **视觉**：步骤3计算样式 + 布局 + 间距断言
- **结构边界**：步骤 3.6 页面结构断言（header↔main↔bottomNav）
- **空状态**：步骤5空/加载状态专项
- **截图**：步骤4视觉模型判断（仅 3 页，模型读不了图时标注"⚠ 请人工查看"）

全绿才能进入部署流程。存在异常时先修复，修复后重新验证。

## 常见问题

- **登录失败/token 失效**：重新执行步骤1的 token 提取，注意必须用 `ConvertFrom-Json` 解析 `data.token`，不能直接用响应体
- **页面跳转白屏**：检查是否 `<transition>` 与 `<KeepAlive>` 嵌套冲突（本项目已知坑）
- **日历页加载失败**：`syncAllEvents` 返回 403 不应阻塞页面，检查 `Promise.all` 是否用 `.catch(() => {})` 容错
- **内容紧贴顶部/底部导航（headerGap=0）**：main 的 `pt-14`（56px）恰好等于 AppHeader 高度 → 内容被推到 header 底边无间距。修复：`pt-16`（64px）留 8px 呼吸空间
- **间距塌陷（main 子元素 marginTop=0）**：`tailwind.config.js` 改了 spacing key 后未重启 Vite dev server，Tailwind PostCSS 插件 dev 模式不热重载配置 → `space-y-*` 类的 CSS 未生成。修复：重启 Vite
- **space-y-* 类名存在但无 CSS 规则**：用步骤 3.5 的 styleSheets 检查模板确认，若有类名无规则即为 Tailwind 配置未重新加载
- **agent-browser 命令找不到**：用 `npx agent-browser` 调用
- **截图命令报错 `Element not found: --path`**：截图用位置参数 `screenshot 文件名.png`，不是 `--path`
- **PowerShell 参数问题**：`@e1` 这类 ref 用单引号包裹 `'@e1'`，避免被解析为变量
- **eval 中变量重复声明**：用 IIFE `(function(){...})()` 包裹，避免多次调用时变量名冲突
- **当前模型读不了截图**：视觉判断步骤降级——保存截图、在汇总注明"请人工查看"，不得假装通过
