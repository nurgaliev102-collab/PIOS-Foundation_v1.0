import type { ReactNode } from 'react'
import styles from './Heading.module.css'

export interface HeadingProps {
  children: ReactNode
  /** Document-semantic level — independent of visual size (docs/PIOS_DESIGN_SYSTEM.md Section 5). */
  level?: 1 | 2 | 3
  /** Visual weight — 'display' is reserved for the one identity element that should lead the screen. */
  visual?: 'display' | 'heading'
}

/** PIOS design-system foundation Heading — docs/PIOS_DESIGN_SYSTEM.md Section 5. */
export function Heading({ children, level = 1, visual = level === 1 ? 'display' : 'heading' }: HeadingProps) {
  const Tag = (`h${level}` as const)
  return <Tag className={visual === 'display' ? styles.display : styles.heading}>{children}</Tag>
}
