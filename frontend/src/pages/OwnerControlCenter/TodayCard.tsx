import type { TodayCounters } from './todayData'
import styles from './OwnerControlCenter.module.css'

export interface TodayCardProps {
  counters: TodayCounters
}

/**
 * "Сегодня" (`PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md` Section 5.2).
 * "Заказов создано" is the exact wording, deliberately, not "поездок
 * завершено сегодня": `Order` carries no completion timestamp
 * (ADR-043 Decision 7's own disclosed measurement limitation), so these
 * are orders *created* today, classified by their current status — the
 * label says exactly what is counted, nothing more.
 */
export function TodayCard({ counters }: TodayCardProps) {
  return (
    <section className={styles.todayCard}>
      <h2 className={styles.sectionTitle}>СЕГОДНЯ</h2>
      <dl className={styles.todayGrid}>
        <dt>Водителей всего</dt>
        <dd>{counters.driversTotal}</dd>
        <dt>На линии сейчас</dt>
        <dd>{counters.driversAvailable}</dd>
        <dt>Заказов создано</dt>
        <dd>{counters.ordersCreated}</dd>
        <dt className={styles.todaySubItem}>выполнено</dt>
        <dd>{counters.ordersCompleted}</dd>
        <dt className={styles.todaySubItem}>в работе</dt>
        <dd>{counters.ordersInProgress}</dd>
        <dt className={styles.todaySubItem}>отменено</dt>
        <dd>{counters.ordersCancelled}</dd>
      </dl>
    </section>
  )
}
