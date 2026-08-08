# A. tailwind.config.js —— 完整建议版

> **修订记录：v2，修正 P0-1 兼容别名、P0-2 间距单位歧义，采纳 toast/num 双源优化**
>
> 可直接**整体替换**现有 `tailwind.config.js`。
> 基于现有项目增量精修：修正对比度、补齐语义色、8pt 间距、动效令牌。
> 默认按 Tailwind **v3** 语法（`theme.extend`）撰写；若项目是 v4，将 `theme.extend` 内层移到 `@theme` 块即可。
>
> **v2 关键改动**：语义色对象统一补 `light` 兼容别名（`bg-brand-light` 等旧类名不再失效）；数字层级**不再由 fontSize 定义**，统一收敛到 B 文档 `.num`/`.num-l*` 组件类（单一数据源，避免双写）。

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

---

## 对照：改动点速查

| 旧令牌 | 新令牌 | 说明 |
|--------|--------|------|
| `text-tertiary` `#A09E9B` | `#6F6F6E` | 对比度 AA 达标 |
| `alert` `#C25A3E` | `#B84E33` | 加深警示色 |
| — | `pos` / `neg` / `gold` / `flat` | 新增涨跌语义色组 |
| `brand-light` / `alert-light` 等 | `light` 兼容别名（= soft） | **P0-1**：对象化后旧类名不再静默失效 |
| `amber` | `gold` | 改名避免与 Tailwind 默认 amber 冲突 |
| — | `gutter`(16px) / `section`(32px) | 语义间距，见 C 文档代码级映射 |
| `num-l1`~`num-l4`（fontSize） | 移出 config | **P1**：统一由 B 文档 `.num-l*` 负责 |
| `fontFeatureSettings:"tnum"` | 移出 config | **P2**：Tailwind v3 不生效，等宽已由 `.num` 兜底 |
| — | `--dur-fast/base/slow/stagger` | 动效时长令牌 |
