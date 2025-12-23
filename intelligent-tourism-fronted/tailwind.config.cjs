/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './index.html',
    './src/**/*.{vue,js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      dropShadow: {
        glow: '0 10px 30px rgba(56, 189, 248, 0.35)',
      },
      backgroundImage: {
        'grid-glow':
          'radial-gradient(circle at 1px 1px, rgba(255,255,255,0.08) 1px, transparent 0)',
      },
    },
  },
  plugins: [require('@tailwindcss/typography')],
};
