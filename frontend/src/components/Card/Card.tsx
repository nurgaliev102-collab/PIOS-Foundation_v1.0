import type { ReactNode } from 'react'
import styles from './Card.module.css'

export interface CardProps {
  children: ReactNode
  /** Raised is reserved for something genuinely "above" the rest of the screen — docs/PIOS_DESIGN_SYSTEM.md Section 3 (Elevation). */
  elevated?: boolean
  align?: 'start' | 'center'
  /**
   * Redesign 2.0: an optional background tier, for the one case a card
   * needs to read as *grouped inside* its own surroundings rather than as
   * one more identical raised rectangle on the page (the exact "много
   * одинаковых прямоугольных коробок" gap the redesign brief names) —
   * `'muted'` uses `--pios-color-surface-secondary` with no border of its
   * own. Optional, defaults to the original `'surface'` look every
   * existing caller already renders — nothing changes for a caller that
   * doesn't pass this.
   */
  tone?: 'surface' | 'muted'
}

/** PIOS design-system foundation Card — docs/PIOS_DESIGN_SYSTEM.md Section 5, generalizing `DriverCard`'s existing container styling. */
export function Card({ children, elevated = false, align = 'start', tone = 'surface' }: CardProps) {
  return (
    <div
      className={`${styles.card} ${elevated ? styles.elevated : ''} ${align === 'center' ? styles.center : ''} ${tone === 'muted' ? styles.muted : ''}`}
    >
      {children}
    </div>
  )
}
