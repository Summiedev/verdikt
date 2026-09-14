import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // sockjs-client's browser bundle still references Node's `global` name.
  define: {
    global: 'globalThis',
  },
})
