import { useCallback, useEffect, useRef, useState } from 'react'
import { Header } from '../../components/Header'
import { ActionButton } from '../../components/ActionButton'
import { LoginScreen } from './LoginScreen'
import { StatusCard } from './StatusCard'
import { TodayCard } from './TodayCard'
import { EventFeed } from './EventFeed'
import { AIAnalystCard } from './AIAnalystCard'
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
 *
 * 2026-08-17 incident (second phase): `tick()` used to be driven by
 * `window.setInterval(tick, POLL_INTERVAL_MS)`, which fires unconditionally
 * every 15 seconds regardless of whether the previous [runPoll] call is
 * still in flight. `runPoll`'s own duration is dominated by
 * `loadTodaySnapshot`'s per-driver fan-out (`todayData.ts`'s own
 * `FAN_OUT_CONCURRENCY_LIMIT`); on this shared pilot host,
 * `OwnerCredentialGate.verify` (replicated in every module) recomputes
 * PBKDF2-HMAC-SHA256 at 210,000 iterations on *every* authenticated
 * request, uncached — measured directly at ~2.2s for one such request and
 * ~8-9s for five concurrent ones under real contention on this machine. At
 * the pilot's accumulated test-driver count, a single `runPoll` can
 * therefore legitimately take minutes, far longer than 15 seconds, and
 * `setInterval` would start a brand new, never-cancelled `runPoll` on top
 * of one still running, every 15 seconds, without bound.
 *
 * This is also why the concurrency limit added earlier the same day
 * (`todayData.ts`'s own KDoc) was verified with a Playwright reproduction
 * that used a placeholder (wrong) owner credential and reported the fix
 * clean — `OwnerCredentialGate`'s own brute-force cap
 * (`maxFailuresPerWindow`/`windowMillis`) short-circuits *before* computing
 * PBKDF2 once ~20 failures have accumulated in the current window, so a
 * sustained wrong-credential test goes cheap (confirmed: ~150ms per
 * request once rate-limited, vs. the ~2.2s/~8-9s above before that point) —
 * a discrepancy that does not exist for a real, valid credential, which is
 * never counted against that cap and always pays the full cost. `tick` is
 * now self-scheduling: it only queues the next call, via
 * `window.setTimeout`, after the current `runPoll` has resolved, so at most
 * one poll's worth of authenticated requests is ever in flight — verified
 * live via Playwright both with an artificially slowed backend (a second
 * poll's health checks never start while the first's fan-out is still
 * running) and against the public Funnel (health-check cadence becomes
 * irregular, each cycle waiting out its own `runPoll` plus 15s, rather than
 * firing on an exact 15.0s beat).
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
    let timeoutId: number | undefined

    async function tick() {
      if (cancelled) {
        return
      }
      await runPoll(activeCredential, generation)
      if (cancelled) {
        return
      }
      timeoutId = window.setTimeout(tick, POLL_INTERVAL_MS)
    }

    void tick()
    const clockInterval = window.setInterval(() => setNow(new Date()), 30_000)

    return () => {
      cancelled = true
      window.clearTimeout(timeoutId)
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
          <AIAnalystCard credential={credential} healths={healths} />
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
