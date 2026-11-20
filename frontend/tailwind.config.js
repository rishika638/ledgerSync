/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        fintech: {
          dark: '#0B0F19',
          card: '#111827',
          subtle: '#0F172A',
          border: '#1E293B',
          muted: '#94A3B8',
          text: '#F8FAFC',
          teal: '#14B8A6',
          tealDark: '#0D9488',
          blue: '#0EA5E9',
          amber: '#F59E0B',
          rose: '#F43F5E',
          emerald: '#10B981',
        }
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace']
      }
    },
  },
  plugins: [],
}
