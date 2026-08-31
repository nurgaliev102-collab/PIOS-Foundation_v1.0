/**
 * PIOS Install v1 (Product Owner exception — see `InstallPIOS.tsx`'s own
 * doc comment for the full note on why this Sprint runs without a prior
 * `E-NNN`/registered hypothesis).
 *
 * Pure, side-effect-free device/browser signals `InstallPIOS.tsx` and
 * `InstallInstructions.tsx` use to decide which instructions to show.
 * Every function is best-effort `navigator.userAgent` sniffing — there is
 * no reliable feature-detection alternative for "is this iPhone Safari"
 * today (`beforeinstallprompt` itself is the only *reliable* signal, and
 * only Chromium ever fires it — see `installPrompt.ts`). Never thrown,
 * never asserted as ground truth to the user; only ever used to choose
 * which one of a small set of already-written, honest instructions to
 * show.
 */

export type Platform = 'ios' | 'android' | 'desktop'

function userAgent(): string {
  return typeof navigator !== 'undefined' ? navigator.userAgent : ''
}

/**
 * iPadOS 13+ reports itself as `Macintosh` in its own User-Agent string by
 * default (Apple's own "desktop-class Safari" change) -- `maxTouchPoints`
 * is the one signal that still tells an iPad apart from a real Mac.
 */
function isIPadOS(): boolean {
  return /Macintosh/.test(userAgent()) && typeof navigator !== 'undefined' && navigator.maxTouchPoints > 1
}

export function detectPlatform(): Platform {
  const ua = userAgent()
  if (/iPhone|iPod|iPad/.test(ua) || isIPadOS()) {
    return 'ios'
  }
  if (/Android/.test(ua)) {
    return 'android'
  }
  return 'desktop'
}

/**
 * True only for real Safari -- every other iOS browser (Chrome, Firefox,
 * Edge, Opera on iOS) is required by Apple to use WebKit underneath and so
 * still carries the string "Safari" in its own User-Agent, but each also
 * carries its own distinguishing token this excludes. Relevant only on iOS:
 * "На экран «Домой»" is a Safari-only affordance (ADR/Product text, Section
 * 4) -- Chrome-on-iOS has no equivalent menu item at all.
 */
export function isSafari(): boolean {
  const ua = userAgent()
  return /Safari/.test(ua) && !/CriOS|FxiOS|EdgiOS|OPiOS|Mercury/.test(ua)
}

/** True for Chrome on any platform, excluding other Chromium-based browsers that also carry "Chrome" in their own UA. */
export function isChrome(): boolean {
  const ua = userAgent()
  return /Chrome/.test(ua) && !/Edg|OPR|SamsungBrowser|Brave/.test(ua)
}

/**
 * The one standards-based signal here (not UA sniffing): true once PIOS is
 * actually running installed, on any platform -- `display-mode: standalone`
 * is set by the OS/browser shell itself for an installed PWA;
 * `navigator.standalone` is Safari's own older, iOS-only equivalent,
 * checked as a fallback since Safari did not add `display-mode` support
 * until later. Section 13's own rule ("не утверждать «установлено», если
 * браузер не позволяет достоверно это определить") is why this function
 * exists at all -- it is the only honest way to answer "is this already
 * installed?" without trusting a click or a guess.
 */
export function isStandalone(): boolean {
  if (typeof window === 'undefined') {
    return false
  }
  const displayModeStandalone = window.matchMedia?.('(display-mode: standalone)')?.matches ?? false
  const iosStandalone = (window.navigator as Navigator & { standalone?: boolean }).standalone === true
  return displayModeStandalone || iosStandalone
}
