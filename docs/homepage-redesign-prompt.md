# 首页重构提示词 — 「种树」个人资产看板

> **目标**：将首页从"炒股软件式仪表盘"重构为"个人资产收息看板"
> **对标风格**：Monobank / Apple 钱包 / 记账类 App — 干净、温和、不刺眼、关注"你自己的钱"
> **约束**：不改变任何功能逻辑，不增删 API 调用，不改变数据来源

---

## 一、当前问题（用户原话整理）

1. **整体感觉不对** — 不像个人资产看板，像炒股软件的仪表盘
2. **布局不协调** — Hero 卡片的三栏数字（连续收息/预测分红/10年收益）左右没对齐
3. **卡片太大太笨重** — 信息塞得太满，缺乏呼吸感
4. **信息展示单调** — 全是文字+数字堆砌，缺少图形化表达
5. **色彩不对** — 亮橙色太扎眼，有"促销感"，不是"温和的个人资产管理"的感觉
6. **按钮功能要保留** — 所有现有交互（点击持仓、点击覆盖、FAB、指标设置等）保持不变

---

## 二、重构后的页面布局（从上到下）

### 2.1 整体布局

```
┌──────────────────────────────────────┐
│  AppHeader (毛玻璃顶栏)               │
│  🎋 种树                     🔍      │
├──────────────────────────────────────┤
│                                       │
│  [公告横幅 — 轻量、紧凑]               │
│                                       │
│  [核心大数字 — 页面视觉锚点]            │
│  ┌────────────────────────────┐       │
│  │  ¥ 12,800                   │       │
│  │  预测年度分红                │       │
│  │  ┌───────────┐ ┌────────┐  │       │
│  │  │ 365天连续  │ │ 10年×  │  │       │
│  │  │ 收息       │ │ 预期   │  │       │
│  │  └───────────┘ └────────┘  │       │
│  └────────────────────────────┘       │
│                                       │
│  [分红覆盖 — 环形进度条]              │
│  ┌─────┬─────┬─────┬─────┬─────┐     │
│  │ 🍜  │ 🚇  │ ☕  │ 🏠  │ +  │     │
│  │ 已   │ 已   │ 进   │ 未   │ 添  │     │
│  │ 覆   │ 覆   │ 行   │ 覆   │ 加  │     │
│  │ 盖   │ 盖   │ 中   │ 盖   │     │     │
│  └─────┴─────┴─────┴─────┴─────┘     │
│                                       │
│  [持仓列表 — 精简卡片]                 │
│  ┌────────────────────────────┐       │
│  │ ■ 沪深300ETF               │       │
│  │   预测分红 ¥1,200/年        │       │
│  │   ────── 圆环进度条 ──────  │       │
│  └────────────────────────────┘       │
│  ┌────────────────────────────┐       │
│  │ ■ 标普500ETF               │       │
│  │   ...                     │       │
│  └────────────────────────────┘       │
│                                       │
│                    [+ FAB 添加持仓]     │
├──────────────────────────────────────┤
│  BottomNav                            │
└──────────────────────────────────────┘
```

### 2.2 布局规则

```yaml
page_layout:
  max_width: "600px"
  horizontal_padding: "16px (gutter)"
  section_spacing: "20px"
  
  hero_card:
    - "不要三栏均分布局"  # 当前问题
    - "左侧或居中展示核心大数字（预测年度分红），作为页面视觉锚点"
    - "次要指标（连续收息天数、10年预期）以 Chip/标签 形式放在大数字下方或右侧"
    - "展开指标区域保持，但展开后使用 2 列布局而非 3 列"
    - "内部间距增大，减少信息密度"
  
  coverage_section:
    - "保留环形进度展示"
    - "缩小卡片高度，更紧凑"
    - "已覆盖/进行中/未覆盖的状态标签用不同明度的灰色区分，不要用鲜艳色"
  
  holding_list:
    - "卡片高度缩小，减少内边距"
    - "每个持仓卡片内：左侧名称+代码，右侧预测分红金额"
    - "下方用一条简短的进度条或小环形图展示回本进度"
    - "去掉 4 列指标网格（市值/成本/份额/股息率），太占空间且不是首页需要的信息"
```

---

## 三、配色方案

### 3.1 核心色板

```yaml
palette:
  description: "温和、沉稳、不刺眼的个人资产管理风格"
  
  brand: "#1A6B56"        # 深松绿 — 取代亮橙色，沉稳且有"种树"的生态感
  brand_light: "#E8F5F0"  # 浅松绿 — 背景、标签
  brand_dim: "#A8D5C5"    # 柔绿 — 次要元素
  
  surface:
    page_bg: "#F6F5F3"    # 暖灰 — 页面背景
    card_bg: "#FFFFFF"    # 白色 — 卡片
    card_alt: "#F0EFED"   # 浅灰 — 次要卡片
  
  text:
    primary: "#1C1B1A"    # 近黑 — 主文字
    secondary: "#6B6A68"  # 中灰 — 次级文字
    tertiary: "#A09E9B"   # 浅灰 — 辅助文字
  
  accent:
    progress: "#1A6B56"   # 进度条绿色
    progress_bg: "#E8E7E5" # 进度条背景
    alert: "#C25A3E"      # 警告/注意（仅在必要时使用，不作为主色）
```

### 3.2 颜色使用规则

```yaml
color_rules:
  - "整页不使用红色表示涨、绿色表示跌"  # 这不是炒股软件
  - "所有数据标签使用 text-secondary 灰色"
  - "唯一使用 brand 色的地方：核心大数字、进度条、活跃交互态"
  - "卡片之间使用极浅的 border 或不同的 surface 层级区分，而非阴影"
  - "阴影保持微妙，不抢眼（0.03-0.05 透明度）"
```

