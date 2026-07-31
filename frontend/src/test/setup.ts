// Sprint 6 (Passenger Entry-Path Failure Handling): global test setup for
// vitest — registers `@testing-library/jest-dom`'s DOM matchers
// (e.g. `toBeInTheDocument`) once for every test file.
import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

// `@testing-library/react`'s own automatic cleanup only registers itself
// against a *global* `afterEach` (e.g. when `test.globals: true` is set).
// This project's `vitest.config.ts` deliberately leaves globals off (each
// test file imports `describe`/`it`/`expect` explicitly), so cleanup is
// wired up here instead -- without it, one test's rendered DOM leaks into
// the next test in the same file.
afterEach(() => {
  cleanup()
})
