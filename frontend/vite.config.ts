import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import { loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  if (mode === 'production') {
    const apiBaseUrl = loadEnv(mode, process.cwd(), '').VITE_API_BASE_URL
    let parsedUrl: URL

    try {
      parsedUrl = new URL(apiBaseUrl)
    } catch {
      throw new Error('Production builds require a valid VITE_API_BASE_URL')
    }

    if (!['http:', 'https:'].includes(parsedUrl.protocol)) {
      throw new Error('Production VITE_API_BASE_URL must use HTTP or HTTPS')
    }
  }

  return {
    plugins: [react()],
    test: {
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
      css: true,
    },
  }
})
