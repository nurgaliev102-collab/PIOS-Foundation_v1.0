import { useCallback, useEffect, useRef, useState } from 'react'
import { Header } from '../../components/Header'
import { ActionButton } from '../../components/ActionButton'
import { LoginScreen } from './LoginScreen'
import { StatusCard } from './StatusCard'
import { TodayCard } from './TodayCard'
import { EventFeed } from './EventFeed'
import { pollAllModuleHealth, type ModuleHealth } from './healthPoll'
import { loadTodaySnapshot, type TodaySnapshot } from './todayData'
import { evaluateOwnerStatus } from './statusEvaluation'
import { buildOwnerReport } from './buildReport'
import { clearOwnerCredential, getStoredOwnerCredential, type OwnerCredential } from './ownerCredential'
import styles from './OwnerControlCenter.module.css'

/**
 * The Owner Control Center itself (ADR-043, ADR-044;
 * `PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md`). The `/owner` route
 * (`app/routes.tsx`) renders this component unconditionally; this
 * component itself decides login screen vs. console, which is the entire
 * "gate" ADR-044 Decision 5 places on this one route — no router-level
 * guard, no redirect, since there is nowhere else to redirect to (Section
 * 4.2: every other route stays exactly as unauthenticated as before).
 *
 * Polling (Section 7.5): every 15 seconds, health first (drives the main
 * card's color), then the "Сегодня" fan-out — cancelled on unmount via
 * [cancelled], mirroring `DriverHome.tsx`'s own `active` flag convention
 * for a polling effect that must not update state after the page is left.
 */
const POLL_INTERVAL_MS = 15_000

const EMPTY_TODAY: TodaySnapshot = {
  counters: {
    driversTotal: 0,
    driversAvailable: 0,
    ordersCreated: 0,
    ordersCompleted: 0,
    ordersInProgress: 0,
    ordersCancelled: 0,
  },
  events: [],
}

export function OwnerControlCenter() {
  const [credential, setCredential] = useState<OwnerCredential | null>(() => getStoredOwnerCredential())
  const [healths, setHealths] = useState<ModuleHealth[]>([])
  const [today, setToday] = useState<TodaySnapshot>(EMPTY_TODAY)
  const [lastCheckedAt, setLastCheckedAt] = useState<Date | null>(null)
  const [now, setNow] = useState(() => new Date())
  const [reportCopied, setReportCopied] = useState(false)
  const pollGeneration = useRef(0)

  const runPoll = useCallback(async (activeCredential: OwnerCredential, generation: number) => {
    const health = await pollAllModuleHealth(activeCredential)
    if (pollGeneration.current !== generation) {
      return
    }
    setHealths(health)
    setLastCheckedAt(new Date())

    const snapshot = await loadTodaySnapshot(activeCredential)
    if (pollGeneration.current !== generation) {
      return
    }
    setToday(snapshot)
  }, [])

  useEffect(() => {
    if (!credential) {
      return
    }
    const activeCredential = credential
    const generation = ++pollGeneration.current
    let cancelled = false

    function tick() {
      if (cancelled) {
        return
      }
      void runPoll(activeCredential, generation)
    }

    tick()
    const interval = window.setInterval(tick, POLL_INTERVAL_MS)
    const clockInterval = window.setInterval(() => setNow(new Date()), 30_000)

    return () => {
      cancelled = true
      window.clearInterval(interval)
      window.clearInterval(clockInterval)
    }
  }, [credential, runPoll])

  function handleLogout() {
    clearOwnerCredential()
    setCredential(null)
    setHealths([])
    setToday(EMPTY_TODAY)
    setLastCheckedAt(null)
  }

  function handleRetry() {
    if (credential) {
      void runPoll(credential, pollGeneration.current)
    }
  }

  async function handlePrepareReport() {
    const statusResult = evaluateOwnerStatus(healths)
    const report = buildOwnerReport(statusResult, healths, today, new Date())
    try {
      await navigator.clipboard.writeText(report)
      setReportCopied(true)
      window.setTimeout(() => setReportCopied(false), 2000)
    } catch {
      setReportCopied(false)
    }
  }

  if (!credential) {
    return <LoginScreen onLoggedIn={() => setCredential(getStoredOwnerCredential())} />
  }

  const statusResult = evaluateOwnerStatus(healths)

  return (
    <div className={styles.page}>
      <div className={styles.headerRow}>
        <Header />
        <span className={styles.clock}>{now.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}</span>
      </div>

      <StatusCard result={statusResult} lastCheckedAt={lastCheckedAt} onRetry={handleRetry} />

      {statusResult.status !== 'unreachable' && (
        <>
          <TodayCard counters={today.counters} />
          <EventFeed events={today.events} />
        </>
      )}

      <div className={styles.actions}>
        <ActionButton
          label={reportCopied ? 'Скопировано' : 'Подготовить отчёт'}
          variant="primary"
          onClick={() => void handlePrepareReport()}
        />
        <ActionButton label="Выйти" variant="secondary" onClick={handleLogout} />
      </div>
    </div>
  )
}
