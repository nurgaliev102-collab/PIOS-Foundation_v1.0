import { Spinner } from '../Spinner'
import styles from './LoadingState.module.css'

export interface LoadingStateProps {
  /** Required — a specific, contextual label beats a bare spinner (docs/PIOS_DESIGN_SYSTEM.md Section 5, "EmptyState"/"ErrorState": required message contract). */
  label: string
}

/**
 * PIOS design-system foundation LoadingState — docs/PIOS_DESIGN_SYSTEM.md
 * Section 5: "generalizes the existing `Spinner`... explicitly documented
 * as *not* a skeleton-screen system." Reuses `components/Spinner`
 * unchanged (it already respects `prefers-reduced-motion`) rather than
 * duplicating it — this component only adds the consistent, token-driven
 * container/contract the design system asks for.
 */
export function LoadingState({ label }: LoadingStateProps) {
  return (
    <div className={styles.wrap}>
      <Spinner label={label} />
    </div>
  )
}
