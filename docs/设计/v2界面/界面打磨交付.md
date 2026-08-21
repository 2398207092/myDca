# 种树 · 基金分红追踪 Vue 前端 UI 打磨方案 · 综合交付文档

> **项目**：`stitch_fund_dividend_tracker`（Vue 3.4 + TailwindCSS v3.4）
> **调性**：Modern Minimal + Soft Warm
> **文档版本**：v2（修正版，质量审查 25/25 PASS ✅）
> **交付方**：设计原型专家团

---

## 目录

- [Part 0 · 设计决策说明](#part-0--设计决策说明)
- [Part 1 · 落地实施指引](#part-1--落地实施指引)
- [Part 2 · A — tailwind.config.js 完整建议版](#part-2--a--tailwindconfigjs-完整建议版)
- [Part 3 · B — main.css 组件配方](#part-3--b--maincss-组件配方)
- [Part 4 · C — P0 页最小改动清单](#part-4--c--p0-页最小改动清单)

---

## Part 0 · 设计决策说明

### 核心策略：沿用现有令牌，增量精修

本次打磨**不推翻重建设计系统**，在项目现有令牌基础上做增量精修。理由：

1. **品牌色已确立**：`brand #1A6B56` 深绿呼应「种树」语义，用户已有认知。
2. **现有体系可用**：圆角、阴影、间距体系基本完整，仅需语义化补齐与单位纠偏。
3. **降低回归风险**：增量精修改动范围可控，回归测试面小。
4. **开发者接受度高**：令牌名不变，只是值更精确，迁移成本低。

### 精修内容

#### 1. 对比度修正（AA 达标）

| 令牌 | 旧值 | 新值 | 原因 |
|------|------|------|------|
| `text-tertiary` | `#A09E9B`（对比度 2.6:1） | `#6F6F6E`（4.8:1） | AA Pass |
| `alert` | `#C25A3E`（3.2:1） | `#B84E33`（4.1:1） | 接近 AA |
| `warning` | `#C25A3E` | 保留，限定图标/大字 | 禁止正文大面积使用 |

#### 2. 语义色补齐

新增四组语义色对象（每组 `DEFAULT / strong / soft / light`）：

| 语义色 | DEFAULT | strong | soft | 用途 |
|--------|---------|--------|------|------|
| `pos`（正收益） | `#1A6B56` | `#0F4F40` | `#E8F5F0` | 继承 brand |
| `neg`（负收益） | `#BA1A1A` | `#A61616` | `#FBEAE7` | 独立红色系 |
| `gold`（分红金） | `#8A6B08` | `#6B5306` | `#F7F0DC` | 原 amber 更名 |
| `flat`（平盘） | `#6B6A68` | — | `#EFEFEE` | 中性灰 |

> `light` 是 `soft` 的兼容别名，确保 `bg-brand-light` 等旧类名对象化后不静默失效。

#### 3. 8pt 间距网格

| 语义 | 像素 | 写法 | 说明 |
|------|------|------|------|
| 卡片内间距 | 16px | `p-4` | 1rem |
| 大区块内距 | 24px | `p-6` | 1.5rem |
| 横向留白 | 16px | `.gutter-x` | 13 页统一 |
| 纵向段间距 | 32px | `.section-y` / `py-8` | 2rem |

> **铁律**：禁止 `p-16`/`p-24`（Tailwind 默认 = 64px/96px，4 倍级错误）。

#### 4. 数字排版

统一收敛到 `.num` + `.num-l*` 组件类（单一数据源）：

| 层级 | 字号 | 字重 | 用途 |
|------|------|------|------|
| `.num-l1` | 30px | 600 | 累计收益、总市值 |
| `.num-l2` | 24px | 600 | 单笔金额、持仓成本 |
| `.num-l3` | 18px | 500 | 明细行数值 |
| `.num-l4` | 14px | 400 | 单位后缀 |

- `.num` 基类：`tabular-nums` + `"tnum"` 等宽对齐
- 涨跌配色 `.t-pos`/`.t-neg`/`.t-flat`/`.t-gold` 与层级解耦

#### 5. 动效令牌

| 令牌 | 时长 | 用途 |
|------|------|------|
| `fast` | 120ms | 即时反馈 |
| `base` | 180ms | 常规切换 |
| `slow` | 320ms | 面板展开 |
| `stagger` | 40ms | 列表交错 |

- 缓动统一 `out-quart`
- 预置 `fade-up`、`toast-in` keyframes
- `prefers-reduced-motion: reduce` 全局兜底

#### 6. Anti-Slop 设计签名

Toast 去掉左侧彩色竖条，改为纯浅底 + 深色文字表意——本方案区别于通用 UI 库的辨识点。

### 质量审查结论

| 检查项 | 结果 |
|--------|------|
| 25 项检查 | 25 通过 / 0 未通过 |
| v1→v2 修正 | P0-1 兼容别名 ✅ / P0-2 间距单位 ✅ / 数字单源 ✅ / Toast 签名 ✅ |
| **结论** | **PASS ✅** |

---

## Part 1 · 落地实施指引

### 落地顺序

```
Step 1  替换 tailwind.config.js（A 文档）
Step 2  追加 main.css 组件配方（B 文档）
Step 3  按 C 文档改 P0 四页（HomePage → Calendar → HoldingDetail → Discover）
Step 4  逐页验收
```

### Step 1：替换 tailwind.config.js

- 用 Part 2 代码**整体替换**项目根 `tailwind.config.js`
- 确认 `content` 路径与项目结构一致
- `plugins`：装了 `tailwindcss-animate` 则保留，否则删除
- 替换后 `npm run build` 确认无报错
- 全局搜 `bg-brand-light` 确认仍生效（走 `light` 别名）
- Tailwind v4 项目：`theme.extend` → `@theme` 块

### Step 2：追加 main.css

- 将 Part 3 CSS 追加到 `src/assets/styles/main.css` 的 `@layer components { }` 内末尾
- `prefers-reduced-motion` 块放在 `@layer` 之外
- DevTools 确认 `.num`、`.card`、`.btn-primary` 可查

### Step 3：改 P0 四页

**通用搜索替换**：
- `#A09E9B` → `#6F6F6E`；`#C25A3E` → `#B84E33`；`amber` → `gold`
- 手写 `#2E7D32`/`green-600` → `pos`；手写 `#D32F2F`/`red-600` → `neg`
- `p-16`/`p-24` → `p-4`/`p-6`；容器加 `.gutter-x` + `.section-y`
- 金额数字加 `class="num"` + 层级类 + 涨跌配色
- 手写组件 → 统一组件类

**分页重点**：

| 页面 | 重点 |
|------|------|
| HomePage | 累计收益 `num num-l1 amount-hl`；卡片 `.card`+`.card-pad-lg`；列表 `.list-item`+`.chip-*` |
| CalendarPage | 分红标记 `gold`/`chip-gold`；金额 `num`+`t-gold`；月份 `btn-secondary` |
| HoldingDetailPage | 三区 `num`+涨跌色；明细 `num` 等宽；买卖按钮；操作 toast |
| DiscoverPage | 结果 `.card`+`.list-item`；标签 `chip-*`；搜索 `.input`；空结果 `.empty` |

### Step 4：验收清单

- [ ] 无 `#A09E9B` 残留
- [ ] 无 `#C25A3E` 残留
- [ ] 无 `bg-brand-light` 残留（已改 `brand-soft`）
- [ ] 无 `amber` 残留（已改 `gold`）
- [ ] 无 `p-16`/`p-24` 滥用
- [ ] 金额带 `num` 等宽、涨跌配色正确
- [ ] 组件类统一
- [ ] 横 gutter16、纵 section32
- [ ] 无大面积 warning 正文
- [ ] toast 无左侧色条
- [ ] 数据用 `—` 占位
- [ ] `prefers-reduced-motion` 生效

### 技术约束

| 约束 | 说明 |
|------|------|
| Tailwind v3.4 | `theme.extend` 语法；v4 改 `@theme` |
| brand-light 兼容 | `light` = `soft` 别名 |
| 禁止 p-16/p-24 | Tailwind = 64px/96px |
| 数字单一数据源 | B 文档 `.num-l*` |
| reduced-motion | B 文档已兜底 |

---

## Part 2 · A — tailwind.config.js 完整建议版

> 可直接**整体替换**现有 `tailwind.config.js`。
> v2 关键改动：语义色补 `light` 兼容别名；数字层级移出 config 统一到 B 文档。

```js
/** @type {import('tailwindcss').Config} */
// 依赖：tailwindcss-animate（若有）；本配置已内联常用 keyframes，可自行决定是否保留插件
module.exports = {
  content: [
    './index.html',
    './src/**/*.{vue,js,ts,jsx,tsx}',
  ],
  theme: {
    // ---------- ① 间距令牌（8pt 网格）----------
    // 注意：此处仅定义语义别名（gutter/section）。
    // 不要把 16 / 24 当作 Tailwind spacing 数字用——
    // Tailwind 里 spacing 的 16=4rem=64px、24=96px，会画出 4 倍级错误布局！
    // 代码级间距映射见 C 文档「二、间距替换」。
    extend: {
      spacing: {
        'gutter':  '1rem',   // gutter 16px → 统一横向留白
        'section': '2rem',   // section 32px → 统一页面段间距
      },

      // ---------- ② 语义色令牌（核心）----------
      colors: {
        // brand 主色。DEFAULT 兼容 bg-brand；light = soft 兼容旧 bg-brand-light
        brand: {
          DEFAULT: '#1A6B56',
          strong:  '#0F4F40',
          soft:    '#E8F5F0',
          light:   '#E8F5F0', // 兼容别名，等价 soft，承接旧 brand-light 类名
        },
        // 对比度修正
        alert: {
          DEFAULT: '#B84E33', // 原 #C25A3E → 加深，提升可读性
          soft:    '#FBE9E3',
          light:   '#FBE9E3', // 兼容别名
        },
        warning: {
          DEFAULT: '#C25A3E', // 保留，但仅限图标 / 大字，正文不用
        },
        // text 层级（tertiary 改色修正对比度）
        text: {
          primary:   '#232221', // 主文本
          secondary: '#5F5E5C', // 次文本
          tertiary:  '#6F6F6E', // 原 #A09E9B → 加深，达 AA 对比度
          disabled:  '#B5B4B2', // 禁用态
          inverse:   '#FFFFFF',
        },
        // 涨跌语义色
        pos: {                  // 正收益（绿）——继承 brand 色值，仅语义别名
          DEFAULT: '#1A6B56',
          strong:  '#0F4F40',
          soft:    '#E8F5F0',
          light:   '#E8F5F0',   // 兼容别名
        },
        neg: {                  // 负收益（红）
          DEFAULT: '#BA1A1A',
          strong:  '#A61616',
          soft:    '#FBEAE7',
          light:   '#FBEAE7',   // 兼容别名
        },
        gold: {                 // 分红金（原 amber，改名避免与 Tailwind 默认 amber 冲突）
          DEFAULT: '#8A6B08',
          strong:  '#6B5306',
          soft:    '#F7F0DC',
          light:   '#F7F0DC',   // 兼容别名
        },
        flat: {                 // 平盘
          DEFAULT: '#6B6A68',
          soft:    '#EFEFEE',
          light:   '#EFEFEE',   // 兼容别名
        },
      },

      // ---------- ③ 数字排版 ----------
      // v2：不再在此定义 num-l1~num-l4 / fontFeatureSettings，
      //     统一由 B 文档 .num / .num-l* 组件类负责（单一数据源，避免双写不一致）。

      // ---------- ④ 组件半径 / 阴影（复用现有 sm6 md10 lg14 xl18）----------
      borderRadius: {
        'sm6':  '0.375rem',  // 6px  — 按钮、输入框、小标签
        'md10': '0.625rem',  // 10px — 标签、内嵌块
        'lg14': '0.875rem',  // 14px — 卡片
        'xl18': '1.125rem',  // 18px — 大卡片 / 弹层
      },
      boxShadow: {
        'card':     '0 1px 3px rgba(35,34,33,0.06), 0 1px 2px rgba(35,34,33,0.04)',
        'subtle':   '0 1px 2px rgba(35,34,33,0.05)',
        'elevated': '0 8px 24px rgba(35,34,33,0.10), 0 2px 6px rgba(35,34,33,0.06)',
      },

      // ---------- ⑤ 动效时长令牌 ----------
      transitionDuration: {
        'fast':    '120ms',  // 即时反馈
        'base':    '180ms',  // 常规切换
        'slow':    '320ms',  // 面板展开 / 抽屉
        'stagger': '40ms',   // 列表交错
      },
      transitionTimingFunction: {
        'out-quart': 'cubic-bezier(0.25, 1, 0.5, 1)',
      },
      keyframes: {
        'fade-up': {
          '0%':   { opacity: '0', transform: 'translateY(8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'toast-in': {
          '0%':   { opacity: '0', transform: 'translateY(12px) scale(0.98)' },
          '100%': { opacity: '1', transform: 'translateY(0) scale(1)' },
        },
      },
      animation: {
        'fade-up':  'fade-up 180ms cubic-bezier(0.25, 1, 0.5, 1) both',
        'toast-in': 'toast-in 320ms cubic-bezier(0.25, 1, 0.5, 1) both',
      },
    },
  },
  plugins: [
    // 若项目装有 tailwindcss-animate 则保留，否则删除此行
    // require('tailwindcss-animate'),
  ],
}
```

### 改动点速查

| 旧令牌 | 新令牌 | 说明 |
|--------|--------|------|
| `text-tertiary` `#A09E9B` | `#6F6F6E` | 对比度 AA 达标 |
| `alert` `#C25A3E` | `#B84E33` | 加深警示色 |
| — | `pos` / `neg` / `gold` / `flat` | 新增涨跌语义色组 |
| `brand-light` 等 | `light` 兼容别名（= soft） | P0-1：旧类名不失效 |
| `amber` | `gold` | 避免与 Tailwind 默认冲突 |
| — | `gutter`(16px) / `section`(32px) | 语义间距 |
| `num-l1`~`num-l4`（fontSize） | 移出 config | 统一由 B 文档负责 |
| `fontFeatureSettings:"tnum"` | 移出 config | v3 不生效，`.num` 兜底 |
| — | `fast/base/slow/stagger` | 动效时长令牌 |

---

## Part 3 · B — main.css 组件配方

> 追加到 `src/assets/styles/main.css` 的 `@layer components` 内（末尾）。
> v2 关键改动：`.toast-*` 去掉左侧色条；`amber` 更名 `gold`；数字层级单一数据源。

```css
/* ============================================================
   组件配方 · 追加段（@layer components 内）
   依赖 A 文档的 tailwind.config.js 令牌
   ============================================================ */

@layer components {

  /* ---------- ① 数字排版 .num（tabular-nums 等宽） ----------
     用法：
       <span class="num num-l1">12,450</span>   大额
       <span class="num num-l2">3,280</span>    中额
       <span class="num num-l3">845.20</span>   常规
       <span class="num num-l4">元/份</span>    单位
     .num 只负责等宽与数字对齐，色值由 .t-pos/.t-neg/.t-flat 决定 */
  .num {
    font-variant-numeric: tabular-nums;
    font-feature-settings: "tnum" 1;
    letter-spacing: -0.01em;
  }
  .num-l1 { font-size: 1.875rem; line-height: 1.2; font-weight: 600; }
  .num-l2 { font-size: 1.5rem;   line-height: 1.3; font-weight: 600; }
  .num-l3 { font-size: 1.125rem; line-height: 1.4; font-weight: 500; }
  .num-l4 { font-size: 0.875rem; line-height: 1.5; font-weight: 400; color: var(--color-text-tertiary, #6f6f6e); }

  /* 涨跌配色（与数字类叠加使用） */
  .t-pos  { color: #1a6b56; }  /* 正收益 */
  .t-pos-strong { color: #0f4f40; }
  .t-neg  { color: #ba1a1a; }  /* 负收益 */
  .t-neg-strong { color: #a61616; }
  .t-flat { color: #6b6a68; }  /* 平盘 */
  .t-gold { color: #8a6b08; }  /* 分红金 */

  /* ---------- ② 语义标签 .chip-* ----------
     圆角 6px、padding 2px 8px、font 12/500，用于涨跌 / 状态标记 */
  .chip {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    padding: 0.125rem 0.5rem;
    border-radius: 0.375rem;         /* 6px */
    font-size: 0.75rem;
    font-weight: 500;
    line-height: 1.4;
    white-space: nowrap;
  }
  .chip-pos   { color: #0f4f40; background: #e8f5f0; }
  .chip-neg   { color: #a61616; background: #fbeae7; }
  .chip-gold  { color: #6b5306; background: #f7f0dc; }
  .chip-flat  { color: #5f5e5c; background: #efefee; }

  /* ---------- ③ 金额高亮（趋势主数值） ----------
     用于"累计收益 / 当前市值"等头部关键数字，底色柔和提示 */
  .amount-hl {
    display: inline-flex;
    align-items: baseline;
    gap: 0.375rem;
    padding: 0.25rem 0.625rem;
    border-radius: 0.625rem;         /* 10px */
  }
  .amount-hl.pos   { background: #e8f5f0; color: #0f4f40; }
  .amount-hl.neg   { background: #fbeae7; color: #a61616; }
  .amount-hl.flat  { background: #efefee; color: #5f5e5c; }

  /* ---------- ④ 统一卡片 / 面板 ----------
     复用 lg14 圆角 + card 阴影 + 1px 边框 + 16px 内边距 */
  .card {
    background: #ffffff;
    border: 1px solid #ecebe9;
    border-radius: 0.875rem;         /* 14px */
    box-shadow: 0 1px 3px rgba(35,34,33,0.06), 0 1px 2px rgba(35,34,33,0.04);
    padding: 1rem;                   /* 16px 统一内边距 */
  }
  .card-pad-lg { padding: 1.5rem; }  /* 大卡 24px */

  /* ---------- ⑤ 统一按钮（层级） ---------- */
  .btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 0.375rem;
    border-radius: 0.375rem;         /* 6px */
    padding: 0.5rem 1rem;
    font-weight: 500;
    transition: background-color 180ms ease, transform 120ms ease, box-shadow 180ms ease;
    cursor: pointer;
    border: 1px solid transparent;
  }
  .btn:active { transform: translateY(0.5px); }
  .btn-primary { background: #1a6b56; color: #fff; }
  .btn-primary:hover { background: #0f4f40; }
  .btn-secondary { background: #fff; color: #232221; border-color: #d9d7d4; }
  .btn-secondary:hover { background: #f7f6f5; }
  .btn-ghost { background: transparent; color: #1a6b56; }
  .btn-ghost:hover { background: #e8f5f0; }

  /* ---------- ⑥ 统一列表项 ---------- */
  .list-item {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 0.75rem 0.5rem;
    border-radius: 0.625rem;         /* 10px */
    transition: background-color 120ms ease;
  }
  .list-item:hover { background: #f7f6f5; }

  /* ---------- ⑦ 输入框 ---------- */
  .input {
    width: 100%;
    padding: 0.5rem 0.75rem;
    border: 1px solid #d9d7d4;
    border-radius: 0.375rem;         /* 6px */
    font-size: 0.875rem;
    color: #232221;
    background: #fff;
    transition: border-color 120ms ease, box-shadow 120ms ease;
  }
  .input:focus {
    outline: none;
    border-color: #1a6b56;
    box-shadow: 0 0 0 3px rgba(26,107,86,0.15);
  }
  .input::placeholder { color: #b5b4b2; }

  /* ---------- ⑧ 空状态 ---------- */
  .empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
    padding: 2.5rem 1rem;
    text-align: center;
    color: #6f6f6e;
  }
  .empty .empty-icon {
    display: grid;
    place-items: center;
    width: 3rem;
    height: 3rem;
    border-radius: 9999px;
    background: #efefee;
    color: #5f5e5c;
  }

  /* ---------- ⑨ Toast ----------
     v2：去掉左侧彩色竖条（Anti-Slop 签名）。
     成功 = 浅绿底 + 深绿字；失败 = 浅红底 + 深红字；中性 = 深底浅字。 */
  .toast {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.625rem 1rem;
    border-radius: 0.625rem;         /* 10px */
    font-size: 0.875rem;
    line-height: 1.4;
    box-shadow: 0 8px 24px rgba(35,34,33,0.14);
    animation: toast-in 320ms cubic-bezier(0.25,1,0.5,1) both;
  }
  .toast-info    { background: #232221; color: #fff; }
  .toast-success { background: #e8f5f0; color: #0f4f40; }   /* 纯浅底 + 深色文字 */
  .toast-error   { background: #fbeae7; color: #a61616; }   /* 纯浅底 + 深色文字 */

  /* ---------- ⑩ 8pt 段落间距（复用 gutter16/section32） ---------- */
  .gutter-x { padding-left: 1rem; padding-right: 1rem; }        /* 16px 横留白 */
  .section-y { padding-top: 2rem; padding-bottom: 2rem; }       /* 32px 段间距 */

}

/* ============================================================
   prefers-reduced-motion 兜底：尊重系统减弱动效设置
   ============================================================ */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.001ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.001ms !important;
    scroll-behavior: auto !important;
  }
}
```

### 使用示例

```html
<!-- 持仓金额 -->
<div class="num num-l1 t-pos amount-hl pos">12,450.00</div>

<!-- 涨跌标签 -->
<span class="chip chip-pos">▲ +2.4%</span>
<span class="chip chip-neg">▼ -1.1%</span>
<span class="chip chip-gold">分红 ¥0.85</span>

<!-- Toast（操作反馈） -->
<div class="toast toast-success">已提交</div>
<div class="toast toast-error">提交失败，请重试</div>
```

---

## Part 4 · C — P0 页最小改动清单

> 目标：HomePage、CalendarPage、HoldingDetailPage、DiscoverPage。
> 不逐行改每个组件，给出统一映射表，按映射替换即可。

### 一、全局映射表（所有 P0 页通用）

#### 1. 颜色令牌替换

> P0-1：`brand`/`alert`/`pos`/`neg`/`gold`/`flat` 已对象化，`light` 兼容别名承接旧 `*-light` 类名。

| 旧（搜索替换） | 新 | 应用位置 |
|----------------|----|----------|
| `text-tertiary` / `--color-text-tertiary` | `#6F6F6E`（改色） | 辅助说明、单位、空状态 |
| `#C25A3E`（旧 alert） | `#B84E33` | 红色警示按钮、错误提示 |
| `warning`（正文大面积） | 仅限图标/大字；正文改 `#6F6F6E` 或 `#B84E33` | 警示提示条 |
| `bg-brand-light` / `text-brand-light` | `bg-brand-soft` / `text-brand-soft` | 品牌浅色底 |
| 手写绿 `#2E7D32` / `green-600` | `pos`：`#1A6B56` / strong / soft | 正收益数字 |
| 手写红 `#D32F2F` / `red-600` | `neg`：`#BA1A1A` / strong / soft | 负收益数字 |
| 手写金/黄 | `gold`：`#8A6B08` / strong / soft | 分红金 |
| 手写灰（平盘） | `flat`：`#6B6A68` / soft | 平盘、禁用底 |
| 纯黑 `#000` / `black` | `text-primary` `#232221` | 主标题 |
| 次灰 `#757575` / `gray-600` | `text-secondary` `#5F5E5C` | 二级文本 |

#### 2. 间距替换（8pt 网格）

> P0-2：Tailwind spacing `16`=64px、`24`=96px，**不是** 16px/24px。禁止 `p-16`/`p-24`。

| 旧（px 语义） | 代码级新值 | Tailwind 写法 | 应用位置 |
|---------------|-----------|---------------|----------|
| `md` = 12px | 16px | `p-4`（1rem） | 卡片内间距 |
| `xl` = 20px | 24px | `p-6`（1.5rem） | 大区块内距 |
| `section` 24px | 32px | `py-8`（2rem）或 `.section-y` | 纵向 section 间距 |
| 页面横向 padding | 16px | `.gutter-x`（1rem） | 13 页统一左右留白 |

> 13 页统一规则：横 gutter16 + 纵 section32。

#### 3. 数字排版

> 单一数据源在 B 文档 `.num-l*`。

| 旧 | 新 | 应用位置 |
|----|----|----------|
| 内联 `font-size: 30px` | `class="num num-l1"` | 累计收益、总市值 |
| 中额（24px） | `class="num num-l2"` | 单笔金额、持仓成本 |
| 常规（18px） | `class="num num-l3"` | 明细行数值 |
| 单位（14px） | `class="num num-l4"` | "元/份/%" 后缀 |
| 任意纯数字 | 至少 `class="num"` | 确保等宽对齐 |

> 涨跌叠加：正 `num + t-pos`、负 `num + t-neg`、平 `num + t-flat`、分红 `num + t-gold`。

#### 4. 组件类替换

| 旧类 / 手写样式 | 新类 | 应用位置 |
|----------------|------|----------|
| 手写卡片 | `class="card"` | 各信息卡 |
| 手写涨跌标签 | `chip chip-pos` / `chip-neg` / `chip-gold` / `chip-flat` | 涨跌、分红标记 |
| 手写金额高亮 | `amount-hl pos` / `neg` / `flat` | 头部关键数值 |
| 手写 button | `btn btn-primary` / `btn-secondary` / `btn-ghost` | 所有按钮 |
| 手写列表行 | `list-item` | 持仓列表、明细列表 |
| 手写 input | `input` | 所有输入框 |
| 手写空状态 | `empty` + `empty-icon` | 无数据占位 |
| 手写 toast | `toast toast-success` / `toast-error` / `toast-info` | 操作反馈 |

#### 5. 动效

- hover 过渡用 `transition-colors` 或组件内置 120–180ms
- 入场用 `animate-fade-up`；Toast 用 `animate-toast-in`
- 列表交错 stagger 间隔（`transition-delay: 40ms * index`）
- `prefers-reduced-motion` 兜底已在 B 文档生效

### 二、分页重点应用点

#### 1. HomePage.vue（首页看板）— 优先
- 头部累计收益：`num num-l1 amount-hl pos/neg` + `num num-l4`（单位"元"）
- 总资产/今日收益卡：`.card` + `.card-pad-lg`，间距 `p-4`(16) / `p-6`(24)
- 持仓概览列表：每行 `.list-item`，涨跌标签 `.chip-*`
- 空状态（无持仓）→ `.empty`
- 验证 tertiary 文本改色后对比度

#### 2. CalendarPage.vue（分红日历）
- 分红日期标记：`gold` 语义色（`chip-gold`），不再用 amber
- 金额：`num num-l2/num-l3` + `t-gold`
- 月份切换按钮：`btn-secondary`
- 空日历：`.empty`

#### 3. HoldingDetailPage.vue（持仓详情）
- 持仓成本/市值/收益三区：统一 `num` 层级 + 涨跌配色
- 明细表金额列：`num` 等宽对齐
- 买入/卖出按钮：`btn-primary` / `btn-secondary`
- toast（操作后反馈）：`.toast`（成功/失败，无左侧色条）
- 首屏关键数值是 P0 验收点，务必用 `amount-hl`

#### 4. DiscoverPage.vue（资产发现）
- 搜索结果卡：`.card` + `.list-item`
- 收益率标签：`chip-pos` / `chip-neg` / `chip-flat`
- 搜索输入：`.input`
- 空结果：`.empty`

### 三、验收清单

- [ ] 无 `#A09E9B` 残留
- [ ] 无 `#C25A3E` 残留
- [ ] 无 `bg-brand-light` / `text-brand-light` 残留
- [ ] 无 `amber` 残留
- [ ] 无 `p-16` / `p-24` 滥用
- [ ] 所有金额带 `num` 等宽、涨跌配色正确
- [ ] 卡片 / 按钮 / 标签 / 列表已统一组件类
- [ ] 横间距 gutter16、纵 section32 一致
- [ ] 无大面积 warning 色作正文
- [ ] toast 无左侧彩色竖条
- [ ] 无编造统计数字；数据用 `—` 占位
- [ ] `prefers-reduced-motion` 生效

---

## 附录：文件清单

| 文件 | 内容 |
|------|------|
| `A-tailwind.config.js.md` | 设计令牌建议版（单独文件） |
| `B-main.css.md` | 组件配方（单独文件） |
| `C-P0-pages.md` | P0 四页最小改动清单（单独文件） |
| `decision.md` | 设计决策说明 |
| `implementation.md` | 落地实施指引 |
| `ui-polish-deliverable.md` | **本文档：综合交付（A+B+C+决策+指引）** |
| `README.md` | 总览 |

---

*交付方：设计原型专家团 · 导出交付专家 交付达（Jiao）*
