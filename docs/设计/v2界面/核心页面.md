# C. P0 页最小改动清单（可复用映射）

> **修订记录：v2，修正 P0-1 兼容别名、P0-2 间距单位歧义，采纳 toast/num 双源优化**
>
> 目标：首页看板 HomePage、分红日历 CalendarPage、持仓详情 HoldingDetailPage、资产发现 DiscoverPage。
> 本清单**不逐行改每个组件**，给出「旧令牌 → 新令牌」「旧类名 → 新类名」的统一映射，按映射替换即可，所有页面共用同一套规则。

---

## 一、全局映射表（所有 P0 页通用）

### 1. 颜色令牌替换

> **P0-1 说明**：`brand`/`alert`/`pos`/`neg`/`gold`/`flat` 已从字符串改为对象 `{DEFAULT, strong, soft, light}`。
> `bg-brand` 兼容（走 DEFAULT）；`bg-brand-light` 等旧 `*-light` 类名由 `light` 兼容别名承接，**不再静默失效**。
> `pos` 继承 `brand` 色值，仅语义别名；分红金统一叫 `gold`（不再用 `amber`，避免与 Tailwind 默认 amber 冲突）。

| 旧（搜索替换） | 新 | 应用位置 |
|----------------|----|----------|
| `text-tertiary` / `--color-text-tertiary` | `#6F6F6E`（改色） | 辅助说明文字、单位、空状态 |
| `#C25A3E`（旧 alert） | `#B84E33` | 红色警示按钮、错误提示 |
| `warning`（正文里大面积使用） | 仅限**图标 / 大字**；正文改为 `#6F6F6E` 或 `#B84E33` | 警示提示条 |
| **`bg-brand-light` / `text-brand-light`** | **`bg-brand-soft` / `text-brand-soft`**（= `light` 别名） | 品牌浅色底、链接浅色字 |
| 手动写死的绿色（如 `#2E7D32` / `green-600`） | `pos` 组：DEFAULT `#1A6B56` / strong `#0F4F40` / soft `#E8F5F0` | 正收益数字、涨跌标签底 |
| 手动写死的红色（如 `#D32F2F` / `red-600`） | `neg` 组：`#BA1A1A` / strong `#A61616` / soft `#FBEAE7` | 负收益数字、跌标签底 |
| 手动写死的金色（分红/黄色） | `gold` 组：`#8A6B08` / strong `#6B5306` / soft `#F7F0DC` | 分红金、日历分红标记 |
| 手动写死的灰（平盘/中性） | `flat` 组：`#6B6A68` / soft `#EFEFEE` | 平盘、禁用底、空状态底 |
| 纯黑文本 `#000` / `black` | `text-primary` `#232221` | 主标题 |
| 次灰 `#757575` / `gray-600` | `text-secondary` `#5F5E5C` | 二级文本 |

### 2. 间距替换（8pt 网格）

> **P0-2 关键提醒（务必先读）**：
> Tailwind 默认 spacing 里 `16`=4rem=**64px**、`24`=96px，**不是** 16px/24px。
> 若把这里的 16/24 当 spacing 数字写 `p-16`/`p-24` 会画出 **4 倍级错误布局**。
> 因此下方给出**代码级映射**：旧 `p-md`(12px)→`p-4`（1rem=16px）、旧 `p-xl`(20px)→`p-6`（1.5rem=24px）；
> 语义间距统一走 `gutter`(1rem=16px)/`section`(2rem=32px)，**不要直接写 `p-16`/`p-24`**。

| 旧（px 语义） | 代码级新值 | Tailwind 写法 | 应用位置 |
|---------------|-----------|---------------|----------|
| `md` = 12px | 16px | `p-4`（1rem） | 卡片内间距、元素间间距 |
| `xl` = 20px | 24px | `p-6`（1.5rem） | 大区块内距、列表上下距 |
| `section` 24px | 32px | `py-8`（2rem）或 `.section-y` | 页面纵向 section 之间 |
| 页面横向 padding | 16px | `.gutter-x`（1rem） | 13 页统一左右留白 |
| 页面纵向 section | 32px | `.section-y`（2rem） | 统一段间距 |

> **13 页统一规则：横 gutter16 + 纵 section32。** 页面容器直接用 `.gutter-x` + `.section-y` 类，不手写 `p-16`/`p-24`。
> 若用工具类，严格用 `p-4`(16px) / `p-6`(24px) / `py-8`(32px)。

### 3. 数字排版

> 数字层级**单一数据源**在 B 文档 `.num-l*` 组件类（v2 已从 A 文档 fontSize 移出），本表沿用即可。

