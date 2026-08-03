import type { OwnerStatusResult } from './statusEvaluation'
import styles from './OwnerControlCenter.module.css'

export interface StatusCardProps {
  result: OwnerStatusResult
  /** When the last successful poll completed, or `null` before the first one has. */
  lastCheckedAt: Date | null
  onRetry: () => void
}

const CIRCLE_CLASS: Record<OwnerStatusResult['status'], string> = {
  checking: styles.circleGrey,
  ok: styles.circleGreen,
  delayed: styles.circleYellow,
  problem: styles.circleRed,
  configuration: styles.circleGrey,
  unreachable: styles.circleGrey,
}

/**
 * The main status card — the one answer this whole screen exists to give
 * in five seconds (`PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md` Section 1).
 * Four colors plus the pre-first-poll "checking" state (Section 5.2–5.5).
 *
 * The `configuration` state (Section 4.4) deliberately renders **no**
 * business-outage language at all — its whole reason to exist is to keep
 * a settings mismatch from being misread as "PIOS требует внимания."
 */
export function StatusCard({ result, lastCheckedAt, onRetry }: StatusCardProps) {
  return (
    <section className={styles.statusCard}>
      <div className={`${styles.circle} ${CIRCLE_CLASS[result.status]}`} aria-hidden="true" />

      {result.status === 'checking' && <p className={styles.statusHeadline}>Проверяю…</p>}

      {result.status === 'unreachable' && (
        <>
          <p className={styles.statusHeadline}>Не удаётся связаться с PIOS.</p>
          <p className={styles.statusBody}>Проверьте интернет на этом устройстве.</p>
          <button type="button" className={styles.retryButton} onClick={onRetry}>
            Попробовать снова
          </button>
        </>
      )}

      {result.status === 'configuration' && (
        <>
          <p className={styles.statusHeadline}>Не удаётся получить часть данных.</p>
          <p className={styles.statusBody}>Нужна проверка настроек.</p>
          <p className={styles.statusBody}>Это не сбой в работе с заказами.</p>
        </>
      )}

      {result.status === 'ok' && (
        <>
          <p className={styles.statusHeadline}>PIOS работает</p>
          <p className={styles.statusBody}>
            {lastCheckedAt ? 'Проверено только что' : 'Проверяю…'}
          </p>
        </>
      )}

      {result.status === 'delayed' && (
        <>
          <p className={styles.statusHeadline}>PIOS работает с оговоркой</p>
          {result.delayLine && <p className={styles.statusBody}>{result.delayLine}</p>}
        </>
      )}

      {result.status === 'problem' && (
        <>
          <p className={styles.statusHeadline}>PIOS требует внимания</p>
          {result.problemLines.map((line) => (
            <p className={styles.statusBody} key={line}>
              {line}
            </p>
          ))}
        </>
      )}
    </section>
  )
}
