import type { ButtonHTMLAttributes, ReactNode } from 'react'
import styles from './Button.module.css'

export interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'className'> {
  label: string
  icon?: ReactNode
  variant?: 'primary' | 'secondary' | 'destructive'
  loading?: boolean
}

/**
 * PIOS design-system foundation Button — docs/PIOS_DESIGN_SYSTEM.md
 * Section 5 ("Button — the existing `ActionButton` generalizes into
 * this"). A new, separate component rather than an edit to
 * `components/ActionButton`, deliberately: `ActionButton` is still used,
 * unchanged, by every other screen (DriverHome, PassengerLanding,
 * Coordinator) — editing it in place would redesign those screens too,
 * which this task's own scope (one screen only) forbids. This component
 * exists so `RideRequest` — this task's one redesign target — can adopt
 * the token-driven foundation component set without touching anything
 * else. A future task migrates the remaining screens onto this same
 * component and retires `ActionButton`, per
 * docs/PIOS_DESIGN_SYSTEM.md Section 15's own incremental-migration plan.
 *
 * `variant="destructive"` is new relative to `ActionButton` (which only
 * had primary/secondary) — added because this design system's own
 * Section 6 ("Component states") names a distinct treatment for a
 * consequential/negative action (e.g. cancelling an order), and
 * `--pios-color-error` already exists as a token for exactly this.
 */
export function Button({ label, icon, variant = 'secondary', loading = false, disabled, type = 'button', ...rest }: ButtonProps) {
  const variantClass = variant === 'primary' ? styles.primary : variant === 'destructive' ? styles.destructive : styles.secondary
  return (
    <button
      type={type}
      className={`${styles.button} ${variantClass}`}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? (
        <span className={styles.spinner} aria-hidden="true" />
      ) : (
        icon && <span className={styles.icon}>{icon}</span>
      )}
      <span>{label}</span>
    </button>
  )
}
