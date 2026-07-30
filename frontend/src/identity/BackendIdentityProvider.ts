import { ApiError, request } from '../api/apiClient'
import type { IdentityProvider, StoredIdentity } from './IdentityProvider'

// Identity's own local port (INTERFACE_CONTRACTS.md-style convention, ADR-038) —
// distinct from apiClientConfig's default (Driver Management's port), same
// pattern every other page already uses for a module beyond the first.
const IDENTITY_BASE_URL = import.meta.env.VITE_IDENTITY_BASE_URL ?? 'http://localhost:8086'

const STORAGE_KEY = 'pios.identity'

interface IdentityApiResponse {
  id: string
  phone: string | null
  driverId: string | null
}

/**
 * Today's only [IdentityProvider] (ADR-039). Storage on this device
 * remembers *which* identity is this device's own — the identity itself,
 * and its driver association, are real records on the `identity` backend,
 * not fabricated locally. This is still not authentication: nothing here
 * proves the person using this device is who the stored identity claims to
 * be, the same honesty `ADR-038`/`ADR-039` already state as their explicit
 * scope boundary.
 *
 * Sprint 2 (Identity MVP): [restoreIdentity] is the only method that reads
 * the local pointer and then calls the backend to confirm it — every
 * other read (`getStoredIdentity`) stays a synchronous local cache read.
 * Re-entry ("повторный вход") therefore reflects `identity`'s own current,
 * real state, not a value cached indefinitely on a device that could go
 * stale.
 */
export class BackendIdentityProvider implements IdentityProvider {
  getStoredIdentity(): StoredIdentity | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) {
        return null
      }
      const parsed: unknown = JSON.parse(raw)
      return isStoredIdentity(parsed) ? parsed : null
    } catch {
      return null
    }
  }

  async createIdentity(): Promise<StoredIdentity> {
    const response = await request<IdentityApiResponse>('/v1/identities', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({}),
      baseUrl: IDENTITY_BASE_URL,
    })
    const identity: StoredIdentity = { identityId: response.id, driverId: response.driverId }
    this.persist(identity)
    return identity
  }

  async attachDriver(driverId: string): Promise<StoredIdentity> {
    const current = this.getStoredIdentity()
    if (!current) {
      throw new Error('attachDriver called before an identity exists')
    }
    const response = await request<IdentityApiResponse>(`/v1/identities/${current.identityId}/driver`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ driverId }),
      baseUrl: IDENTITY_BASE_URL,
    })
    const identity: StoredIdentity = { identityId: response.id, driverId: response.driverId }
    this.persist(identity)
    return identity
  }

  /**
   * Sprint 2 (Identity MVP): see [IdentityProvider.restoreIdentity]'s own
   * KDoc for the contract. This is the only place `getStoredIdentity`'s
   * cache is cross-checked against the backend — every other read in this
   * class stays synchronous and local, unchanged.
   */
  async restoreIdentity(): Promise<StoredIdentity | null> {
    const cached = this.getStoredIdentity()
    if (!cached) {
      return null
    }
    try {
      const response = await request<IdentityApiResponse>(`/v1/identities/${cached.identityId}`, {
        baseUrl: IDENTITY_BASE_URL,
      })
      const identity: StoredIdentity = { identityId: response.id, driverId: response.driverId }
      this.persist(identity)
      return identity
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        // This device's own pointer no longer resolves to anything the
        // backend recognizes -- clear it so the welcome screen offers a
        // fresh start instead of getting stuck pointing at nothing.
        this.clear()
        return null
      }
      // Any other failure (offline, backend briefly down): keep the
      // cached pointer and let the caller use it, rather than force a
      // person back into onboarding over a momentary network problem.
      return cached
    }
  }

  private persist(identity: StoredIdentity): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(identity))
    } catch {
      // Storage may be unavailable (private browsing, quota exceeded). The
      // identity still exists on the backend; only this device's own
      // pointer to it fails to survive a reload — same accepted limitation
      // `localPassengerIdentity.ts` already documents for its own case.
    }
  }

  private clear(): void {
    try {
      localStorage.removeItem(STORAGE_KEY)
    } catch {
      // Storage may be unavailable -- nothing further to clear.
    }
  }
}

function isStoredIdentity(value: unknown): value is StoredIdentity {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const candidate = value as Record<string, unknown>
  return typeof candidate.identityId === 'string' && (candidate.driverId === null || typeof candidate.driverId === 'string')
}
