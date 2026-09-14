import type { ReactNode } from 'react'
import styles from './StatusMessage.module.css'

export type StatusTone = 'information' | 'success' | 'warning' | 'error'

export interface StatusMessageProps {
  children: ReactNode
  tone?: StatusTone
  /**
   * A ride/request status change is a real accessibility event, not just
   * a visual one (docs/PIOS_DESIGN_SYSTEM.md Section 13) — defaults to
   * `polite` so a live status update (e.g. "driver accepted") is
   * announced without interrupting the user.
   */
  live?: 'polite' | 'off'
}

const toneClass: Record<StatusTone, string> = {
  information: styles.information,
  success: styles.success,
  warning: styles.warning,
  error: styles.error,
}

/**
 * Redesign 2.0 ("никаких emoji вместо нормальной iconography"): one small,
 * fixed glyph per tone — the same four this component already commits to
 * (`StatusTone`), never a per-message custom icon. `aria-hidden` — the
 * tone is already carried by `role`/color/the message text itself; this is
 * a purely visual reinforcement, never the only signal (docs/PIOS_DESIGN_SYSTEM.md
 * Section 13, "color is never the sole carrier of meaning" — extended here
 * to "icon is never the only carrier either"). Contributes no text node,
 * so it cannot change what any existing `getByText`/`textContent` check
 * against this component's own `children` matches.
 */
const toneIcon: Record<StatusTone, ReactNode> = {
  information: (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="10" cy="10" r="7.25" />
      <path d="M10 9v4.5" />
      <circle cx="10" cy="6.5" r="0.15" fill="currentColor" stroke="none" />
    </svg>
  ),
  success: (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="10" cy="10" r="7.25" />
      <path d="M6.75 10.25 8.75 12.25 13.25 7.75" />
    </svg>
  ),
  warning: (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M10 3.5 17 15.5H3L10 3.5Z" strokeLinejoin="round" />
      <path d="M10 8.5v3.25" />
      <circle cx="10" cy="13.5" r="0.15" fill="currentColor" stroke="none" />
    </svg>
  ),
  error: (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="10" cy="10" r="7.25" />
      <path d="M10 6.75v4" />
      <circle cx="10" cy="13.25" r="0.15" fill="currentColor" stroke="none" />
    </svg>
  ),
}

/**
 * PIOS design-system foundation status indicator — docs/PIOS_DESIGN_SYSTEM.md
 * Section 5 ("Alert — inline, non-blocking; maps to
 * information/warning/error/success tokens") and Section 10 (this is the
 * shared primitive `RideStatus`-shaped text uses). Color is never the
 * sole carrier of meaning (Section 13) — each tone pairs its color with
 * a left accent bar and a distinct surface tint, not color-on-white text
 * alone.
 */
export function StatusMessage({ children, tone = 'information', live = 'polite' }: StatusMessageProps) {
  return (
    <div className={`${styles.message} ${toneClass[tone]}`} role={tone === 'error' ? 'alert' : 'status'} aria-live={live}>
      <span className={styles.icon} aria-hidden="true">
        {toneIcon[tone]}
      </span>
      <span className={styles.text}>{children}</span>
    </div>
  )
}
