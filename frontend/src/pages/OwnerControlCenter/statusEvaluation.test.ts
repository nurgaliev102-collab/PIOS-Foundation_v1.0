import { describe, expect, it } from 'vitest'
import { evaluateOwnerStatus } from './statusEvaluation'
import type { ModuleHealth } from './healthPoll'

function health(overrides: Partial<ModuleHealth> & Pick<ModuleHealth, 'module' | 'outcome'>): ModuleHealth {
  return {
    outboxPending: null,
    outboxOldestAgeSeconds: null,
    checkedAt: null,
    ...overrides,
  }
}

const ALL_UP: ModuleHealth[] = [
  health({ module: 'driver-management', outcome: 'up' }),
  health({ module: 'passenger-experience', outcome: 'up' }),
  health({ module: 'order-management', outcome: 'up' }),
  health({ module: 'dispatch', outcome: 'up' }),
  health({ module: 'identity', outcome: 'up' }),
]

describe('evaluateOwnerStatus', () => {
  it('reports "checking" before any health result has arrived', () => {
    expect(evaluateOwnerStatus([]).status).toBe('checking')
  })

  it('reports "ok" (green) when every module answers UP with no backlog', () => {
    expect(evaluateOwnerStatus(ALL_UP).status).toBe('ok')
  })

  it('reports "unreachable" (grey) only when every module fails to answer at all', () => {
    const allUnreachable = ALL_UP.map((h) => health({ module: h.module, outcome: 'unreachable' }))

    expect(evaluateOwnerStatus(allUnreachable).status).toBe('unreachable')
  })

  it('reports "configuration" (grey, distinct state), not "problem", when a module rejects the credential', () => {
    const withOneUnauthorized = [
      ...ALL_UP.slice(0, 4),
      health({ module: 'identity', outcome: 'unauthorized' }),
    ]

    const result = evaluateOwnerStatus(withOneUnauthorized)

    expect(result.status).toBe('configuration')
    expect(result.problemLines).toHaveLength(0)
  })

  it('reports "problem" (red) with one line per affected module when a module is unreachable but not all are', () => {
    const withOneDown = [...ALL_UP.slice(0, 4), health({ module: 'identity', outcome: 'unreachable' })]

    const result = evaluateOwnerStatus(withOneDown)

    expect(result.status).toBe('problem')
    expect(result.problemLines).toEqual(['Новый водитель сейчас не сможет зарегистрироваться.'])
  })

  it('reports "problem" (red) when a module answers but its own database is down', () => {
    const withOneDatabaseDown = [
      ...ALL_UP.slice(0, 3),
      health({ module: 'dispatch', outcome: 'down' }),
      ALL_UP[4],
    ]

    const result = evaluateOwnerStatus(withOneDatabaseDown)

    expect(result.status).toBe('problem')
  })

  it('reports "delayed" (yellow) when every module answers but one has a stale outbox backlog', () => {
    const withStaleBacklog = [
      ...ALL_UP.slice(0, 3),
      health({ module: 'dispatch', outcome: 'up', outboxPending: 14, outboxOldestAgeSeconds: 372 }),
      ALL_UP[4],
    ]

    const result = evaluateOwnerStatus(withStaleBacklog)

    expect(result.status).toBe('delayed')
    expect(result.delayLine).toBe('Заказы принимаются, но доходят до водителей с задержкой.')
  })

  it('does not report "delayed" for a small, fresh backlog that is just mid-flight through the relay', () => {
    const withFreshBacklog = [
      ...ALL_UP.slice(0, 3),
      health({ module: 'dispatch', outcome: 'up', outboxPending: 2, outboxOldestAgeSeconds: 3 }),
      ALL_UP[4],
    ]

    expect(evaluateOwnerStatus(withFreshBacklog).status).toBe('ok')
  })

  // --- Priority: a real operational outage always wins over a
  // configuration diagnostic (Product Owner correction, 2026-08-03).
  // Regression coverage for the bug QA found: "configuration" used to be
  // checked and returned before the outage check ever ran, so an outage
  // elsewhere was silently dropped whenever any module was also
  // unauthorized.

  it('reports "problem" (not "configuration"), with the outage\'s own problemLines, when one module is unreachable and a different module is unauthorized at the same time', () => {
    // The exact synthetic input QA used to confirm the defect:
    // order-management unreachable (a real outage), identity unauthorized
    // (a configuration mismatch), present simultaneously.
    const mixed = [
      health({ module: 'driver-management', outcome: 'up' }),
      health({ module: 'passenger-experience', outcome: 'up' }),
      health({ module: 'order-management', outcome: 'unreachable' }),
      health({ module: 'dispatch', outcome: 'up' }),
      health({ module: 'identity', outcome: 'unauthorized' }),
    ]

    const result = evaluateOwnerStatus(mixed)

    expect(result.status).toBe('problem')
    expect(result.problemLines).toEqual(['Новые пассажиры сейчас не могут оформить заказ.'])
  })

  it('reports "problem" (not "configuration") when a module\'s own database is down and a different module is unauthorized at the same time', () => {
    const mixed = [
      ...ALL_UP.slice(0, 2),
      health({ module: 'order-management', outcome: 'down' }),
      ALL_UP[3],
      health({ module: 'identity', outcome: 'unauthorized' }),
    ]

    expect(evaluateOwnerStatus(mixed).status).toBe('problem')
  })

  it('still reports "configuration" when misconfiguration is the only thing wrong — no outage anywhere', () => {
    const configurationOnly = [...ALL_UP.slice(0, 4), health({ module: 'identity', outcome: 'unauthorized' })]

    const result = evaluateOwnerStatus(configurationOnly)

    expect(result.status).toBe('configuration')
    expect(result.problemLines).toHaveLength(0)
  })

  it('still reports "problem" when an outage is the only thing wrong — no configuration mismatch anywhere', () => {
    const outageOnly = [...ALL_UP.slice(0, 4), health({ module: 'identity', outcome: 'unreachable' })]

    const result = evaluateOwnerStatus(outageOnly)

    expect(result.status).toBe('problem')
    expect(result.problemLines).toEqual(['Новый водитель сейчас не сможет зарегистрироваться.'])
  })
})
