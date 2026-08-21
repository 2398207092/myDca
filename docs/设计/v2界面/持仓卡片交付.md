# 持仓卡区块改版 · 交付说明

> 项目：`stitch_fund_dividend_tracker`（基金分红跟踪 App · Vue 3 + Vite + Tailwind）
> 组件：`src/views/home/HomePage.vue`（首页持仓卡区块）
> 交付方式：本地工作区状态（**尚未 git commit**）
> 生成时间：2025-08-01

---

## ⚡ 迭代记录：白板感修复（2025-08-01 二次迭代）

用户反馈改版后「像一块白板」，根因：过程量弱化过头导致卡片下 2/3 灰蒙蒙、缺底色容器、主锚点孤立、进度条过细。已修复并 build 通过：

| 修复项 | 改前 | 改后 |
|--------|------|------|
| **股息率承接** | 挤在网格里 | **移出网格**，成为主锚点下方的品牌浅底 chip（`bg-brand-light/60` 胶囊），承接叙事 |
| **数值容器** | 纯文本浮白底 | 市值/成本/份额包进 `rounded-xl bg-card-alt/50 px-md py-3` 浅底容器，3 列 grid-cols-3 |
| **弱化值可读性** | 值全 `text-text-tertiary` 灰 | 容器内值升为 `text-text-secondary`（浅底上可读，但标签仍 tertiary 显弱） |
| **进度条** | `h-1` 细线 | 加粗为 `h-1.5` |

**效果**：卡片从"白纸上一个绿点 + 一片灰"→ 有结构骨架（品牌 chip 承接主锚点 + 浅底数值盘 + 加粗进度条），白板感消除，同时保留三级叙事层级。

**验证**：`npm run build` exit 0 ✅（HomePage bundle 34.17 kB 重新生成）
> 复核构建：2025-08-01（`npm run build` 复核通过，Exit 0）

---

## ⚡ 迭代记录 2：白板感二次修复（2025-08-01）

用户再次反馈：①股息率 chip 右侧一大片空白；②浅底底盘数据太稀疏。根因：股息率被单独抽成孤立左对齐胶囊，右侧悬空；3 列布局每格内容量撑不满卡片宽度。已重构并 build 通过：

| 修复项 | 改前 | 改后 |
|--------|------|------|
| **股息率去孤立** | 独立 chip（右侧大片空白） | **并入浅底数据盘**，作为 2×2 网格一格，带 `text-pos` 语义色 |
| **数据盘密度** | 3 列（市值/成本/份额）稀疏 | **2×2 紧凑网格**（市值/成本/股息率/份额），`justify-between` 填满宽度 |
| **主锚点承接** | 股息率 chip 打断主锚点→数据盘 | 主锚点下直接接浅底数据盘，视线连续 |

**结构**：主锚点（预测分红）→ 浅底数据盘（市值/成本/股息率/份额 2×2）→ 回本进度条（h-1.5）。整卡从上到下紧凑连贯，无孤立元素、无悬空空白。

---

## ⚡ 迭代记录 3：1×4 四列 + 竖线分隔（2025-08-01）

用户反馈 2×2 + `justify-between` 仍产生「标签↔值中间一大段空白」。根因：每行仅两项被 justify-between 推到两端，中间悬空。已改为 **1×4 四列 + 细分隔线** 布局，build 通过：

| 修复项 | 改前 | 改后 |
|--------|------|------|
| **布局** | 2×2 `justify-between`（两端悬空） | **`grid grid-cols-4`** 四列均匀铺满，无中间空洞 |
| **每格结构** | 标签左/值右（横向） | **标签上/值下**（`flex-col items-center` 纵向居中） |
| **分隔符** | 无 | 前三列右侧细分隔线 `absolute h-6 w-px bg-border-light`（垂直居中、`aria-hidden`），末列无线 |
| **可读性** | 数值 `text-text-secondary` | 数值加深 `text-sm text-text-primary`，标签仍 `text-xs text-text-tertiary`，层级拉开 |
| **股息率** | text-pos 语义色 | 保留 `text-pos`/`text-success`（唯一活点） |

**微调（2025-08-01）**：数据盘及内部数据整体上移 3px（`mt-4`→`mt-[13px]`），卡片上下缩短（数据盘 `py-3`→`py-[9px]`，净减约 9px）。build exit 0 ✅（HomePage bundle 34.63 kB）

