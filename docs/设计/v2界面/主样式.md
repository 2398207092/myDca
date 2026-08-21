# B. main.css —— 追加到 `@layer components` 的组件配方

> **修订记录：v2，修正 P0-1 兼容别名、P0-2 间距单位歧义，采纳 toast/num 双源优化**
>
> 追加到 `src/assets/styles/main.css` 的 `@layer components` 内（建议放在末尾）。
> 提供全局可复用组件类，供所有 `.vue` 直接引用，保证一致性。
> 动效遵循 `prefers-reduced-motion` 兜底（见文末）。
>
> **v2 关键改动**：`.toast-success/error` 去掉左侧彩色竖条（Anti-Slop 签名），改为纯浅底 + 深色文字；`amber` 色组更名 `gold`（与 A 文档一致）；数字层级仍由本文件 `.num-l*` 单一数据源负责。

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
     成功 = 浅绿底 + 深绿字；失败 = 浅红底 + 深红字；中性 = 深底浅字。
     均以背景 + 文字色表意，不用左侧色条。 */
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

---

## 使用示例（在任意 `.vue` 中）

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
