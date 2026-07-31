import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Sprint 6 (Passenger Entry-Path Failure Handling): this project's first
// automated frontend tests. Kept as its own config, separate from
// `vite.config.ts` (which also wires up `vite-plugin-pwa`, a build/dev-only
// concern with no bearing on unit tests), so adding a test runner does not
// touch the existing dev/build configuration at all.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
