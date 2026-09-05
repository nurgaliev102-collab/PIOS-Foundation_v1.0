import { Button } from '../Button'
import styles from './ErrorState.module.css'

export interface ErrorStateProps {
  /** Required — no generic fallback string (docs/PIOS_DESIGN_SYSTEM.md Section 5/10, "specific to the actual condition"). */
  message: string
  retryLabel?: string
  onRetry?: () => void
}

/** PIOS design-system foundation ErrorState — docs/PIOS_DESIGN_SYSTEM.md Section 5. */
export function ErrorState({ message, retryLabel = 'Попробовать снова', onRetry }: ErrorStateProps) {
  return (
    <div className={styles.wrap}>
      <p className={styles.message} role="alert">
        {message}
      </p>
      {onRetry && (
        <div className={styles.action}>
          <Button label={retryLabel} variant="secondary" onClick={onRetry} />
        </div>
      )}
    </div>
  )
}
