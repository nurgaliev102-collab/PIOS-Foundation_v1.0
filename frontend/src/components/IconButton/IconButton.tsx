import type { ButtonHTMLAttributes, ReactNode } from 'react'
import styles from './IconButton.module.css'

export interface IconButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'className'> {
  icon: ReactNode
  /** Required, not optional — an icon-only control with no accessible name fails
   * docs/PIOS_DESIGN_SYSTEM.md Section 13 ("every icon-only IconButton has an
   * accessible name"). */
  label: string
  variant?: 'primary' | 'secondary'
}

/** PIOS design-system foundation IconButton — docs/PIOS_DESIGN_SYSTEM.md Section 5. */
export function IconButton({ icon, label, variant = 'secondary', type = 'button', ...rest }: IconButtonProps) {
  return (
    <button
      type={type}
      className={`${styles.button} ${variant === 'primary' ? styles.primary : styles.secondary}`}
      aria-label={label}
      title={label}
      {...rest}
    >
      <span aria-hidden="true">{icon}</span>
    </button>
  )
}
