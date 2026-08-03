import type { ModuleHealth } from './healthPoll'
import { MODULE_DOWN_TEXT, MODULE_UNREACHABLE_TEXT, QUEUE_DELAY_TEXT } from './textMap'

/**
 * The four states `PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md` Section 5
 * names, plus the "checking" state Section 5.5 renders before the first
 * poll completes. `configuration` is Section 4.4's own separate, third
 * case — a credential mismatch between modules. It is reported only when
 * a configuration mismatch is the *only* thing wrong; if a genuine
 * operational outage exists anywhere at the same time, `problem` is
 * reported instead (Product Owner correction, 2026-08-03: an operational
 * problem always takes priority over a configuration diagnostic — see
 * [evaluateOwnerStatus]'s own KDoc for the exact rule and why).
 */
export type OwnerStatus = 'checking' | 'ok' | 'problem' | 'delayed' | 'configuration' | 'unreachable'

export interface OwnerStatusResult {
  status: OwnerStatus
  /** Plain-language lines, one per affected module (Section 5.3: "каждая своей строкой", no severity ordering). */
  problemLines: string[]
  /** Present only for [OwnerStatus.delayed]. */
  delayLine: string | null
}

/**
 * A queue is only reported as delayed once it has been stuck long enough
 * to be a genuine stall, not a record still mid-flight through the
 * ordinary outbox relay (which runs every few seconds under normal
 * conditions — `pios.outbox.relay.fixed-delay-ms`, default 2000). No ADR
 * or design-document text fixes this number; 60 seconds is this file's own
 * deliberate, disclosed default, chosen so the yellow state does not
 * flicker on for a record that is a few relay ticks away from publishing.
 */
const QUEUE_DELAY_THRESHOLD_SECONDS = 60

/**
 * Turns the five raw `GET /v1/health` outcomes into exactly one of the
 * states this screen's main card renders.
 *
 * **Priority rule, per the Product Owner's binding correction (2026-08-03):
 * "Реальная проблема эксплуатации всегда имеет приоритет над
 * диагностическим состоянием конфигурации."** A genuine operational
 * outage (a module unreachable, or answering but unable to serve) is
 * checked, and reported as `problem`, **before** the configuration check —
 * not after. If both an outage and a configuration mismatch exist at the
 * same time (e.g. one module rejects the credential while a *different*
 * module is simply down), the outage wins: this function must never
 * return `configuration` — whose own screen copy says "Это не сбой в
 * работе с заказами" — while a real outage is also present, since that
 * would make that sentence false on screen. `configuration` is reported
 * only when misconfiguration is the *only* thing wrong: no module is
 * unreachable or down. A queue delay is reported only if nothing worse
 * (outage or configuration) is true; anything else is the green "working"
 * state.
 */
export function evaluateOwnerStatus(healths: ModuleHealth[]): OwnerStatusResult {
  if (healths.length === 0) {
    return { status: 'checking', problemLines: [], delayLine: null }
  }

  if (healths.every((health) => health.outcome === 'unreachable')) {
    return { status: 'unreachable', problemLines: [], delayLine: null }
  }

  const outages = healths.filter((health) => health.outcome === 'unreachable' || health.outcome === 'down')
  if (outages.length > 0) {
    return {
      status: 'problem',
      problemLines: outages.map((health) =>
        health.outcome === 'down' ? MODULE_DOWN_TEXT[health.module] : MODULE_UNREACHABLE_TEXT[health.module]
      ),
      delayLine: null,
    }
  }

  const misconfigured = healths.filter((health) => health.outcome === 'unauthorized')
  if (misconfigured.length > 0) {
    return { status: 'configuration', problemLines: [], delayLine: null }
  }

  const delayed = healths.find(
    (health) =>
      (health.outboxOldestAgeSeconds ?? 0) > QUEUE_DELAY_THRESHOLD_SECONDS && QUEUE_DELAY_TEXT[health.module]
  )
  if (delayed) {
    return { status: 'delayed', problemLines: [], delayLine: QUEUE_DELAY_TEXT[delayed.module] ?? null }
  }

  return { status: 'ok', problemLines: [], delayLine: null }
}
