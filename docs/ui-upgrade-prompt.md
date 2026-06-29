# 「种树」前端 UI 升级提示词

> 本文档用于指导 AI 在不改变功能的前提下，对现有前端 UI 进行品质升级。
> 目标：消除"廉价感"，让界面达到 Robinhood / 支付宝理财 / Stripe Dashboard 级别的精致度。
> 使用方式：将此文档完整贴给 AI（Google Gemini / DeepSeek / GPT），然后提供需要修改的 .vue 文件。

---

## 一、升级目标

```
当前水平：个人开发者练手项目 UI
目标水平：精品金融类 App UI（对标 Robinhood、支付宝理财、Bloomberg）
约束条件：不改变任何功能逻辑，不增删 API，不改变数据结构，只改 UI 层
改造范围：tailwind.config.js + main.css + 所有 .vue 文件 + index.html
```

---

## 二、配色体系重塑

### 2.1 问题诊断

| 问题 | 现状 | 影响 |
|------|------|------|
| 主色暗沉 | `primary: '#a73a05'` 铁锈红/暗棕 | 缺乏活力，不像金融产品 |
| 灰阶过密 | 7 个 surface 层级实际只有 4 个可分辨 | 卡片堆叠无纵深感 |
| 背景与卡片分不清 | `background: #eeedef` ≈ `surface-container-high: #e9e8e8` | 页面像一张大白纸 |
| 硬编码颜色泛滥 | `bg-blue-100`、`style="background-color:#3B82F6"` 绕过 token | 破坏整体色板一致性 |

### 2.2 修改方案

**第一步：重新定义 primary 色板**

```js
// tailwind.config.js — 替换现有 primary 色板
colors: {
  'primary': {
    50:  '#FFF5EE',
    100: '#FFE8D6',
    200: '#FFD0AD',
    300: '#FFB07A',
    400: '#FF8F47',  // ← 活泼的暖橙色，主操作色
    500: '#FF6B35',  // ← 默认 primary
    600: '#E85D04',  // ← hover/active
    700: '#C44B03',
    800: '#9C3A02',
    900: '#732B01',
  },
  // 使用方式：bg-primary-500, text-primary-600, border-primary-200
}
```

**第二步：精简 surface 层级（7→4，拉大色差）**

```js
'surface': {
  DEFAULT: '#ffffff',           // 最高层：主卡片
  'raised': '#f8f7f7',          // 次高层：列表项、分组背景
  'container': '#f0eeed',       // 中层：次级容器、非活跃区域
  'dim': '#e8e5e4',             // 底层：禁用态、骨架屏
}
// 注意：色差从原来的 ΔE<2 拉大到 ΔE>8，人眼可清晰分辨
```

**第三步：页面背景与卡片背景拉开差距**

```js
'background': '#f2f0ef',  // 比最深的卡片再深一度，形成明显对比
```

**第四步：定义语义化事件颜色（替换所有硬编码）**

```js
'event': {
  'registration': { bg: '#EEF2FF', text: '#4338CA', border: '#A5B4FC' },  // 登记日 — 靛蓝
  'exDividend':   { bg: '#FFF7ED', text: '#C2410C', border: '#FDBA74' },  // 除权日 — 橙色
  'payout':       { bg: '#ECFDF5', text: '#047857', border: '#6EE7B7' },  // 发放日 — 翠绿
  'announcement': { bg: '#FDF2F8', text: '#BE185D', border: '#F9A8D4' },  // 公告日 — 粉红
}
// 替换 CalendarPage.vue 中所有 bg-blue-100 text-blue-700 等硬编码
```

**第五步：统一涨跌颜色（中国股市约定：涨红跌绿）**

```js
'up':   { DEFAULT: '#DC2626', light: '#FEE2E2' },   // 涨：红
'down': { DEFAULT: '#059669', light: '#ECFDF5' },   // 跌：绿
```

---

## 三、阴影体系重做

### 3.1 问题诊断

当前 `card-shadow: 0 4px 12px rgba(0,0,0,0.05)` — 透明度 0.05 在白色背景上几乎不可见，这是"廉价感"的第一元凶。卡片看起来像贴在背景上的纸片，而非浮起的三维物体。

### 3.2 修改方案

