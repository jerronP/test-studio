import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/record': 'http://localhost:8080',
      '/ai-agent': 'http://localhost:8080',// Your Quarkus backend address
    }
  }
})