---

## 四、字体与排版

```yaml
typography:
  hero_number:
    font: "Plus Jakarta Sans"
    size: "36px"
    weight: "600"
    color: "brand (#1A6B56)"
    feature: "tabular-nums (等宽数字)"
  
  hero_label:
    font: "Work Sans"
    size: "13px"
    weight: "400"
    color: "secondary (#6B6A68)"
  
  section_title:
    font: "Work Sans"
    size: "15px"
    weight: "500"
    color: "primary (#1C1B1A)"
  
  card_title:
    font: "Work Sans"
    size: "14px"
    weight: "500"
    color: "primary"
  
  card_value:
    font: "Work Sans"
    size: "14px"
    weight: "600"
    color: "primary"
    feature: "tabular-nums"
  
  card_label:
    font: "Work Sans"
    size: "11px"
    weight: "400"
    color: "tertiary (#A09E9B)"
```

---

## 五、组件级修改清单

### 5.1 HeroCard — 完全重做

```yaml
component: HeroCard
location: HomePage.vue 顶部卡片

layout:
  type: "垂直单列（非三栏均分）"
  padding: "24px 20px"
  children:
    - line1: "大数字 + 单位/标签"
      description: "页面核心：预测年度分红"
      style: |
        ¥12,800      ← font-display text-4xl font-semibold text-brand
        预测年度分红    ← font-body text-sm text-secondary
        完全居中或左侧对齐，不要三栏均分
    - line2: "两个 Chip 标签并排"
      description: "次要指标作为标签"
      style: |
        [📅 365天连续收息] [📈 10年预期 ×3.2倍]
        使用 rounded-full 药丸标签
        bg-brand_light text-brand 小字号
    - line3: "展开指标区域（折叠态）"
      description: "保留原有的折叠/展开功能"
      style: |
        展开后使用 2 列网格，每格左侧图标+数字+标签
        指标间用极浅分割线区分
```

### 5.2 CoverageSection — 精简

```yaml
component: CoverageSection
location: Hero 下方

changes:
  - "移除圆环 SVG（复杂度太高，换为简洁的百分比条）"
  - "每个支出项使用紧凑卡片：左侧 emoji + 名称，右侧进度条 + 状态标签"
  - "已覆盖=绿色圆点+文字，进行中=橙色圆点+文字，未覆盖=灰色圆点+文字"
  - "整体高度缩减 30%"
```

### 5.3 HoldingCard — 紧凑化

```yaml
component: HoldingCard
location: 持仓列表

changes:
  - "移除内层的 4 列指标网格（市值/成本/份额/股息率）"
  - "保留：左侧颜色圆点 + 名称 + 代码"
  - "保留：右侧预测分红金额"
  - "新增：底部一条细进度条（回本进度）"
  - "内边距从 p-lg(16px) 缩减为 p-md(12px)"
  - "卡片高度从 ~120px 缩减到 ~70px"
  - "hover: 轻微上浮 + 左边框变色"
```

### 5.4 NoticeBanner — 轻量化

```yaml
component: NoticeBanner
location: Hero 上方

changes:
  - "移除边框和背景色块"
  - "改为纯文字 + 左侧小图标"
  - "高度缩减到 36px"
  - "文字颜色使用 text-secondary"
```

---

## 六、执行步骤

```yaml
steps:
  step1:
    action: "修改 tailwind.config.js"
    changes:
      - "brand 色改为 #1A6B56（深松绿），删除亮橙色"
      - "建立 brand_light/brand_dim 色阶"
      - "页面背景改为暖灰 #F6F5F3"
      - "卡片背景保持白色"
      - "阴影透明度降低到 0.03-0.05"
      - "字号新增 4xl: 36px"
  
  step2:
    action: "完全重写 HomePage.vue 的 template 结构"
    changes:
      - "Hero 卡片从三栏均分 → 单列核心大数字 + Chip 标签"
      - "覆盖区精简为紧凑进度条"
      - "持仓卡片移除指标网格，改为名称+金额+进度条"
  
  step3:
    action: "修改 DividendCard.vue"
    changes:
      - "移除内部指标网格 div"
      - "添加底部回本进度条"
      - "缩减内边距"
  
  step4:
    action: "修改 BottomNav.vue"
    changes:
      - "Active 颜色改为 brand 色"
  
  step5:
    action: "修改 AppHeader.vue"
    changes:
      - "标题颜色改为 brand 色"
```

---

## 七、样式规则速查

```css
/* 新的品牌色 */
.bg-brand { background-color: #1A6B56; }
.text-brand { color: #1A6B56; }
.bg-brand-light { background-color: #E8F5F0; }
.text-brand-dim { color: #A8D5C5; }

/* 新的背景色 */
.bg-page { background-color: #F6F5F3; }

/* 新的阴影（更微妙） */
.shadow-card { box-shadow: 0 1px 3px rgba(0,0,0,0.04); }
.shadow-elevated { box-shadow: 0 2px 8px rgba(0,0,0,0.05); }

/* 核心大数字 */
.hero-number { 
  font-family: 'Plus Jakarta Sans', sans-serif;
  font-size: 36px;
  font-weight: 600;
  color: #1A6B56;
  font-variant-numeric: tabular-nums;
}

/* Chip 标签 */
.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border-radius: 999px;
  background: #E8F5F0;
  color: #1A6B56;
  font-size: 12px;
}

/* 精简进度条 */
.progress-bar {
  height: 4px;
  border-radius: 2px;
  background: #E8E7E5;
}
.progress-bar-fill {
  height: 100%;
  border-radius: 2px;
  background: #1A6B56;
  transition: width 0.5s ease;
}
```
