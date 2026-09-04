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
      {children}
    </div>
  )
}
