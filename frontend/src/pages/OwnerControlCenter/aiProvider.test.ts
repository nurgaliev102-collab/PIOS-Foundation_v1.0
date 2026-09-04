import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PilotAnalyticsInput } from './pilotAnalytics'

vi.mock('../../api/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/apiClient')>()
  return {
    ...actual,
    request: vi.fn(),
  }
})

import { request } from '../../api/apiClient'
import { AdvisorOutcomeError, BackendAIProvider, getActiveAIProvider } from './aiProvider'

const mockedRequest = vi.mocked(request)
const OWNER_CREDENTIAL = { username: 'owner', password: 'secret' }
const EXPECTED_BASIC_HEADER = `Basic ${btoa('owner:secret')}`

const SAMPLE_INPUT: PilotAnalyticsInput = {
  generatedAt: '2026-08-17T12:00:00.000Z',
  periodLabel: 'Весь период наблюдения (все данные, доступные системе сейчас)',
  orders: { total: 4, completed: 3, cancelled: 1, open: 0 },
  proposals: { total: 4, accepted: 3, declined: 0, lapsed: 1, withdrawn: 0, open: 0 },
  assignments: { total: 3, completed: 3, inProgress: 0 },
  drivers: { total: 2, available: 1, withActivity: 2 },
  reactionTime: { averageMinutes: null, medianMinutes: null, sampleSize: 0 },
  health: { modulesUp: 5, modulesTotal: 5 },
}

describe('BackendAIProvider', () => {
  beforeEach(() => {
    mockedRequest.mockReset()
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('POSTs to /v1/advisor/analyze with the owner Basic header and the analytics input as the body', async () => {
    mockedRequest.mockResolvedValueOnce({
      outcome: 'ok',
      result: {
        status: 'attention',
        summary: 'x',
        keyFindings: [],
        risks: [],
        recommendations: [],
        metrics: { acceptanceRate: 1, completionRate: 0.75, cancellationRate: 0.25 },
        generatedAt: SAMPLE_INPUT.generatedAt,
        providerName: 'mock',
      },
      message: null,
    })

    const provider = new BackendAIProvider(OWNER_CREDENTIAL)
    await provider.analyze(SAMPLE_INPUT)

    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/v1/advisor/analyze')
    expect((init as RequestInit).method).toBe('POST')
    expect((init as RequestInit).headers).toMatchObject({ Authorization: EXPECTED_BASIC_HEADER })
    expect(JSON.parse((init as RequestInit).body as string)).toEqual(SAMPLE_INPUT)
  })

  it('returns result unchanged when outcome is "ok"', async () => {
    const result = {
      status: 'ok' as const,
      summary: 'Всё хорошо',
      keyFindings: ['finding'],
      risks: [],
      recommendations: ['rec'],
      metrics: { acceptanceRate: 1, completionRate: 1, cancellationRate: 0 },
      generatedAt: SAMPLE_INPUT.generatedAt,
      providerName: 'mock',
    }
    mockedRequest.mockResolvedValueOnce({ outcome: 'ok', result, message: null })

    const provider = new BackendAIProvider(OWNER_CREDENTIAL)
    const analysis = await provider.analyze(SAMPLE_INPUT)

    expect(analysis).toEqual(result)
  })

  it('throws AdvisorOutcomeError with the backend\'s own honest message when outcome is "provider_unavailable"', async () => {
    mockedRequest.mockResolvedValueOnce({
      outcome: 'provider_unavailable',
      result: null,
      message: 'AI-помощник сейчас недоступен. Попробуйте позже.',
    })

    const provider = new BackendAIProvider(OWNER_CREDENTIAL)

    await expect(provider.analyze(SAMPLE_INPUT)).rejects.toMatchObject({
      constructor: AdvisorOutcomeError,
      outcome: 'provider_unavailable',
      userMessage: 'AI-помощник сейчас недоступен. Попробуйте позже.',
    })
  })

  it('throws AdvisorOutcomeError distinctly for "budget_exceeded" -- rate limit is not the same failure as provider unavailability', async () => {
    mockedRequest.mockResolvedValueOnce({
      outcome: 'budget_exceeded',
      result: null,
      message: 'Слишком частые запросы. Попробуйте через несколько секунд.',
    })

    const provider = new BackendAIProvider(OWNER_CREDENTIAL)

    await expect(provider.analyze(SAMPLE_INPUT)).rejects.toMatchObject({
      outcome: 'budget_exceeded',
      userMessage: 'Слишком частые запросы. Попробуйте через несколько секунд.',
    })
  })

  it('falls back to a generic message if the backend omits one for a non-ok outcome', async () => {
    mockedRequest.mockResolvedValueOnce({ outcome: 'invalid_input', result: null, message: null })

    const provider = new BackendAIProvider(OWNER_CREDENTIAL)

    await expect(provider.analyze(SAMPLE_INPUT)).rejects.toMatchObject({ outcome: 'invalid_input' })
  })

  it('lets a network/auth failure (ApiError, from request() itself) propagate unchanged -- distinct from AdvisorOutcomeError', async () => {
    mockedRequest.mockRejectedValueOnce(new Error('network down'))

    const provider = new BackendAIProvider(OWNER_CREDENTIAL)

    await expect(provider.analyze(SAMPLE_INPUT)).rejects.toThrow('network down')
  })
})

describe('getActiveAIProvider', () => {
  it('returns a BackendAIProvider -- the only frontend implementation; a real provider is a backend-only change (ADR-056 Decision 1)', () => {
    const provider = getActiveAIProvider(OWNER_CREDENTIAL)
    expect(provider).toBeInstanceOf(BackendAIProvider)
    expect(provider.name).toBe('backend')
  })
})
