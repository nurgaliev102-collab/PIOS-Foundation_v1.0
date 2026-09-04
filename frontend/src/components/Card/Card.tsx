import type { ReactNode } from 'react'
import styles from './Card.module.css'

export interface CardProps {
  children: ReactNode
  /** Raised is reserved for something genuinely "above" the rest of the screen — docs/PIOS_DESIGN_SYSTEM.md Section 3 (Elevation). */
  elevated?: boolean
  align?: 'start' | 'center'
}

/** PIOS design-system foundation Card — docs/PIOS_DESIGN_SYSTEM.md Section 5, generalizing `DriverCard`'s existing container styling. */
export function Card({ children, elevated = false, align = 'start' }: CardProps) {
  return (
    <div className={`${styles.card} ${elevated ? styles.elevated : ''} ${align === 'center' ? styles.center : ''}`}>
      {children}
    </div>
  )
}
