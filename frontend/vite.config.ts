/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icon.svg'],
      manifest: {
        name: 'MaterialBook',
        short_name: 'MaterialBook',
        start_url: '/',
        display: 'standalone',
        background_color: '#ffffff',
        theme_color: '#1677ff',
        icons: [
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any maskable' },
        ],
      },
      // App shell only: precache static assets, never touch /api.
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png}'],
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api\//],
        // The recharts chunk (~380 kB) is not precached: it is cached on first use instead (static hashed asset, never /api).
        globIgnores: ['**/DashboardChart-*.js'],
        runtimeCaching: [
          { urlPattern: ({ url }) => url.pathname.startsWith('/assets/DashboardChart-'), handler: 'CacheFirst', options: { cacheName: 'lazy-chunks' } },
        ],
      },
    }),
  ],
  build: {
    rolldownOptions: {
      output: {
        // Stable vendor groups so the entry stays small and vendor chunks cache across deploys. antd is deliberately NOT forced into one chunk, so components used only by lazy pages (Table, DatePicker, ...) stay out of the initial load; recharts stays in the lazy chart chunk.
        codeSplitting: {
          groups: [
            { name: 'vendor-react', test: /node_modules[\\/](react|react-dom|react-router|react-router-dom|scheduler|@remix-run)[\\/]/, priority: 40 },
            { name: 'vendor-data', test: /node_modules[\\/](@tanstack|axios)[\\/]/, priority: 30 },
            { name: 'vendor-dayjs', test: /node_modules[\\/]dayjs[\\/]/, priority: 30 },
          ],
        },
      },
    },
  },
  server: { proxy: { '/api': 'http://localhost:8080' } },
  test: { environment: 'jsdom', setupFiles: ['./src/test/setup.ts'], globals: true },
});
