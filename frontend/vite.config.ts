import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  base: '/ai-hub/',
  plugins: [react()],
  server: {
    proxy: {
      '/ai-hub/api': {
        target: 'http://127.0.0.1:8090',
        rewrite: path => path.replace(/^\/ai-hub/, ''),
      },
      '/ai-hub/actuator': {
        target: 'http://127.0.0.1:8090',
        rewrite: path => path.replace(/^\/ai-hub/, ''),
      },
    },
  },
})