```css
/* main.css — 替换现有阴影定义 */

/* 微阴影 — 列表项、小卡片 */
.shadow-subtle {
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 1px 2px rgba(0, 0, 0, 0.06);
}

/* 卡片阴影 — 标准卡片（替代原有 card-shadow）*/
.shadow-card {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.04);
}

/* 浮动阴影 — Hero 卡片、弹窗、BottomSheet */
.shadow-elevated {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08), 0 2px 8px rgba(0, 0, 0, 0.06);
}

/* 顶层阴影 — Modal 遮罩层 */
.shadow-overlay {
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.12), 0 4px 16px rgba(0, 0, 0, 0.08);
}
```

**同步到 tailwind.config.js：**
```js
boxShadow: {
  'subtle':   '0 1px 3px rgba(0,0,0,0.04), 0 1px 2px rgba(0,0,0,0.06)',
  'card':     '0 2px 8px rgba(0,0,0,0.06), 0 1px 4px rgba(0,0,0,0.04)',
  'elevated': '0 8px 24px rgba(0,0,0,0.08), 0 2px 8px rgba(0,0,0,0.06)',
  'overlay':  '0 16px 48px rgba(0,0,0,0.12), 0 4px 16px rgba(0,0,0,0.08)',
}
```

**应用规则：**
- 列表中的普通卡片 → `shadow-card`
- 首页 Hero 卡片、BottomSheet、FAB → `shadow-elevated`
- 小标签/Chip → `shadow-subtle`
- 弹窗遮罩层 → `shadow-overlay`

---

## 四、字体排印规范化

### 4.1 问题诊断

- fontFamily token 把相同字体映射为 6 个不同名称，造成混淆
- 多处使用 `text-[32px]`、`text-[22px]`、`text-[13px]`、`text-[11px]`、`text-[10px]`、`text-[8px]` 等任意值绕过 token
- `text-headline-lg-mobile` 类名未定义

### 4.2 修改方案

**第一步：精简 fontFamily 到 2 个**

```js
fontFamily: {
  'display': ['Plus Jakarta Sans', 'sans-serif'],  // 大标题、Hero 数字
  'body': ['Work Sans', 'sans-serif'],              // 正文、标签、辅助文字
}
```

**第二步：建立严格的字号阶梯（11/12/13/14/16/20/24/32）**

```js
fontSize: {
  'xs':    ['11px', { lineHeight: '16px', fontWeight: '400' }],   // 辅助说明
  'sm':    ['12px', { lineHeight: '16px', fontWeight: '400' }],   // 标签、时间
  'base':  ['13px', { lineHeight: '20px', fontWeight: '400' }],   // 正文
  'md':    ['14px', { lineHeight: '20px', fontWeight: '500' }],   // 强调正文、列表标题
  'lg':    ['16px', { lineHeight: '24px', fontWeight: '500' }],   // 小标题
  'xl':    ['20px', { lineHeight: '28px', fontWeight: '500' }],   // 段落标题
  '2xl':   ['24px', { lineHeight: '32px', fontWeight: '500' }],   // 页面标题
  '3xl':   ['32px', { lineHeight: '40px', fontWeight: '500' }],   // Hero 数字
}
```

**第三步：全局搜索替换**
- `text-[32px]` → `text-3xl`
- `text-[22px]` → `text-2xl`（24px 近似）
- `text-[14px]` → `text-md`
- `text-[13px]` → `text-base`
- `text-[11px]` → `text-xs`
- `text-[10px]` → `text-xs`
- `text-[8px]` → `text-xs`（8px 过小，不推荐用于正文）
- `font-headline-lg` → `font-display text-2xl`
- `font-body-md` → `font-body text-base`
- 删除 `text-headline-lg-mobile` 引用（该类不存在）

---

## 五、间距体系整理

### 5.1 问题诊断

`sm=base=8px`, `md=gutter=container-padding=16px`，4 个命名指向 2 个值，缺乏 12px/20px 等过渡值。

### 5.2 修改方案

```js
spacing: {
  'xs': '4px',
  'sm': '8px',
  'md': '12px',
  'lg': '16px',
  'xl': '20px',
  '2xl': '24px',
  '3xl': '32px',
  '4xl': '40px',
  'gutter': '16px',       // 保留，用于页面水平内边距
  'section': '24px',      // 新增，用于区块间距
}
```