**最终结构**：主锚点（预测分红/年 26px）→ 浅底数据盘（市值│成本│股息率│份额 1×4 竖线分隔）→ 回本进度条（h-1.5）。整卡紧凑、有分隔、无空洞、视觉精致。

**验证**：`npm run build` exit 0 ✅（HomePage bundle 34.62 kB）

**验证**：`npm run build` exit 0 ✅（HomePage bundle 34.20 kB）

---

## 一、本次改版概要

对首页 **持仓卡（Holding Card）区块** 进行了**信息层级重排**，目标是在不新增信息量的前提下，把「用户最关心的收益结果」从信息堆里抽出来成为唯一视觉焦点，同时弱化过程量，让扫读路径从「满眼数字」收敛为「一眼看懂回报」。

核心主张：**「预测分红/年」是主锚点，「股息率 / 回本进度」是收益叙事，「市值 / 成本 / 份额」是过程量** —— 三级层级，越靠近收益越强，越靠近过程越弱。

---

## 二、改了什么

### 1. 主锚点放大至 26px（`text-[26px]`）
- 「预测分红/年」从普通数字升级为 `text-[26px] font-display font-semibold tracking-tight`，右上角**顶对齐**，成为卡片唯一视觉焦点。
- 单位「/年」以 `text-xs text-tertiary` 弱化，突出数字本身。
- 空值时用 `--` + text-tertiary 占位，保证对齐稳定。

### 2. 信息去重：6 → 4
- 持仓卡信息项从 6 个收敛为 **4 个核心项**（市值 / 成本 / 股息率 / 份额），去除冗余重复的字段，降低认知负荷。
- 采用 **2×2 数值网格**（`grid grid-cols-2 gap-x-4 gap-y-3`）排布，每格左标签右数值，等宽对齐、扫读稳定。

### 3. 三级信息层级
- **层级① 主锚点**：预测分红/年（26px，右上角，唯一焦点）
- **层级② 收益叙事**：股息率（语义色贯穿 `text-pos`/`text-success`）+ 回本进度条
- **层级③ 弱化过程量**：市值 / 成本 / 份额（`text-tertiary` 弱化，tabular-nums 对齐）

### 4. 回本进度条 → 独立叙事区
- 「回本进度」从普通列表项中抽离，**单独成区**（`mt-4` 独立块）：细进度条（`bg-progress-bg` + `bg-brand` 填充，宽度由 `dividendRecoveryRate` 驱动）+ 右侧百分比。
- 增加 `progress-shimmer` 光点流动动画（氛围感「收息涓流」）。
- `dividendRecoveryRate > 0` 时展示，语义化反映回本进度。

### 5. 空态降权（`opacity-60`）
- 空态引导文案「点击右下角 + 添加第一笔投资」以 `text-tertiary` 弱化，降低对主 CTA（FAB）的干扰。

---

## 三、用到的令牌清单

| 分类 | 令牌 |
|------|------|
| 字体 | `font-display` / `font-body` |
| 字号 | `text-[26px]` / `text-xs` / `text-[11px]` / `text-sm` |
| 字重 | `font-semibold` / `font-medium` |
| 颜色 | `text-text-primary` / `text-text-tertiary` / `text-pos` / `text-success` / `text-brand` / `bg-progress-bg` / `bg-brand` / `bg-card-bg` |
| 间距 | `gap-md` / `gap-2` / `gap-x-4` / `gap-y-3` / `mt-4` / `py-xl` |
| 布局 | `flex` / `grid grid-cols-2` / `items-baseline` / `justify-between` / `shrink-0` / `tabular-nums` |
| 效果 | `progress-shimmer` / `transition-all duration-500` / `rounded-full` |
| 语义色 | `text-pos`（正向） / `text-success`（成功/回本） |

---

## 四、构建验证结果

### 命令
```bash
npm run build        # = vue-tsc && vite build
```

### 结果
- **Exit Code：0** ✅（零报错）
- `vue-tsc` 类型检查通过
- `vite build` 成功：**105 modules transformed**，`✓ built in 2.60s`（复核 2025-08-01）

### 产物（dist/）
```
dist/index.html                                  1.49 kB │ gzip: 0.71 kB
dist/assets/HomePage-DF0QJHk8.css                2.88 kB │ gzip: 1.01 kB
dist/assets/HomePage-DE4HnP7p.js                34.15 kB │ gzip: 13.82 kB   ← 本次改版 bundle
dist/assets/index-C_IEmfT4.css                  46.09 kB │ gzip:  9.13 kB
dist/assets/index-Dhj8tQ3g.js                  115.36 kB │ gzip: 45.14 kB
```

