import { useState } from 'react'
import type { TodayEvent } from './todayData'
import styles from './OwnerControlCenter.module.css'

export interface EventFeedProps {
  events: TodayEvent[]
}

const COLLAPSED_COUNT = 7

/**
 * "Что происходило" (`PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md` Section
 * 5.2) — plain-language events, newest first, with "Показать ещё" to
 * expand beyond the first seven. A record with no timestamp is never
 * shown here, never guessed (ADR-043 Decision 4's own disclosed
 * limitation: every fact recorded before the relevant migration has none,
 * permanently) — [events] is built by `todayData.ts` from only the
 * records that do carry one.
 */
export function EventFeed({ events }: EventFeedProps) {
  const [expanded, setExpanded] = useState(false)
  const visible = expanded ? events : events.slice(0, COLLAPSED_COUNT)

  return (
    <section className={styles.eventFeed}>
      <h2 className={styles.sectionTitle}>ЧТО ПРОИСХОДИЛО</h2>
      {events.length === 0 && <p className={styles.statusBody}>Событий пока нет.</p>}
      <ul className={styles.eventList}>
        {visible.map((event) => (
          <li key={`${event.at}-${event.text}`} className={styles.eventItem}>
            <span className={styles.eventTime}>
              {new Date(event.at).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}
            </span>
            <span>{event.text}</span>
          </li>
        ))}
      </ul>
      {!expanded && events.length > COLLAPSED_COUNT && (
        <button type="button" className={styles.showMoreButton} onClick={() => setExpanded(true)}>
          Показать ещё
        </button>
      )}
    </section>
  )
}
