import styles from './Spinner.module.css'

export interface SpinnerProps {
  label?: string
}

/**
 * Pilot UX audit: every screen's "loading" state used to be plain text
 * only ("Загрузка…") — functional, but one of the clearest "unfinished
 * prototype" signals an interface can give a first-time user, since it
 * gives no visual confirmation that anything is actually happening. This
 * is a small, purely decorative, CSS-only spinner (no library, no
 * network) shared by every screen that already shows a loading status,
 * so "waiting" looks and feels the same everywhere in the app.
 */
export function Spinner({ label }: SpinnerProps) {
  return (
    <div className={styles.wrap} role="status" aria-live="polite">
      <div className={styles.spinner} aria-hidden="true" />
      {label && <p className={styles.label}>{label}</p>}
    </div>
  )
}
