import { describe, expect, it } from 'vitest'
import { PRODUCTION_PUBLIC_ORIGIN, resolvePublicOrigin } from './publicOrigin.mjs'

describe('public origin', () => {
  it('uses the canonical production origin by default', () => {
    expect(PRODUCTION_PUBLIC_ORIGIN).toBe('https://piosapp.ru')
    expect(resolvePublicOrigin(undefined)).toBe('https://piosapp.ru')
    expect(resolvePublicOrigin('   ')).toBe('https://piosapp.ru')
  })

  it('keeps an explicit local-development override', () => {
    expect(resolvePublicOrigin('http://localhost:5173')).toBe('http://localhost:5173')
  })
})
