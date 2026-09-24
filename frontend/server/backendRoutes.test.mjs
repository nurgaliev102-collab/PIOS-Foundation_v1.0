import { describe, expect, it } from 'vitest'
import { resolveBackendTarget } from './backendRoutes.mjs'

describe('production backend routing', () => {
  it.each([
    '/v1/handoffs',
    '/v1/handoffs?assignmentId=assignment-1',
    '/v1/handoffs/handoff-1/withdraw',
    '/v1/handoffs/handoff-1/accept-substitute',
    '/v1/handoffs/handoff-1/decline-substitute',
    '/v1/handoffs/handoff-1/consent',
    '/v1/handoffs/handoff-1/refuse',
  ])('routes Dispatch handoff API path %s to port 8084', (path) => {
    expect(resolveBackendTarget(path)).toBe('http://localhost:8084')
  })

  it('does not expose dormant Network Management persons API', () => {
    expect(resolveBackendTarget('/v1/persons')).toBeNull()
    expect(resolveBackendTarget('/v1/persons/person-1/profiles')).toBeNull()
  })
})
