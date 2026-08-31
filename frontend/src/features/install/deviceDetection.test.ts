import { afterEach, describe, expect, it } from 'vitest'
import { detectPlatform, isChrome, isSafari, isStandalone } from './deviceDetection'

const IPHONE_SAFARI_UA =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1'
const IPHONE_CHROME_UA =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/125.0.6422.80 Mobile/15E148 Safari/604.1'
const ANDROID_CHROME_UA =
  'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36'
const DESKTOP_CHROME_UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36'
const DESKTOP_EDGE_UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36 Edg/125.0.0.0'

function setUserAgent(ua: string) {
  Object.defineProperty(navigator, 'userAgent', { value: ua, configurable: true })
}

const originalUserAgent = navigator.userAgent

afterEach(() => {
  setUserAgent(originalUserAgent)
  Object.defineProperty(window, 'matchMedia', { value: undefined, configurable: true })
  Object.defineProperty(navigator, 'standalone', { value: undefined, configurable: true })
})

describe('detectPlatform', () => {
  it('detects an iPhone', () => {
    setUserAgent(IPHONE_SAFARI_UA)
    expect(detectPlatform()).toBe('ios')
  })

  it('detects iPhone Chrome as iOS too (platform, not browser)', () => {
    setUserAgent(IPHONE_CHROME_UA)
    expect(detectPlatform()).toBe('ios')
  })

  it('detects Android', () => {
    setUserAgent(ANDROID_CHROME_UA)
    expect(detectPlatform()).toBe('android')
  })

  it('detects desktop', () => {
    setUserAgent(DESKTOP_CHROME_UA)
    expect(detectPlatform()).toBe('desktop')
  })
})

describe('isSafari', () => {
  it('is true for real iPhone Safari', () => {
    setUserAgent(IPHONE_SAFARI_UA)
    expect(isSafari()).toBe(true)
  })

  it('is false for Chrome on iPhone (CriOS), even though "Safari" appears in its own UA', () => {
    setUserAgent(IPHONE_CHROME_UA)
    expect(isSafari()).toBe(false)
  })
})

describe('isChrome', () => {
  it('is true for desktop Chrome', () => {
    setUserAgent(DESKTOP_CHROME_UA)
    expect(isChrome()).toBe(true)
  })

  it('is false for Edge, even though "Chrome" appears in its own UA', () => {
    setUserAgent(DESKTOP_EDGE_UA)
    expect(isChrome()).toBe(false)
  })

  it('is true for Android Chrome', () => {
    setUserAgent(ANDROID_CHROME_UA)
    expect(isChrome()).toBe(true)
  })
})

describe('isStandalone', () => {
  it('is false with no display-mode support and no navigator.standalone', () => {
    expect(isStandalone()).toBe(false)
  })

  it('is true when display-mode: standalone matches', () => {
    Object.defineProperty(window, 'matchMedia', {
      value: (query: string) => ({ matches: query === '(display-mode: standalone)' }),
      configurable: true,
    })
    expect(isStandalone()).toBe(true)
  })

  it('is true when navigator.standalone is true (iOS Safari legacy signal)', () => {
    Object.defineProperty(navigator, 'standalone', { value: true, configurable: true })
    expect(isStandalone()).toBe(true)
  })
})