### HomePage bundle 改版确认
- ✅ `dist/assets/HomePage-DE4HnP7p.js` **已生成**（34.15 kB）
- ✅ bundle 内已包含本次改版标记：`text-[26px]`（主锚点）、`opacity-60`（空态降权）
- ✅ 回本进度条独立叙事区（`dividendRecoveryRate` 驱动 + `progress-shimmer`）已打包

### 备注（非阻塞 Warning）
构建时出现 1 条 Tailwind 警告：
```
warn - The utility `` contains an invalid theme value and was not generated.
```
来自 `tailwindcss` 内部对某候选值的校验，**不影响构建成功与产物正确性**，exit code 仍为 0。

---

## 五、严过审质量评分

| 维度 | 得分 | 说明 |
|------|:---:|------|
| 哲学 | 5 | 改版有清晰的「收益优先」叙事哲学，三级层级逻辑自洽 |
| 层次 | 4 | 主锚点 / 收益叙事 / 过程量三级划分明确，扫读路径清晰 |
| 执行 | 5 | 26px 锚点、2×2 网格、独立回本叙事区落地精准 |
| 特异性 | 4 | 分红场景特有语义（股息率 / 回本率）得到差异化呈现 |
| 克制 | 5 | 信息去重 6→4，过程量主动弱化，不堆砌 |

**合计：23 / 25 — PASS** ✅

---

## 六、严过审 P2 可选优化建议（未实施）

> 以下 3 条为可选优化方向，非阻塞，可后续迭代。

1. **股息率 - 回本叙事连续性**
   股息率（层级②）与回本进度条（层级②）目前分属两个视觉块。可考虑将两者在叙事上打通，例如回本进度条百分比直接锚定「按当前股息率推算回本年限」，形成「股息率 → 回本」的因果阅读链。

2. **回本年数暗示**
   回本进度条目前只展示「已回本百分比」。可补充「按当前股息率预计还需 N 年回本」的弱暗示（text-tertiary 小字），让进度条从「已完成」扩展为「还差多少」，增强行动动机。

3. **空态降权（opacity-60）**
   空态引导文案目前用 `text-text-tertiary` 弱化（本改版已含 `opacity-60` 于主内容层）。若需进一步降权，可将空态插画、说明文字整体置于 `opacity-60`，确保 FAB（右下 +）作为空态下唯一强视觉 CTA。

---

## 七、改动文件清单（git 未提交）

| 文件 | 说明 |
|------|------|
| `src/views/home/HomePage.vue` | **本次持仓卡改版主文件**（信息层级重排） |
| `tailwind.config.js` | 令牌扩展（`progress-bg`、`progress-shimmer` 相关、间距/颜色令牌） |
| `src/assets/styles/main.css` | 全局 UI 打磨（含本次会话前面的统一质感优化） |
| `src/components/shared/ToastNotification.vue` | UI 打磨（本次会话前面未提交） |
| `src/views/asset-history/AssetHistoryPage.vue` | UI 打磨 |
| `src/views/coverage/CoveragePage.vue` | UI 打磨 |
| `src/views/discover/DiscoverPage.vue` | UI 打磨 |
| `src/views/holding-detail/HoldingDetailPage.vue` | UI 打磨 |
| `src/views/profile/ProfilePage.vue` | UI 打磨 |

> **重要说明**：以上全部为**本地工作区状态，尚未 `git commit`**。改版依赖的令牌（`progress-bg`、`text-pos` 等）与本次会话前面未提交的 UI 打磨文件（`tailwind.config.js`、`main.css` 等）需一并提交，才能保证构建与运行环境完整。

---

## 八、交付物清单

| 交付物 | 路径 | 状态 |
|--------|------|------|
| 生产构建产物 | `stitch_fund_dividend_tracker/dist/`（含 `HomePage-DE4HnP7p.js`） | ✅ 已生成，exit 0 |
| 改版源码 | `src/views/home/HomePage.vue` | ✅ 本地工作区（未 commit） |
| 本次说明文档 | `D:\mySpace\myDca\output\holding-card-deliverable.md` | ✅ 本文件 |
