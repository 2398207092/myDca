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
      },
      fontFamily: {
        'display': ['Plus Jakarta Sans', 'sans-serif'],
        'body': ['Work Sans', 'sans-serif'],
      },
      fontSize: {
        'xs':    ['11px', { lineHeight: '16px', fontWeight: '400' }],
        'sm':    ['12px', { lineHeight: '16px', fontWeight: '400' }],
        'base':  ['13px', { lineHeight: '20px', fontWeight: '400' }],
        'md':    ['14px', { lineHeight: '20px', fontWeight: '500' }],
        'lg':    ['16px', { lineHeight: '24px', fontWeight: '500' }],
        'xl':    ['20px', { lineHeight: '28px', fontWeight: '500' }],
        '2xl':   ['24px', { lineHeight: '32px', fontWeight: '500' }],
        '3xl':   ['32px', { lineHeight: '40px', fontWeight: '500' }],
        '4xl':   ['36px', { lineHeight: '44px', fontWeight: '600' }],
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