**全局搜索替换：**
- `p-md` 原本是 16px → 现在 `p-md = 12px`，检查并改为 `p-lg`
- `space-y-4` → `space-y-lg`
- `p-[10px]` → `p-md`（12px）
- `gap-2` → `gap-sm`
- `mt-6` → `mt-2xl`

---

## 六、卡片质感提升

### 6.1 问题诊断

- 所有卡片使用相同 `rounded-xl` 和 `shadow-card`
- 卡片之间无视觉分隔，堆叠时融为一体
- 无嵌套圆角节奏（外层圆角应大于内层）

### 6.2 修改方案

**圆角体系增加层级：**

```js
borderRadius: {
  'sm': '6px',      // Chip、小标签
  'md': '10px',     // 列表项、内层卡片
  'lg': '14px',     // 标准卡片
  'xl': '18px',     // Hero 卡片、弹窗
  '2xl': '24px',    // Modal
}
```

**卡片嵌套规则：**
```
外层容器（页面 section）: rounded-xl (18px)
  └── 内层卡片: rounded-lg (14px)
        └── 列表项: rounded-md (10px)
              └── Chip: rounded-sm (6px)
```

**卡片视觉分隔增强：**

```
方案 A（推荐）：交替 surface 层级
  第1张卡片: bg-surface
  第2张卡片: bg-surface-raised
  第3张卡片: bg-surface
  ...

方案 B：添加微边框
  每张卡片: border border-black/[0.04]

方案 C：增大卡片间距
  space-y-lg (16px) → space-y-xl (20px)
```

---

## 七、动效与微交互注入

### 7.1 问题诊断

全站仅 3 处动效（`active:scale`、`hover:bg`、`animate-spin`），缺少：
- 页面切换过渡
- 数字滚动动画
- 骨架屏加载态
- 列表进入交错动画
- 底部导航图标变形

### 7.2 修改方案

**1. 页面切换动画 — 在 App.vue 的 `<router-view>` 外包 `<Transition>`**

```html
<!-- App.vue -->
<router-view v-slot="{ Component }">
  <Transition name="page-slide" mode="out-in">
    <component :is="Component" />
  </Transition>
</router-view>
```

```css
/* main.css */
.page-slide-enter-active,
.page-slide-leave-active {
  transition: opacity 0.2s ease, transform 0.25s ease;
}
.page-slide-enter-from {
  opacity: 0;
  transform: translateX(20px);
}
.page-slide-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}
```

**2. 数字递增动画 — 创建 composable**

```ts
// src/composables/useCountUp.ts
import { ref, watch, onMounted } from 'vue'

export function useCountUp(target: () => number, duration = 800) {
  const display = ref(0)
  let rafId: number

  const animate = (from: number, to: number) => {
    const start = performance.now()
    const tick = (now: number) => {
      const progress = Math.min((now - start) / duration, 1)
      // easeOutCubic
      const eased = 1 - Math.pow(1 - progress, 3)
      display.value = from + (to - from) * eased
      if (progress < 1) rafId = requestAnimationFrame(tick)
    }
    rafId = requestAnimationFrame(tick)
  }

  onMounted(() => animate(0, target()))
  watch(target, (val, old) => {
    cancelAnimationFrame(rafId)
    animate(old ?? 0, val)
  })

  return { display }
}
```

**应用：首页 Hero 数字、资产概览总资产、持仓详情预测分红**

**3. 加载态改为骨架屏**

```html
<!-- 替代 PageState.vue 的 spinner + 文字 -->
<!-- 骨架屏示例 — HomePage 加载中 -->
<div v-if="loading" class="space-y-lg animate-pulse">
  <!-- Hero 骨架 -->
  <div class="bg-surface rounded-xl p-lg">
    <div class="h-4 w-24 bg-surface-container rounded mb-md" />
    <div class="h-8 w-48 bg-surface-container rounded mb-sm" />
    <div class="h-4 w-36 bg-surface-container rounded" />
  </div>
  <!-- 卡片骨架 × 3 -->
  <div v-for="i in 3" :key="i" class="bg-surface rounded-xl p-lg">
    <div class="flex justify-between mb-md">
      <div class="h-5 w-32 bg-surface-container rounded" />
      <div class="h-5 w-20 bg-surface-container rounded" />
    </div>
    <div class="grid grid-cols-4 gap-md">
      <div v-for="j in 4" :key="j" class="h-10 bg-surface-container rounded" />
    </div>
  </div>
</div>
```

