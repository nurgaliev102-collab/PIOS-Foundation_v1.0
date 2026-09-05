import styles from './DriverTrustIndicator.module.css'

export type DriverAvailability = 'AVAILABLE' | 'UNAVAILABLE'

export interface DriverTrustIndicatorProps {
  /**
   * Required — the one fact this component exists to make prominent
   * (docs/DRIVER_IDENTITY_DESIGN_DECISION.md Section 1). No photo/avatar
   * prop exists: none is available in the current data model (that
   * document's own Phase 1 audit) — inventing one here would be exactly
   * the kind of invented driver attribute this task's own instructions
   * forbid.
   */
  name: string
  /**
   * Only rendered when the caller actually has this fact — never
   * guessed or defaulted to a value. `RideRequest`'s confirmed-ride step
   * never fetches availability, so it simply omits this prop rather
   * than asserting something unverified.
   */
  availability?: DriverAvailability
  /**
   * Circle of Trust's own ratified "primary" designation
   * (docs/PRODUCT_DECISION_CIRCLE_OF_TRUST.md Rule 1) — a real,
   * passenger-chosen fact, rendered as a single, non-numeric marker.
   * Never a rank, never a count. Omitted (not `false`) wherever the
   * caller has no Circle-of-Trust data loaded at all.
   */
  isPrimary?: boolean
  /**
   * 'prominent' for the one identity element that should lead a screen
   * (docs/PIOS_TAXI_DESIGN_BRIEF.md Section 2, Principle 1 — "driver's
   * name is the most prominent identity element"); 'compact' for a list
   * row alongside others, e.g. Circle of Trust's own "other drivers"
   * list. Defaults to 'compact'.
   */
  emphasis?: 'compact' | 'prominent'
  /**
   * Renders an initial-letter avatar circle beside the name — the
   * existing treatment `PassengerLanding.tsx`'s own `.heroAvatar` already
   * used (docs/PASSENGER_INVITATION_DESIGN_DECISION.md's own "Component
   * change required"), matching docs/PIOS_TAXI_DESIGN_BRIEF.md's own
   * recommendation ("a clear initial/name treatment beats a generic
   * silhouette avatar") for when no real photo exists. Deliberately
   * **opt-in, default `false`**: `RideRequest`'s existing
   * `emphasis="prominent"` usages (Task 4/5, already visually verified)
   * stay pixel-identical unless a caller explicitly asks for this.
   */
  showAvatar?: boolean
}

/**
 * PIOS design system — DriverTrustIndicator
 * (docs/DRIVER_IDENTITY_DESIGN_DECISION.md). Communicates DRIVER-AS-FACE,
 * PIOS-AS-FRAME: the driver's name, and only the real, already-ratified
 * relationship facts the product has (availability, Circle-of-Trust
 * primary) — never a rating, count, rank, or any fabricated trust score.
 * Consolidates the design system's own originally-separate
 * `DriverIdentity`/`DriverTrustIndicator` roles into one component, per
 * that design decision document's own "Component scope note".
 */
export function DriverTrustIndicator({
  name,
  availability,
  isPrimary,
  emphasis = 'compact',
  showAvatar = false,
}: DriverTrustIndicatorProps) {
  const isProminent = emphasis === 'prominent'
  const initial = name.trim().charAt(0).toUpperCase()

  const identity = (
    <div className={styles.identity}>
      <span className={isProminent ? styles.nameProminent : styles.nameCompact}>{name}</span>
      {(availability || isPrimary) && (
        <div className={styles.metaRow}>
          {isPrimary && <span className={styles.primaryBadge}>Основной</span>}
          {availability && (
            <span className={styles.availability}>
              <span
                className={availability === 'AVAILABLE' ? styles.dotAvailable : styles.dotUnavailable}
                aria-hidden="true"
              />
              {availability === 'AVAILABLE' ? 'Доступен' : 'Недоступен'}
            </span>
          )}
        </div>
      )}
    </div>
  )

  if (!showAvatar) {
    return <div className={styles.wrap}>{identity}</div>
  }

  return (
    <div className={`${styles.wrap} ${styles.withAvatar}`}>
      <span className={styles.avatar} aria-hidden="true">
        {initial}
      </span>
      {identity}
    </div>
  )
}
