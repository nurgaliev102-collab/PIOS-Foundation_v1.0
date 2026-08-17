import { describe, expect, it } from 'vitest'
import type { PilotAnalyticsInput } from './pilotAnalytics'
import { MockAIProvider } from './aiProvider'

const BASE_INPUT: PilotAnalyticsInput = {
  generatedAt: '2026-08-17T12:00:00.000Z',
  periodLabel: 'Весь период наблюдения (все данные, доступные системе сейчас)',
  orders: { total: 0, completed: 0, cancelled: 0, open: 0 },
  proposals: { total: 0, accepted: 0, declined: 0, lapsed: 0, withdrawn: 0, open: 0 },
  assignments: { total: 0, completed: 0, inProgress: 0 },
  drivers: { total: 0, available: 0, withActivity: 0 },
  reactionTime: { averageMinutes: null, medianMinutes: null, sampleSize: 0 },
  health: { modulesUp: 5, modulesTotal: 5 },
}

function input(overrides: Partial<PilotAnalyticsInput>): PilotAnalyticsInput {
  return { ...BASE_INPUT, ...overrides }
}

describe('MockAIProvider', () => {
  it('reports "unknown" and asks to wait for data when there are zero orders', async () => {
    const provider = new MockAIProvider()
    const result = await provider.analyze(input({}))

    expect(result.status).toBe('unknown')
    expect(result.summary).toMatch(/Недостаточно данных/)
    expect(result.keyFindings).toEqual([])
    expect(result.risks).toEqual([])
    expect(result.metrics).toEqual({ acceptanceRate: null, completionRate: null, cancellationRate: null })
  })

  it('reports "ok" for a clean pilot: no lapsed/declined proposals, no cancellations, every driver active, all modules up', async () => {
    const provider = new MockAIProvider()
    const result = await provider.analyze(
      input({
        orders: { total: 5, completed: 5, cancelled: 0, open: 0 },
        proposals: { total: 5, accepted: 5, declined: 0, lapsed: 0, withdrawn: 0, open: 0 },
        drivers: { total: 2, available: 2, withActivity: 2 },
      })
    )

    expect(result.status).toBe('ok')
    expect(result.risks).toEqual([])
    expect(result.metrics.acceptanceRate).toBe(1)
    expect(result.metrics.completionRate).toBe(1)
    expect(result.metrics.cancellationRate).toBe(0)
  })

  it('reports "critical" when a backend module is down, regardless of otherwise-healthy metrics', async () => {
    const provider = new MockAIProvider()
    const result = await provider.analyze(
      input({
        orders: { total: 3, completed: 3, cancelled: 0, open: 0 },
        proposals: { total: 3, accepted: 3, declined: 0, lapsed: 0, withdrawn: 0, open: 0 },
        health: { modulesUp: 4, modulesTotal: 5 },
      })
    )

    expect(result.status).toBe('critical')
    expect(result.risks.some((r) => r.includes('модул'))).toBe(true)
  })

  it('reports "critical" when the cancellation rate is at or above 50%', async () => {
    const provider = new MockAIProvider()
    const result = await provider.analyze(
      input({
        orders: { total: 4, completed: 2, cancelled: 2, open: 0 },
        proposals: { total: 4, accepted: 2, declined: 0, lapsed: 0, withdrawn: 0, open: 0 },
      })
    )

    expect(result.status).toBe('critical')
  })

  it('reports "attention" when there are lapsed proposals, without being "critical"', async () => {
    const provider = new MockAIProvider()
    const result = await provider.analyze(
      input({
        orders: { total: 5, completed: 4, cancelled: 0, open: 1 },
        proposals: { total: 5, accepted: 4, declined: 0, lapsed: 1, withdrawn: 0, open: 0 },
        drivers: { total: 1, available: 1, withActivity: 1 },
      })
    )

    expect(result.status).toBe('attention')
    expect(result.risks.some((r) => r.toLowerCase().includes('просрочен'))).toBe(true)
    expect(result.recommendations.length).toBeGreaterThan(0)
  })

  it('reports "attention" when drivers exist but none has received any proposal', async () => {
    const provider = new MockAIProvider()
    const result = await provider.analyze(
      input({
        orders: { total: 1, completed: 1, cancelled: 0, open: 0 },
        proposals: { total: 1, accepted: 1, declined: 0, lapsed: 0, withdrawn: 0, open: 0 },
        drivers: { total: 3, available: 3, withActivity: 0 },
      })
    )

    expect(result.status).toBe('attention')
    expect(result.risks.some((r) => r.includes('Ни один водитель'))).toBe(true)
  })

  it('includes the real reaction-time sample in key findings only when a sample exists', async () => {
    const provider = new MockAIProvider()
    const withSample = await provider.analyze(
      input({
        orders: { total: 1, completed: 1, cancelled: 0, open: 0 },
        proposals: { total: 1, accepted: 1, declined: 0, lapsed: 0, withdrawn: 0, open: 0 },
        reactionTime: { averageMinutes: 3.4, medianMinutes: 3, sampleSize: 1 },
      })
    )
    expect(withSample.keyFindings.some((f) => f.includes('реакции'))).toBe(true)

    const withoutSample = await provider.analyze(
      input({
        orders: { total: 1, completed: 1, cancelled: 0, open: 0 },
        proposals: { total: 1, accepted: 1, declined: 0, lapsed: 0, withdrawn: 0, open: 0 },
      })
    )
    expect(withoutSample.keyFindings.some((f) => f.includes('реакции'))).toBe(false)
  })

  it('is deterministic: analyzing the same input twice produces the identical result, never a random one', async () => {
    const provider = new MockAIProvider()
    const someInput = input({
      orders: { total: 6, completed: 4, cancelled: 1, open: 1 },
      proposals: { total: 6, accepted: 4, declined: 1, lapsed: 1, withdrawn: 0, open: 0 },
      drivers: { total: 2, available: 1, withActivity: 2 },
    })

    const first = await provider.analyze(someInput)
    const second = await provider.analyze(someInput)

    expect(first).toEqual(second)
  })
})