**4. 底部导航 active 态增强**

```html
<!-- BottomNav.vue — active 图标加粗 + 填充 + 颜色渐变 -->
<span
  class="material-symbols-outlined text-[24px] transition-all duration-300"
  :style="activeTab === tab.key
    ? { fontVariationSettings: '\'FILL\' 1, \'wght\' 700', color: 'var(--color-primary-500)' }
    : { fontVariationSettings: '\'FILL\' 0, \'wght\' 400' }"
>
  {{ tab.icon }}
</span>
```

**5. 列表进入交错动画**

```html
<!-- 持仓列表项动画 -->
<TransitionGroup name="list" tag="div" class="space-y-md">
  <div v-for="(item, idx) in items" :key="item.id"
       class="bg-surface rounded-lg p-lg shadow-card"
       :style="{ transitionDelay: idx * 50 + 'ms' }">
    ...
  </div>
</TransitionGroup>
```

```css
.list-enter-active { transition: all 0.4s ease; }
.list-enter-from { opacity: 0; transform: translateY(12px); }
```

---

## 八、数据展示精致化

### 8.1 问题诊断

- 所有金额使用同一字体同一颜色，无层级
- 百分比无涨跌颜色区分
- `toFixed(2)` 固定小数位显得机械
- 图表过于简陋（纯色柱子，无渐变无光泽）

### 8.2 修改方案

**1. 金额格式化增强**

```ts
// 替代简单的 formatMoney
export function formatMoneyEnhanced(value: number): { integer: string, decimal: string, unit: string } {
  if (Math.abs(value) >= 1e8) {
    return { integer: (value / 1e8).toFixed(2), decimal: '', unit: '亿' }
  }
  if (Math.abs(value) >= 1e4) {
    return { integer: (value / 1e4).toFixed(2), decimal: '', unit: '万' }
  }
  const parts = value.toFixed(2).split('.')
  return { integer: parts[0], decimal: parts[1], unit: '' }
}
```

**模板使用：**
```html
<div class="flex items-baseline gap-1">
  <span class="font-display text-3xl text-on-surface tabular-nums">{{ integer }}</span>
  <span v-if="decimal" class="font-display text-xl text-on-surface/60">.{{ decimal }}</span>
  <span v-if="unit" class="font-body text-lg text-on-surface/60 ml-1">{{ unit }}</span>
</div>
```

注意：添加 `tabular-nums` 使数字等宽，避免数字变化时布局抖动。

**2. 百分比涨跌颜色**

```html
<!-- 正数 → 红色（涨），负数 → 绿色（跌） -->
<span :class="value >= 0 ? 'text-up bg-up-light' : 'text-down bg-down-light'"
      class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-sm font-medium">
  <span class="material-symbols-outlined text-[16px]">
    {{ value >= 0 ? 'trending_up' : 'trending_down' }}
  </span>
  {{ Math.abs(value).toFixed(1) }}%
</span>
```

**3. 柱状图增加质感**

```html
<!-- 替换 CalendarPage 中的纯色柱子 -->
<div class="flex-1 h-6 bg-surface-container rounded-full overflow-hidden">
  <div class="h-full rounded-full transition-all duration-700 ease-out"
       :style="{
         width: item.percentage + '%',
         background: 'linear-gradient(90deg, var(--color-primary-400), var(--color-primary-500))',
         boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.3)'
       }" />
</div>
```

**4. 持仓卡片中金额的三级展示**

```
预测年分红 ¥1,200       ← 主数据（大号、粗体）
成本息率 4.5%           ← 次级数据（中号、有条件颜色）
市值 ¥35,000 / 成本 ¥28,000 / 份额 1,200 ← 三级数据（小号、灰色）
```

---

## 九、图标体系规范

### 9.1 修改方案

**建立图标尺寸阶梯（5 级）：**

```js
// tailwind.config.js
icon: {
  'sm': '16px',   // 内联图标、标签图标
  'md': '20px',   // 列表项图标、按钮图标
  'lg': '24px',   // 导航图标、卡片图标
  'xl': '32px',   // 页面装饰图标
  '2xl': '48px',  // 空状态图标
}
```

