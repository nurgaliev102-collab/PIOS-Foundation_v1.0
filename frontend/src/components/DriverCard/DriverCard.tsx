import styles from './DriverCard.module.css'

export interface DriverCardProps {
  driverCode: string
  availability: 'AVAILABLE' | 'UNAVAILABLE'
  /**
   * UX audit (pilot readiness): Sprint 7B added a real `displayName` to
   * the Driver Management backend, but this component never picked it up
   * — a driver's own home screen kept showing their raw system id
   * (`ILDAR001`) as the headline text, exactly the kind of "engineering
   * prototype" impression the pilot audit was looking for. Optional and
   * defaults to not shown, so `Coordinator.tsx` (which has no name to
   * pass — it lists drivers for operator selection by id, on purpose)
   * keeps its existing behavior unchanged.
   */
  displayName?: string | null
  /**
   * Pilot-readiness audit (ADR-039 follow-up): this component used to show
   * the id as secondary text under a real name "for anyone who still needs
   * it", written when ids were short hand-assigned codes (`ILDAR001`).
   * ADR-039 generates a full `crypto.randomUUID()` instead, and its own
   * explicit rule is that this id is never shown to the person it belongs
   * to — as secondary text it read as a raw UUID printed on a driver's own
   * home screen. Coordinator's own card (no name passed, drivers picked by
   * id on purpose) is unaffected; only Driver Home sets this.
   */
  hideCode?: boolean
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
 * Displays a driver's identity and availability. When a real
 * [displayName] is available, it becomes the headline (with the id shown
 * smaller, underneath, for anyone who still needs it, unless [hideCode]
 * says otherwise) — otherwise this falls back to the id itself exactly as
 * before, so nothing regresses for a driver Driver Management has no name
 * on file for yet.
 */
export function DriverCard({
  driverCode,
  displayName,
  availability,
  hideCode = false,
  onClick,
  selected = false,
}: DriverCardProps) {
  const headline = displayName?.trim() || driverCode
  const initial = headline.charAt(0).toUpperCase()
  const isAvailable = availability === 'AVAILABLE'
  const clickable = onClick !== undefined

  return (
    <section
      className={`${styles.card} ${clickable ? styles.clickable : ''} ${selected ? styles.selected : ''}`}
      onClick={onClick}
      onKeyDown={
        clickable
          ? (event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault()
                onClick?.()
              }
            }
          : undefined
      }
      role={clickable ? 'button' : undefined}
      tabIndex={clickable ? 0 : undefined}
    >
      <div className={styles.avatar} aria-hidden="true">
        {initial}
      </div>
      <div className={styles.details}>
        <span className={styles.code}>{headline}</span>
        {displayName?.trim() && !hideCode && <span className={styles.subcode}>{driverCode}</span>}
        <span className={`${styles.availability} ${isAvailable ? styles.available : styles.unavailable}`}>
          {isAvailable ? 'На линии' : 'Не на линии'}
        </span>
      </div>
    </section>
  )
}
