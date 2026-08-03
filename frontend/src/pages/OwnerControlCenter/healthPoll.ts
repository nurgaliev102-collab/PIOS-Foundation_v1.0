import { PILOT_MODULES, type PilotModule } from './moduleBaseUrls'
import { toBasicAuthorizationHeader, type OwnerCredential } from './ownerCredential'

/**
 * `GET /v1/health/<module-name>`'s own wire shape (ADR-043 Decision 2;
 * `PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md` Section 7.2). The per-module
 * path suffix is a routing detail (`PILOT_INFRASTRUCTURE_ROUTING_DECISION.md`
 * Variant A) and does not change this shape. `outbox` is absent for
 * `passenger-experience` and `identity`, which own no outbox table.
 */
interface HealthResponseBody {
  module: string
  status: 'UP' | 'DOWN'
  database: 'UP' | 'DOWN'
  outbox?: { pending: number; oldestPendingAgeSeconds: number | null }
  checkedAt: string
}

export type ModuleHealthOutcome = 'up' | 'down' | 'unauthorized' | 'unreachable'

export interface ModuleHealth {
  module: PilotModule['key']
  outcome: ModuleHealthOutcome
  outboxPending: number | null
  outboxOldestAgeSeconds: number | null
  checkedAt: string | null
}

/**
 * Calls one module's own `GET /v1/health/<module-name>` directly (not
 * through `api/apiClient.ts`'s shared `request` helper): that helper
 * discards the response body on any non-2xx status, but this endpoint's
 * `503` response carries a real body (`status: "DOWN"`, ADR-043 Decision
 * 2's own `PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md` Section 7.2) this
 * screen must read, not merely detect as an error.
 *
 * The `/<module-name>` suffix (`pilotModule.key`, matching each
 * controller's own `MODULE_NAME`) exists only because all five modules
 * otherwise serve an identical `/v1/health`, which a single-origin proxy
 * cannot route to five different ports (`PILOT_INFRASTRUCTURE_ROUTING_DECISION.md`
 * Variant A, approved 2026-08-03). It carries no meaning beyond routing.
 *
 * Never throws: every failure mode this endpoint can produce — wrong
 * credential (`401`), the module's own database being unreachable
 * (`200`-adjacent `503` with a body), or the module not answering at all
 * (network failure) — is returned as a value, since the console must
 * render all three differently rather than treat any of them as an
 * unhandled error (ADR-043's own disclosed requirement that "I could not
 * reach this" stay visually distinct from "this is zero").
 */
/**
 * `PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md` Section 5.5: "если за 10
 * секунд не удалось получить ничего вообще" — a module that never answers
 * must resolve as unreachable within a bounded time, not hang on the
 * browser's own much longer default `fetch` timeout.
 */
const HEALTH_CHECK_TIMEOUT_MS = 10_000

async function fetchModuleHealth(pilotModule: PilotModule, credential: OwnerCredential): Promise<ModuleHealth> {
  try {
    const response = await fetch(`${pilotModule.baseUrl}/v1/health/${pilotModule.key}`, {
      headers: {
        Accept: 'application/json',
        Authorization: toBasicAuthorizationHeader(credential),
      },
      signal: AbortSignal.timeout(HEALTH_CHECK_TIMEOUT_MS),
    })

    if (response.status === 401) {
      return {
        module: pilotModule.key,
        outcome: 'unauthorized',
        outboxPending: null,
        outboxOldestAgeSeconds: null,
        checkedAt: null,
      }
    }

    if (response.status === 200 || response.status === 503) {
      const body = (await response.json()) as HealthResponseBody
      return {
        module: pilotModule.key,
        outcome: response.status === 200 ? 'up' : 'down',
        outboxPending: body.outbox?.pending ?? null,
        outboxOldestAgeSeconds: body.outbox?.oldestPendingAgeSeconds ?? null,
        checkedAt: body.checkedAt,
      }
    }

    return {
      module: pilotModule.key,
      outcome: 'unreachable',
      outboxPending: null,
      outboxOldestAgeSeconds: null,
      checkedAt: null,
    }
  } catch {
    return {
      module: pilotModule.key,
      outcome: 'unreachable',
      outboxPending: null,
      outboxOldestAgeSeconds: null,
      checkedAt: null,
    }
  }
}

/** Polls every pilot module's own `GET /v1/health/<module-name>`, in parallel (Section 7.5 step 1). */
export async function pollAllModuleHealth(credential: OwnerCredential): Promise<ModuleHealth[]> {
  return Promise.all(PILOT_MODULES.map((pilotModule) => fetchModuleHealth(pilotModule, credential)))
}

/**
 * The login screen's own check (ADR-044 Decision 4): "a 200 from any
 * pilot module proves the credential correct... a 401 from a module that
 * answered proves it incorrect... no answer at all from any module is a
 * third case." All five are polled — not just one — so a single
 * unreachable module never produces a false "cannot verify" while the
 * other four could have answered.
 */
export async function verifyOwnerCredential(
  credential: OwnerCredential
): Promise<'valid' | 'invalid' | 'unreachable'> {
  const results = await Promise.all(PILOT_MODULES.map((pilotModule) => fetchModuleHealth(pilotModule, credential)))
  if (results.some((result) => result.outcome === 'up' || result.outcome === 'down')) {
    return 'valid'
  }
  if (results.some((result) => result.outcome === 'unauthorized')) {
    return 'invalid'
  }
  return 'unreachable'
}