**全局搜索替换所有 `text-[14px]`、`text-[18px]`、`text-[20px]`、`text-[36px]` 图标尺寸为对应的 token。**

**添加图标颜色语义化：**
```html
<!-- 成功态 -->
<span class="material-symbols-outlined text-success">check_circle</span>
<!-- 警告态 -->
<span class="material-symbols-outlined text-warning">warning</span>
<!-- 信息态 -->
<span class="material-symbols-outlined text-primary-400">info</span>
<!-- 默认态 -->
<span class="material-symbols-outlined text-on-surface-variant">chevron_right</span>
```

---

## 十、组件级修改清单

### 10.1 AppHeader.vue

| 修改项 | 当前 | 改为 |
|--------|------|------|
| 背景 | 无特殊处理 | `backdrop-blur-md bg-surface/90`（毛玻璃效果） |
| 阴影触发阈值 | `scrollY > 10` | `scrollY > 4`（更快响应） |
| 阴影 | 默认 shadow | `shadow-subtle` |
| 返回按钮 | 纯文字/图标 | `w-10 h-10 rounded-full hover:bg-surface-container flex items-center justify-center` |

### 10.2 BottomNav.vue

| 修改项 | 当前 | 改为 |
|--------|------|------|
| Active 背景 | `bg-primary-container/20 rounded-full` | `bg-primary-50 rounded-xl`（更大更明显） |
| Active 文字 | `text-on-primary-container` | `text-primary-500 font-medium` |
| Active 图标 | `fill` class 切换 | `fontVariationSettings: 'FILL' 1, 'wght' 700` |
| 顶部阴影 | `shadow-[0_-4px_12px_0_rgba(0,0,0,0.05)]` | `shadow-elevated` |
| 高度 | `h-16` | 保持 |
| 底部安全区 | `safe-bottom` | 保持 |

### 10.3 DividendCard.vue

| 修改项 | 当前 | 改为 |
|--------|------|------|
| 阴影 | `shadow-md` | `shadow-card` |
| 左边框 | 无 | `border-l-[3px]` 使用持仓颜色 |
| hover | `active:scale-[0.98]` | `hover:shadow-elevated active:scale-[0.98] transition-shadow` |
| 指标网格 | 4 列无分隔 | 添加 `divide-x divide-outline-variant/10` 列间分隔 |
| 金额 | `font-headline-xl` | `font-display text-xl tabular-nums` |

### 10.4 PageState.vue

| 修改项 | 当前 | 改为 |
|--------|------|------|
| 加载态 | spinner + "加载中..." | 移除，改为在各页面用骨架屏 |
| 空状态 | inbox 图标 + "暂无数据" | 品牌插画 + 引导文案 + CTA 按钮 |
| 错误态 | error 图标 + 重试按钮 | 保持，增加错误详情折叠 |

### 10.5 HomePage.vue

| 修改项 | 当前 | 改为 |
|--------|------|------|
| Hero 阴影 | `card-shadow` | `shadow-elevated` |
| Hero 内边距 | `p-lg` | `p-2xl`（增加呼吸感） |
| Hero 数字 | 静态 | 使用 `useCountUp` 动画 |
| 指标网格 | 紧凑排列 | 增大 `gap-lg`（16px） |
| 持仓卡片 | `DividendCard` 默认 | 见 10.3 |
| colorPalette | 硬编码 hex | 使用 design token 中的语义色 |

### 10.6 CalendarPage.vue

| 修改项 | 当前 | 改为 |
|--------|------|------|
| 事件颜色 | `bg-blue-100 text-blue-700` 等硬编码 | 使用 event.registration / event.exDividend 等语义色 |
| 柱状图 | 纯色 `bg-primary` | 渐变填充 + 顶部圆角 + 光泽 |
| 年度统计卡片 | `p-[10px]` | `p-md`（12px） |
| 年份切换 | 简单文字 | 添加左右箭头按钮 + 年份选择器 |

### 10.7 DiscoverPage.vue

| 修改项 | 当前 | 改为 |
|--------|------|------|
| 总资产数字 | 静态 | `useCountUp` 动画 |
| 分类卡片颜色 | `style="background-color:#3B82F6"` | 使用 design token |
| 卡片分隔 | 同色堆叠 | 交替 surface 层级 + `space-y-xl` |
| 加载态 | `animate-spin sync 图标` | 骨架屏 |
| 品牌区域 | 渐变色块 + emoji | 品牌插画（SVG 或 CSS 图形） |

