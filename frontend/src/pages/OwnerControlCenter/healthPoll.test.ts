import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { pollAllModuleHealth } from './healthPoll'

const CREDENTIAL = { username: 'owner', password: 'whatever' }

// Regression test for the 2026-08-17 Owner Control Center incident
// investigation: `fetchModuleHealth`'s 401 (and unknown-status) branches
// never read the `Response` body, which Chrome's Network panel reports as
// "(canceled)"/`net::ERR_ABORTED` for an otherwise successfully-resolved
// request -- indistinguishable, at a glance, from a genuine
// `AbortSignal.timeout` cancellation. This does not assert anything about
// *why* the body must be drained (jsdom's Response doesn't expose enough
// to observe stream state) -- it asserts the behavioural contract that
// motivated the fix: a 401/unknown-status response's body method is
// actually invoked, and the health outcome mapping is unaffected either way.
describe('pollAllModuleHealth / fetchModuleHealth', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('drains the body on a 401 and still reports "unauthorized"', async () => {
    const textSpy = vi.fn().mockResolvedValue('')
    vi.mocked(fetch).mockResolvedValue({ status: 401, text: textSpy } as unknown as Response)

    const results = await pollAllModuleHealth(CREDENTIAL)

    expect(results.every((r) => r.outcome === 'unauthorized')).toBe(true)
    expect(textSpy).toHaveBeenCalledTimes(5)
  })

  it('drains the body on an unexpected status and still reports "unreachable"', async () => {
    const textSpy = vi.fn().mockResolvedValue('')
    vi.mocked(fetch).mockResolvedValue({ status: 500, text: textSpy } as unknown as Response)

    const results = await pollAllModuleHealth(CREDENTIAL)

    expect(results.every((r) => r.outcome === 'unreachable')).toBe(true)
    expect(textSpy).toHaveBeenCalledTimes(5)
  })

  it('still reports "up" from a 200 with a real body, unaffected by the drain fix', async () => {
    vi.mocked(fetch).mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ module: 'identity', status: 'UP', database: 'UP', checkedAt: '2026-08-17T00:00:00Z' }),
    } as unknown as Response)

    const results = await pollAllModuleHealth(CREDENTIAL)

    expect(results.every((r) => r.outcome === 'up')).toBe(true)
  })

  it('reports "unreachable" when fetch itself rejects (network failure or a genuine AbortSignal timeout)', async () => {
    vi.mocked(fetch).mockRejectedValue(new DOMException('The operation was aborted', 'TimeoutError'))

    const results = await pollAllModuleHealth(CREDENTIAL)

    expect(results.every((r) => r.outcome === 'unreachable')).toBe(true)
  })
})
