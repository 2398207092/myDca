# 落地实施指引

> **目标**：将设计令牌精修方案落地到 `stitch_fund_dividend_tracker` 项目。
> **预计工时**：0.5–1 人日（P0 四页）
> **前置条件**：项目为 Vue 3.4 + TailwindCSS v3.4，源文件在开发者本地。

---

## 一、落地顺序（严格按序）

```
Step 1  替换 tailwind.config.js（A 文档）
  ↓     —— 所有令牌依赖此文件，必须先完成
Step 2  追加 main.css 组件配方（B 文档）
  ↓     —— 提供全局组件类，页面引用前必须就位
Step 3  按 C 文档改 P0 四页
  ↓     HomePage → CalendarPage → HoldingDetailPage → DiscoverPage
Step 4  逐页验收（对照验收清单）
```

---

## 二、Step 1：替换 tailwind.config.js

**操作**：用 `A-tailwind.config.js.md` 中的代码块**整体替换**项目根目录的 `tailwind.config.js`。

**注意事项**：

1. `content` 路径确认与项目结构一致（默认 `./index.html` + `./src/**/*.{vue,js,ts,jsx,tsx}`）
2. `plugins` 数组：若项目已装 `tailwindcss-animate` 可保留 `require('tailwindcss-animate')`；否则删除该行
3. 替换后执行一次构建确认无报错：`npm run build` 或 `npm run dev`
4. **兼容性验证**：全局搜索 `bg-brand-light`，确认编译后仍生效（走 `light` 别名 = `soft`）

> ⚠️ 若项目是 Tailwind v4（CSS-first 配置），将 `theme.extend` 内层移到 `@theme` 块，语法格式参考 v4 迁移指南。

---

## 三、Step 2：追加 main.css 组件配方

**操作**：将 `B-main.css.md` 中的 CSS 代码块追加到 `src/assets/styles/main.css` 的 `@layer components { ... }` 内（建议放在末尾，`}` 之前）。

**注意事项**：

1. 确认 `@layer components` 已存在；若不存在，在 `@tailwind components;` 之后添加
2. `prefers-reduced-motion` 媒体查询块放在 `@layer components` **之外**（文件末尾即可）
3. 追加后确认 `.num`、`.card`、`.btn-primary` 等类名在浏览器 DevTools 中可查到
4. 组件类中的硬编码色值（如 `#1a6b56`）与 A 文档令牌一致，后续如需改色两处同步

---

## 四、Step 3：按 C 文档改 P0 四页

### 通用操作（所有页面）

按 C 文档「一、全局映射表」执行搜索替换：

1. **颜色**：全局搜索 `#A09E9B` → `#6F6F6E`；`#C25A3E` → `#B84E33`；`amber` → `gold`；手写 `#2E7D32`/`green-600` → `pos`；手写 `#D32F2F`/`red-600` → `neg`
2. **间距**：搜索 `p-16`/`p-24` → 改为 `p-4`/`p-6`；页面容器加 `.gutter-x` + `.section-y`
3. **数字**：所有金额/百分比数字加 `class="num"`，按层级叠加 `num-l1`~`num-l4`
4. **组件**：手写卡片→`.card`；手写按钮→`.btn-*`；手写标签→`.chip-*`；手写列表行→`.list-item`；手写输入框→`.input`；手写空状态→`.empty`；手写 toast→`.toast-*`

### 分页优先级

| 顺序 | 页面 | 重点改动 |
|------|------|----------|
| ① | **HomePage.vue** | 头部累计收益改 `num num-l1 amount-hl pos/neg`；卡片统一 `.card` + `.card-pad-lg`；持仓列表行 `.list-item` + `.chip-*`；空状态 `.empty` |
| ② | **CalendarPage.vue** | 分红标记改 `gold`/`chip-gold`（不再用 amber）；金额 `num num-l2/num-l3` + `t-gold`；月份切换 `btn-secondary` |
| ③ | **HoldingDetailPage.vue** | 成本/市值/收益三区统一 `num` 层级 + 涨跌配色；明细表金额列 `num` 等宽；买入/卖出 `btn-primary`/`btn-secondary`；操作反馈 `.toast` |
| ④ | **DiscoverPage.vue** | 搜索结果 `.card` + `.list-item`；收益率标签 `chip-*`；搜索框 `.input`；空结果 `.empty` |

---

## 五、Step 4：验收清单

每页提交前逐项核对：

- [ ] 无 `#A09E9B` 残留（tertiary 已改 `#6F6F6E`）
- [ ] 无 `#C25A3E` 残留（alert 已改 `#B84E33`）
- [ ] 无 `bg-brand-light` / `text-brand-light` 残留（已改 `brand-soft`，`light` 别名兜底）
- [ ] 无 `amber` 残留（已改 `gold`）
- [ ] 无 `p-16` / `p-24` 滥用（间距严格用 `p-4` / `p-6` / `.gutter-x` / `.section-y`）
- [ ] 所有金额带 `num` 等宽、涨跌配色正确（pos/neg/gold/flat）
- [ ] 卡片 / 按钮 / 标签 / 列表已统一组件类
- [ ] 横间距 gutter16、纵 section32 一致
- [ ] 无大面积 warning 色作正文
- [ ] toast 无左侧彩色竖条（纯浅底 + 深色文字）
- [ ] 无编造统计数字；数据用 `—` 占位
- [ ] `prefers-reduced-motion` 生效（减弱动效）

---

## 六、技术约束速查

| 约束 | 说明 | 违反后果 |
|------|------|----------|
| **Tailwind v3.4** | 默认 `theme.extend` 语法 | v4 需改 `@theme` 块 |
| **brand-light 兼容别名** | `light` = `soft`，旧类名不失效 | 对象化后旧类名静默失效 |
| **禁止 p-16 / p-24** | Tailwind spacing 16=64px、24=96px | 4 倍级布局错误 |
| **数字单一数据源** | 层级由 B 文档 `.num-l*` 负责 | A/B 双写导致不一致 |
| **prefers-reduced-motion** | B 文档已含全局兜底 | 不遵守系统减弱动效设置 |

---

## 七、回滚方案

若落地后出现严重问题：

1. `tailwind.config.js` — git revert 回旧版即可，组件类不受影响（色值已硬编码在 CSS 中）
2. `main.css` — 删除追加段即可，页面回到手写样式
3. `.vue` 页面 — 建议每页一个 commit，可逐页 revert
