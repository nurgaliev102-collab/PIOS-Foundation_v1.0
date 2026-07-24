import styles from './DriverCard.module.css'

export interface DriverCardProps {
  driverCode: string
  availability: 'AVAILABLE' | 'UNAVAILABLE'
  /**
   * Sprint FR-004 — Manual Assignment: the Coordinator page's own driver
   * selection needs each card to be clickable and to show which one is
   * currently selected. Both optional, defaulting to "not selectable" —
   * every existing caller (Driver Home) is unaffected.
   */
  onClick?: () => void
  selected?: boolean
}

/**
 * Displays a driver's own id and availability — the only two fields the
 * real Driver Management backend actually has (Sprint 5: First Backend
 * Integration). Earlier sprints also showed a display name, but no name
 * or profile exists anywhere in the backend's Driver aggregate; rather
 * than inventing one, this component now shows only real data. Purely
 * presentational — takes both values as props, so a future sprint adding
 * a real driver profile concept (once one is documented and approved)
 * only changes what calls this component.
 */
export function DriverCard({ driverCode, availability, onClick, selected = false }: DriverCardProps) {
  const initial = driverCode.trim().charAt(0).toUpperCase()
  const isAvailable = availability === 'AVAILABLE'
  const clickable = onClick !== undefined

  return (
    <section
      className={`${styles.card} ${clickable ? styles.clickable : ''} ${selected ? styles.selected : ''}`}
      onClick={onClick}
      role={clickable ? 'button' : undefined}
      tabIndex={clickable ? 0 : undefined}
    >
      <div className={styles.avatar} aria-hidden="true">
        {initial}
      </div>
      <div className={styles.details}>
        <span className={styles.code}>{driverCode}</span>
        <span className={`${styles.availability} ${isAvailable ? styles.available : styles.unavailable}`}>
          {isAvailable ? 'Available' : 'Unavailable'}
        </span>
      </div>
    </section>
  )
}
