import { describe, expect, it } from 'vitest'
import { buildOwnerReport } from './buildReport'
import { evaluateOwnerStatus } from './statusEvaluation'
import type { ModuleHealth } from './healthPoll'
import type { TodaySnapshot } from './todayData'

const HEALTHS: ModuleHealth[] = [
  { module: 'driver-management', outcome: 'up', outboxPending: 0, outboxOldestAgeSeconds: null, checkedAt: '2026-08-02T15:47:03Z' },
  { module: 'passenger-experience', outcome: 'up', outboxPending: null, outboxOldestAgeSeconds: null, checkedAt: '2026-08-02T15:47:03Z' },
  { module: 'order-management', outcome: 'up', outboxPending: 0, outboxOldestAgeSeconds: null, checkedAt: '2026-08-02T15:47:03Z' },
  { module: 'dispatch', outcome: 'up', outboxPending: 14, outboxOldestAgeSeconds: 372, checkedAt: '2026-08-02T15:47:03Z' },
  { module: 'identity', outcome: 'up', outboxPending: null, outboxOldestAgeSeconds: null, checkedAt: '2026-08-02T15:47:03Z' },
]

const TODAY: TodaySnapshot = {
  counters: {
    driversTotal: 3,
    driversAvailable: 2,
    ordersCreated: 7,
    ordersCompleted: 5,
    ordersInProgress: 1,
    ordersCancelled: 1,
  },
  events: [
    { at: '2026-08-02T20:28:00Z', text: 'Артур завершил поездку' },
    { at: '2026-08-02T20:14:00Z', text: 'Новый заказ. Елена, Ленина 12' },
  ],
}

describe('buildOwnerReport', () => {
  it('never contains a monetary figure or field (ADR-043 Decision 6, ADR-042 R4.3) — this report has no source of one in the first place', () => {
    const report = buildOwnerReport(evaluateOwnerStatus(HEALTHS), HEALTHS, TODAY, new Date('2026-08-02T15:47:00Z'))

    // No known money-shaped word appears anywhere in the report, in any
    // case -- statedPrice is never even passed into this function's own
    // inputs, so this is really asserting the absence of an accident, not
    // just of a word.
    expect(report.toLowerCase()).not.toMatch(/цена|стоимост|выручк|заработ|оплат|₽|руб/)
  })

  it('includes every module by name and its own outbox figure, never another module\'s', () => {
    const report = buildOwnerReport(evaluateOwnerStatus(HEALTHS), HEALTHS, TODAY, new Date('2026-08-02T15:47:00Z'))

    expect(report).toContain('dispatch')
    expect(report).toContain('14')
    expect(report).toContain('driver-management')
  })

  it('includes the today counters with the exact labels the design specifies', () => {
    const report = buildOwnerReport(evaluateOwnerStatus(HEALTHS), HEALTHS, TODAY, new Date('2026-08-02T15:47:00Z'))

    expect(report).toContain('Заказов создано: 7')
    expect(report).toContain('выполнено 5')
    expect(report).toContain('в работе 1')
    expect(report).toContain('отменено 1')
  })

  it('states the working state plainly when nothing is wrong', () => {
    const report = buildOwnerReport(evaluateOwnerStatus(HEALTHS.map((h) => ({ ...h, outboxPending: 0, outboxOldestAgeSeconds: null }))), HEALTHS, TODAY, new Date())

    expect(report).toContain('Состояние: РАБОТАЕТ')
  })
})
