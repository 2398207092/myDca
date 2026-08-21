# 种树 · 基金分红追踪 Vue 前端打磨文档

> **交付方**：原型构建师 · 筑原型
> **项目**：`stitch_fund_dividend_tracker`（Vue 3.4 + TailwindCSS）
> **依据**：彩格调「设计令牌精修方案」（作为唯一权威依据）
> **调性**：Modern Minimal + Soft Warm

本目录含三份可直接落地的代码文档：

| 文件 | 内容 | 落点 |
|------|------|------|
| `A-tailwind.config.js.md` | 完整建议版配置（可整体替换） | 项目根 `tailwind.config.js` |
| `B-main.css.md` | 追加到 `@layer components` 的组件配方 | `src/assets/styles/main.css` |
| `C-P0-pages.md` | P0 四页最小改动清单 + 旧令牌→新令牌映射表 | 各 `.vue` 文件 |

---

## 落地顺序建议

1. **先替换** `tailwind.config.js`（A 文档）——所有 CSS 变量 / 语义类 / 间距依赖它
2. **再追加** `main.css`（B 文档）——提供 `.num`、`.chip-*` 等全局组件类
3. **最后** 按 C 文档逐页套用新令牌 —— 从首页看板开始，逐页验收对比度与间距

> ⚠️ 提示：项目源文件不在本机文件系统，以下配置基于标准 Vue3 + Tailwind v3 项目结构撰写，令牌取值严格取自精修方案，语法与现有结构兼容。若你的 Tailwind 为 v4（CSS-first 配置），需将 A 文档中的 `theme.extend` 改为 `@theme` 块，此处默认 v3。
