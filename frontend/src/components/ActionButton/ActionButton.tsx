import type { ReactNode } from 'react'
import styles from './ActionButton.module.css'

export interface ActionButtonProps {
  label: string
  icon?: ReactNode
  /**
   * Sprint 1 — Driver Home: no handler is wired up for Copy or Share by
   * the page that uses this component. Left optional, and a no-op when
   * omitted, so this component itself is not what's "unimplemented" —
   * clicking is silently inert until a future sprint supplies real
   * behavior, consistent with this sprint's own "no implementation"
   * scope for these two actions specifically.
   */
  onClick?: () => void
  variant?: 'primary' | 'secondary'
  /**
   * Sprint FR-004 — Manual Assignment: the coordinator's own Assign
   * button must stay inert until both an order and a driver are
   * selected. Optional and defaults to `false`, so every existing caller
   * (Copy, Share, Request a Ride) is unaffected.
   */
  disabled?: boolean
}

/**
 * Generic, reusable action button — no knowledge of what action it
 * triggers. Used today for Copy/Share on Driver Home; any future screen
 * needing a labeled, optionally-iconed button reuses this rather than a
 * one-off styled `<button>`.
 */
export function ActionButton({ label, icon, onClick, variant = 'secondary', disabled = false }: ActionButtonProps) {
  return (
    <button
      type="button"
      className={`${styles.button} ${variant === 'primary' ? styles.primary : styles.secondary}`}
      onClick={onClick}
      disabled={disabled}
    >
      {icon && <span className={styles.icon}>{icon}</span>}
      <span>{label}</span>
    </button>
  )
}
