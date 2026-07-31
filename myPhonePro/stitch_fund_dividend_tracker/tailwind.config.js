/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: [
    './index.html',
    './src/**/*.{vue,ts,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        // 品牌色 — 深松绿（沉稳、生态感、不刺眼）
        'brand': '#1A6B56',
        'brand-light': '#E8F5F0',
        'brand-dim': '#A8D5C5',

        // 页面/卡片背景
        'page-bg': '#F6F5F3',
        'card-bg': '#FFFFFF',
        'card-alt': '#F0EFED',

        // 文字
        'text-primary': '#1C1B1A',
        'text-secondary': '#6B6A68',
        'text-tertiary': '#A09E9B',

        // 装饰
        'border-light': '#E8E7E5',
        'progress-bg': '#E8E7E5',

        // 语义（仅在需要时使用）
        'alert': '#C25A3E',
        'success': '#1A6B56',
        'warning': '#B8860B',
        'error': '#BA1A1A',
      },
      boxShadow: {
        'card': '0 1px 3px rgba(0,0,0,0.04)',
        'elevated': '0 2px 8px rgba(0,0,0,0.05)',
        'overlay': '0 4px 16px rgba(0,0,0,0.08)',
      },
      borderRadius: {
        'sm': '6px',
        'md': '10px',
        'lg': '14px',
        'xl': '18px',
        'full': '9999px',
      },
      spacing: {
        'xs': '4px',
        'sm': '8px',
        'md': '12px',
        'lg': '16px',
        'xl': '20px',
        '2xl': '24px',
        '3xl': '32px',
        'gutter': '16px',
        // section 间距：用于页面中 section 之间的大间隔，形成"紧密 vs 宽松"的节奏
        'section': '24px',
      },
      // z-index 语义化分层（按 interaction-design.md 规范）
      // 用法：z-dropdown / z-sticky / z-modal 等
      zIndex: {
        'base': '0',
        'dropdown': '100',
        'sticky': '200',
        'header': '250',
        'modal-backdrop': '300',
        'modal': '400',
        'toast': '500',
        'tooltip': '600',
      },
      fontFamily: {
        'display': ['Plus Jakarta Sans', 'sans-serif'],
        'body': ['Work Sans', 'sans-serif'],
      },
      fontSize: {
        // 全字号上提一档：body 14px 起步，满足移动端可读性阈值
        // weight 分档：body 400 / label 500 / heading 600-700，多维信号区分层级
        'xs':    ['12px', { lineHeight: '16px', fontWeight: '400' }],   // 辅助说明
        'sm':    ['13px', { lineHeight: '18px', fontWeight: '400' }],   // 次要文字
        'base':  ['14px', { lineHeight: '20px', fontWeight: '400' }],   // 正文 body
        'md':    ['15px', { lineHeight: '22px', fontWeight: '500' }],   // 强调文字/标签
        'lg':    ['17px', { lineHeight: '24px', fontWeight: '600' }],   // 小标题
        'xl':    ['21px', { lineHeight: '28px', fontWeight: '600' }],   // 标题
        '2xl':   ['25px', { lineHeight: '32px', fontWeight: '700' }],   // 大标题
        '3xl':   ['33px', { lineHeight: '40px', fontWeight: '700' }],   // Hero 数字
        '4xl':   ['38px', { lineHeight: '44px', fontWeight: '700' }],   // 大数字焦点
      },
    },
  },
  safelist: [
    'text-success',
    'bg-success/10',
    'bg-card-alt',
  ],
  plugins: [],
}
