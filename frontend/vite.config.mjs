import { fileURLToPath, URL } from 'node:url';

import { PrimeVueResolver } from '@primevue/auto-import-resolver';
import vue from '@vitejs/plugin-vue';
import Components from 'unplugin-vue-components/vite';
import { defineConfig } from 'vite';

// https://vitejs.dev/config/
export default defineConfig({
    optimizeDeps: {
        noDiscovery: true
    },
    plugins: [
        vue(),
        Components({
            resolvers: [PrimeVueResolver()]
        })
    ],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url))
        }
    },
    base: '/',
    build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false
  },
   server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: [
    'localhost',
    '.ngrok-free.app',  // 允许所有 ngrok-free.app 子域名
    '.ngrok-free.dev',  // 允许所有 ngrok-free.dev 子域名
    '.ngrok.io',        // 允许所有 ngrok.io 子域名
  ],
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true
      }
    }
  }
});
