import { describe, expect, it } from 'vitest'
import { isValidPhone, normalizePhone } from './phoneFormat'

describe('normalizePhone', () => {
  it('strips spaces, dashes and parentheses -- the shapes a phone keypad\'s own autofill adds', () => {
    expect(normalizePhone('+7 999 123-45-67')).toBe('+79991234567')
    expect(normalizePhone('+7 (999) 123-45-67')).toBe('+79991234567')
  })

  it('never guesses a country code for a number missing its "+" prefix', () => {
    // E-001 (docs/PIOS_PRODUCT_EVIDENCE.md): the everyday Russian domestic
    // format -- deliberately left as-is, not silently rewritten to +7...,
    // since that would be this file inventing an assumption about the
    // user's country it has no basis for.
    expect(normalizePhone('89991234567')).toBe('89991234567')
  })

  it('leaves an already-valid phone unchanged', () => {
    expect(normalizePhone('+79991234567')).toBe('+79991234567')
  })
})

describe('isValidPhone', () => {
  it('accepts the exact shape the backend requires (mirrors backend/identity Phone.kt)', () => {
    expect(isValidPhone('+79991234567')).toBe(true)
    expect(isValidPhone('+15555550100')).toBe(true)
  })

  it('rejects a phone missing its "+" prefix', () => {
    expect(isValidPhone('89991234567')).toBe(false)
  })

  it('rejects a phone with formatting characters still present', () => {
    expect(isValidPhone('+7 999 123-45-67')).toBe(false)
  })

  it('rejects a phone starting with 0 after the "+"', () => {
    expect(isValidPhone('+0991234567')).toBe(false)
  })

  it('rejects a phone shorter than 7 digits total after the "+"', () => {
    expect(isValidPhone('+799912')).toBe(false)
  })

  it('rejects an empty string', () => {
    expect(isValidPhone('')).toBe(false)
  })
})