| 旧 | 新 | 应用位置 |
|----|----|----------|
| 内联 `font-size: 30px` 的大额数字 | `class="num num-l1"` | 累计收益、总市值 |
| 中额（24px）数字 | `class="num num-l2"` | 单笔金额、持仓成本 |
| 常规（18px）数字 | `class="num num-l3"` | 明细行数值 |
| 单位 / 辅助（14px） | `class="num num-l4"` | "元 / 份 / %" 单位后缀 |
| 任意纯数字 | 至少加 `class="num"` | 确保 tabular-nums 等宽对齐 |

> 涨跌数值叠加配色：正 `num + t-pos`、负 `num + t-neg`、平 `num + t-flat`、分红 `num + t-gold`。

### 4. 组件类替换

| 旧类 / 手写样式 | 新类 | 应用位置 |
|----------------|------|----------|
| 手写圆角+边框+阴影的卡片 | `class="card"` | 各信息卡 |
| 手写涨跌标签（小 pill） | `chip chip-pos` / `chip chip-neg` / `chip chip-gold` / `chip chip-flat` | 涨跌、分红标记 |
| 手写金额高亮块 | `amount-hl pos` / `neg` / `flat` | 头部关键数值 |
| 手写 `button` 不同样式 | `btn btn-primary` / `btn-secondary` / `btn-ghost` | 所有按钮 |
| 手写列表行 | `list-item` | 持仓列表、明细列表 |
| 手写 input | `input` | 所有输入框 |
| 手写空状态 | `empty` + `empty-icon` | 无数据占位 |
| 手写 toast | `toast toast-success` / `toast-error` / `toast-info` | 操作反馈 |

### 5. 动效

- 所有 hover 过渡用 `transition-colors` 或 B 文档 `.btn`/`.list-item` 内置 120–180ms；
- 面板/列表入场用 `animate-fade-up`；Toast 用 `animate-toast-in`；
- 列表交错用 stagger 间隔（每项 `transition-delay: 40ms * index`）；
- 无需改动代码，`prefers-reduced-motion` 兜底已在 B 文档生效。

---

## 二、分页重点应用点

### 1. HomePage.vue（首页看板）— 优先
- **头部累计收益**：改为 `num num-l1 amount-hl pos/neg`（数值） + `num num-l4`（单位"元"）
- **总资产 / 今日收益卡**：统一 `.card` + `.card-pad-lg`，间距改 `p-4`(16) / `p-6`(24)
- **持仓概览列表**：每行 `.list-item`，涨跌标签改 `.chip-*`
- **空状态**（无持仓）→ `.empty`
- 验证 **tertiary 文本改色后对比度**是否达标

### 2. CalendarPage.vue（分红日历）
- **分红日期标记**：用 `gold` 语义色（`chip-gold`），不再用泛黄 / `amber`
- **金额**：`num num-l2/num-l3` + `t-gold`
- **月份切换按钮**：统一 `btn-secondary`
- **空日历**：`.empty`

### 3. HoldingDetailPage.vue（持仓详情）
- **持仓成本 / 市值 / 收益三区**：统一 `num` 层级 + 涨跌配色（pos/neg/flat）
- **明细表金额列**：`num` 等宽对齐
- **买入/卖出按钮**：`btn-primary` / `btn-secondary`
- **toast**（操作后反馈）：`.toast`（成功/失败，无左侧色条）
- 首屏关键数值是 **P0 验收点**，务必用 `amount-hl`

### 4. DiscoverPage.vue（资产发现）
- **搜索结果卡**：`.card` + `.list-item`
- **收益率标签**：`chip-pos` / `chip-neg` / `chip-flat`
- **搜索输入**：`.input`
- **空结果**：`.empty`

---

## 三、验收清单（每页提交前核对）

- [ ] 无 `#A09E9B` 残留（tertiary 已改 #6F6F6E）
- [ ] 无 `#C25A3E` 残留（alert 已改 #B84E33）
- [ ] 无 `bg-brand-light` / `text-brand-light` 残留（已改 `brand-soft`）
- [ ] 无 `amber` 残留（已改 `gold`）
- [ ] 无 `p-16` / `p-24` 滥用（间距严格用 `p-4` / `p-6` / `.gutter-x` / `.section-y`）
- [ ] 所有金额带 `num` 等宽、涨跌配色正确（pos/neg/gold/flat）
- [ ] 卡片 / 按钮 / 标签 / 列表已统一组件类
- [ ] 横间距 gutter16、纵 section32 一致
- [ ] 无大面积 warning 色作正文
- [ ] toast 无左侧彩色竖条（纯浅底 + 深色文字）
- [ ] 无编造统计数字；数据用 `—` 占位
- [ ] `prefers-reduced-motion` 生效（减弱动效）
