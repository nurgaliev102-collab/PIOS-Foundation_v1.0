/**
 * PIOS Driver Web Push (ADR-083, D-10). Pure, side-effect-free browser
 * capability detection -- mirrors `features/install/deviceDetection.ts`'s
 * own "never thrown, never asserted as ground truth" discipline: used only
 * to decide whether `DriverHome.tsx`'s own opt-in control should render at
 * all, never surfaced to the user as an error.
 */

/** True only when every API `pushSubscription.ts` needs is actually present. Never throws. */
export function pushSupport(): boolean {
  if (typeof window === 'undefined' || typeof navigator === 'undefined') {
    return false
  }
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window
}
