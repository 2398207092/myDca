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
        // 对象结构：DEFAULT 兼容 bg-brand；light=soft 兼容旧 bg-brand-light
        brand: {
          DEFAULT: '#1A6B56',
          strong:  '#0F4F40',
          soft:    '#E8F5F0',
          light:   '#E8F5F0',  // 兼容别名，等价 soft
          dim:     '#A8D5C5',  // 兼容旧 brand-dim
        },

        // 页面/卡片背景
        'page-bg': '#F6F5F3',
        'card-bg': '#FFFFFF',
        'card-alt': '#F0EFED',

        // 文字（tertiary 修正：#A09E9B → #6F6F6E，达 WCAG AA 4.8:1）
        'text-primary': '#1C1B1A',
        'text-secondary': '#6B6A68',
        'text-tertiary': '#6F6F6E',

        // 装饰
        'border-light': '#E8E7E5',
        'progress-bg': '#E8E7E5',

        // 语义（仅在需要时使用）
        // alert 修正：#C25A3E → #B84E33，对比度 5.0:1
        alert: {
          DEFAULT: '#B84E33',
          soft:    '#FBE9E3',
          light:   '#FBE9E3',
        },
        'success': '#1A6B56',
        'warning': '#B8860B',
        'error': '#BA1A1A',

        // 投资语义色：涨/跌/分红金/平盘
        pos: {                    // 正收益（绿）— 继承 brand 色值
          DEFAULT: '#1A6B56',
          strong:  '#0F4F40',
          soft:    '#E8F5F0',
          light:   '#E8F5F0',
        },
        neg: {                    // 负收益（红）
          DEFAULT: '#BA1A1A',
          strong:  '#A61616',
          soft:    '#FBEAE7',
          light:   '#FBEAE7',
        },
        gold: {                   // 分红金
          DEFAULT: '#8A6B08',
          strong:  '#6B5306',
          soft:    '#F7F0DC',
          light:   '#F7F0DC',
        },
        flat: {                   // 平盘
          DEFAULT: '#6B6A68',
          soft:    '#EFEFEE',
          light:   '#EFEFEE',
        },
      },
      boxShadow: {
        'card': '0 1px 3px rgba(0,0,0,0.04)',
        'subtle': '0 1px 4px rgba(0,0,0,0.06)',
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
        // section 间距：8pt 网格，统一页面段间距
        'section': '32px',
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
