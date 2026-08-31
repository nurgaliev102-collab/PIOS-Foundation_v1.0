import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync } from 'fs'
import { join } from 'path'

/**
 * Section 20's own explicit requirement, made a real, automated check
 * rather than a claim in a report: every source file in this feature
 * (excluding tests) must import nothing from `api/apiClient`, and must
 * never call the raw `fetch`/`request` globals. Mirrors
 * `DriverOnboarding.test.tsx`'s own forbidden-content-scan convention,
 * applied to imports instead of marketing copy.
 */
describe('install feature network safety', () => {
  const dir = join(__dirname)
  const sourceFiles = readdirSync(dir).filter(
    (name) => (name.endsWith('.ts') || name.endsWith('.tsx')) && !name.endsWith('.test.ts') && !name.endsWith('.test.tsx')
  )

  it('found the expected source files (sanity check that this test is not silently scanning nothing)', () => {
    expect(sourceFiles.length).toBeGreaterThan(0)
    expect(sourceFiles).toContain('InstallPIOS.tsx')
  })

  it.each(sourceFiles)('%s never imports api/apiClient', (fileName) => {
    const content = readFileSync(join(dir, fileName), 'utf-8')
    expect(content).not.toMatch(/from ['"].*api\/apiClient['"]/)
  })

  it.each(sourceFiles)('%s never calls the raw fetch() global', (fileName) => {
    const content = readFileSync(join(dir, fileName), 'utf-8')
    expect(content).not.toMatch(/\bfetch\s*\(/)
  })
})