### 10.8 HoldingDetailPage.vue

| 修改项 | 当前 | 改为 |
|--------|------|------|
| Hero 数字 | 静态 | `useCountUp` 动画 |
| 回本进度条 | 纯色 `bg-primary` | 渐变填充 + 光泽 |
| 预测折线图 | 纯色 SVG | 渐变填充区域 + 数据点圆点 |
| 操作网格 | 2×2 无特殊处理 | 每个格子 hover 态 + 图标颜色区分 |

### 10.9 ProfilePage.vue

| 修改项 | 当前 | 改为 |
|--------|------|------|
| 列表项高度 | `p-md` (16px) → ~52px | `py-md` (12px) → ~44px（更紧凑） |
| 头像 | 纯文字 | 添加头像上传或默认头像 SVG |
| 会员标签 | 纯文字 | 徽章样式（渐变背景 + 图标） |

---

## 十一、全局细节打磨清单

### 必须修改（影响整体品质）

- [ ] **1. 阴影升级** — main.css 中 0.05 → 0.06/0.08/0.10，增加双层阴影
- [ ] **2. 配色统一** — 搜索所有 `bg-blue-`、`bg-green-`、`bg-red-`、`bg-yellow-`、`style="background-color:`，替换为 design token 或语义色
- [ ] **3. 字号规范** — 搜索所有 `text-[数字]px`，替换为对应 token（xs/sm/base/md/lg/xl/2xl/3xl）
- [ ] **4. 间距规范** — 搜索所有 `p-[数字]px`、`space-y-[数字]`，替换为 spacing token
- [ ] **5. 圆角规范** — 建立嵌套圆角节奏（外层 > 内层）
- [ ] **6. surface 层级应用** — 检查所有卡片堆叠场景，交替使用不同 surface 层级

### 建议修改（显著提升品质）

- [ ] **7. 数字递增动画** — 创建 `useCountUp` composable，应用到 Hero 数字
- [ ] **8. 页面切换动画** — App.vue 中 `<router-view>` 外包 `<Transition>`
- [ ] **9. 骨架屏** — 替换所有 spinner 加载态
- [ ] **10. 底部导航增强** — 图标加粗、背景色加强、过渡动画
- [ ] **11. 空状态品牌化** — 用品牌插画替代纯图标
- [ ] **12. 涨跌颜色区分** — 所有百分比和变化值使用涨红跌绿

### 锦上添花（可后续迭代）

- [ ] **13. 下拉刷新动画** — 品牌色脉冲或树苗生长动画
- [ ] **14. Toast/通知组件** — 统一的消息提示样式
- [ ] **15. 列表交错进入动画** — `<TransitionGroup>` + `transitionDelay`
- [ ] **16. 图表渐变填充** — 折线图下方区域渐变填充
- [ ] **17. 深色模式** — 当前 `darkMode: 'class'` 但未启用，补充深色色板
- [ ] **18. 触觉反馈** — 关键操作添加 `navigator.vibrate`

---

## 十二、执行顺序建议

```
第一阶段（1-2小时，立即见效）：
  ├── 修改 tailwind.config.js 色板、字号、间距、阴影
  ├── 修改 main.css 阴影定义
  ├── 全局搜索替换硬编码颜色和字号
  └── 修改 BottomNav.vue 和 AppHeader.vue

第二阶段（2-3小时，品质跃升）：
  ├── 修改 DividendCard.vue（阴影、边框、hover）
  ├── 修改 HomePage.vue（Hero 阴影、数字动画、间距）
  ├── 修改 DiscoverPage.vue（卡片分隔、骨架屏、数字动画）
  ├── 创建 useCountUp.ts composable
  └── App.vue 添加页面切换动画

第三阶段（1-2小时，细节打磨）：
  ├── 修改 CalendarPage.vue（事件颜色、图表渐变）
  ├── 修改 HoldingDetailPage.vue（数字动画、进度条）
  ├── 修改 PageState.vue（空状态品牌化）
  └── 修改 ProfilePage.vue（列表间距、会员徽章）
```

---

> **版本**: v1.0
> **适用范围**: myDca / stitch_fund_dividend_tracker 前端
> **约束**: 不改变任何功能逻辑，纯 UI 层改造
