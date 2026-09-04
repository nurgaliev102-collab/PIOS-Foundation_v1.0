import type { ElementType, ReactNode } from 'react'
import styles from './Text.module.css'

export interface TextProps {
  children: ReactNode
  /** docs/PIOS_DESIGN_SYSTEM.md Section 3 (Typography) roles. */
  role?: 'body' | 'label' | 'caption' | 'numeric'
  tone?: 'primary' | 'secondary' | 'muted' | 'error' | 'success' | 'warning'
  strong?: boolean
  as?: ElementType
  htmlFor?: string
}

const roleClass: Record<NonNullable<TextProps['role']>, string> = {
  body: styles.body,
  label: styles.label,
  caption: styles.caption,
  numeric: styles.numeric,
}

const toneClass: Record<NonNullable<TextProps['tone']>, string> = {
  primary: styles.tonePrimary,
  secondary: styles.toneSecondary,
  muted: styles.toneMuted,
  error: styles.toneError,
  success: styles.toneSuccess,
  warning: styles.toneWarning,
}

/**
 * PIOS design-system foundation Text — docs/PIOS_DESIGN_SYSTEM.md Section
 * 3/5. One component for body/label/caption/numeric, not four separate
 * ones, per that document's own instruction. `role="numeric"` applies
 * `font-variant-numeric: tabular-nums` so fares/ETAs/counts align — a
 * rule that document names explicitly and that did not exist anywhere in
 * the codebase before this component.
 */
export function Text({ children, role = 'body', tone = 'primary', strong = false, as, htmlFor }: TextProps) {
  const Tag = as ?? (role === 'label' ? 'label' : 'p')
  const className = `${roleClass[role]} ${toneClass[tone]}${strong ? ` ${styles.strong}` : ''}`
  return (
    <Tag className={className} htmlFor={Tag === 'label' ? htmlFor : undefined}>
      {children}
    </Tag>
  )
}
