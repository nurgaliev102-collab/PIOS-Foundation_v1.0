/**
 * PIOS Install v1 (Product Owner exception — see `InstallPIOS.tsx`'s own
 * doc comment).
 *
 * Wraps the browser's real, native install mechanism (Chromium's
 * `beforeinstallprompt`/`appinstalled` events) — never simulated, per
 * Section 13's own explicit rule against a fake "Установить" button. iOS
 * Safari never fires `beforeinstallprompt` at all (Apple's own platform
 * choice, not a bug here) — `isNativeInstallAvailable()` simply stays
 * `false` there forever, which is exactly what routes `InstallPIOS.tsx` to
 * the manual iPhone instructions instead.
 *
 * The listener below is registered once, at module load — not inside a
 * component's `useEffect` — because `beforeinstallprompt` can fire at any
 * point during the page's lifetime, including before `InstallPIOS.tsx`
 * (or any component that imports this module) ever mounts. Chrome fires it
 * only once per navigation and expects the page to call `preventDefault()`
 * synchronously if it wants to defer and reuse the prompt later
 * (MDN/web.dev's own documented contract) — missing that one call means
 * losing the prompt for this page load entirely.
 */

export type InstallOutcome = 'accepted' | 'dismissed' | 'unavailable'

/**
 * The shape Chromium's own `beforeinstallprompt` event carries. Not
 * exported/reused elsewhere — this file is the only place that needs to
 * know it exists.
 */
interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

let deferredPrompt: BeforeInstallPromptEvent | null = null

if (typeof window !== 'undefined') {
  window.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault()
    deferredPrompt = event as BeforeInstallPromptEvent
  })
  // Chromium's own confirmation that the install actually completed --
  // `userChoice` resolving 'accepted' only means the OS-level prompt was
  // accepted, not that installation finished; this event is the real
  // signal `InstallPIOS.tsx` waits for before showing "PIOS установлен".
  window.addEventListener('appinstalled', () => {
    deferredPrompt = null
  })
}

/** Whether the browser has actually offered a real, native install prompt for this page load. */
export function isNativeInstallAvailable(): boolean {
  return deferredPrompt !== null
}

/**
 * Shows the real, browser-owned install prompt and waits for the person's
 * own choice. Returns 'unavailable' rather than throwing if no prompt was
 * ever captured (e.g. called twice, or on a browser/platform that never
 * fires it) — `InstallPIOS.tsx` treats that identically to "never had a
 * native prompt to begin with" and falls back to manual instructions.
 */
export async function triggerNativeInstall(): Promise<InstallOutcome> {
  if (!deferredPrompt) {
    return 'unavailable'
  }
  const promptEvent = deferredPrompt
  // A captured prompt can only ever be shown once (Chromium's own
  // contract) -- clearing this immediately, before awaiting, means a
  // second accidental click while the first prompt is still open cannot
  // attempt to reuse an already-consumed event.
  deferredPrompt = null
  await promptEvent.prompt()
  const choice = await promptEvent.userChoice
  return choice.outcome
}

/**
 * Fires [callback] the moment the browser confirms installation actually
 * completed (`appinstalled`) -- the one event Section 13 allows treating
 * as ground truth for "PIOS установлен". Returns an unsubscribe function,
 * the same convention `identity/InvitationProvider.ts`'s own callers
 * already follow for effect cleanup.
 */
export function onInstalled(callback: () => void): () => void {
  if (typeof window === 'undefined') {
    return () => {}
  }
  window.addEventListener('appinstalled', callback)
  return () => window.removeEventListener('appinstalled', callback)
}

/**
 * "Поделиться инструкцией" (Section 11) -- same Web Share API, same
 * clipboard fallback, same shape as `identity/InvitationProvider.ts`'s own
 * `LocalInvitationProvider.share`, deliberately not re-abstracted into a
 * shared helper: that class is Driver Home's own invitation-link concept
 * (ADR-038), unrelated to installation, and duplicating four lines here
 * keeps this feature's own isolation (Section 20: no import of anything
 * install has no real reason to depend on).
 */
export async function shareInstallLink(url: string): Promise<'shared' | 'copied' | 'failed'> {
  if (navigator.share) {
    try {
      await navigator.share({ title: 'PIOS', text: 'Как установить PIOS на телефон', url })
      return 'shared'
    } catch {
      // A user-cancelled share (AbortError) is not a failure worth surfacing.
      return 'failed'
    }
  }
  try {
    await navigator.clipboard.writeText(url)
    return 'copied'
  } catch {
    return 'failed'
  }
}
