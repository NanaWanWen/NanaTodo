/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#1677FF',
          deep: '#0958D9',
          ink: '#1C2B45'
        },
        surface: {
          page: '#F5F7FA',
          card: '#FFFFFF',
          sider: '#001529'
        },
        ink: {
          DEFAULT: '#1C2B45',
          muted: '#5A6B85'
        },
        functional: {
          success: '#52C41A',
          danger: '#FF4D4F',
          warning: '#FAAD14',
          neutral: '#8C8C8C'
        }
      },
      fontFamily: {
        sans: ['PingFang SC', 'Microsoft YaHei', 'system-ui', 'sans-serif']
      },
      borderRadius: {
        card: '8px'
      },
      boxShadow: {
        card: '0 1px 2px rgba(28,43,69,0.04), 0 4px 16px rgba(28,43,69,0.06)'
      },
      transitionDuration: {
        160: '160ms'
      }
    }
  },
  plugins: [require('tailwindcss-animate')]
}
