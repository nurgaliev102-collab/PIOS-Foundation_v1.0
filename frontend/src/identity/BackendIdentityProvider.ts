import { ApiError, request } from '../api/apiClient'
import type { IdentityProvider, StoredIdentity } from './IdentityProvider'

// Identity's own local port (INTERFACE_CONTRACTS.md-style convention, ADR-038).
// In a browser context we must issue same-origin requests so the preview
// server (vite preview) can proxy `/v1/identities` to the real backend.
// When running in a non-browser environment (tests, server-side tooling)
// fall back to the configured absolute URL so those environments keep
// working unchanged.
const IDENTITY_BASE_URL =
  typeof window !== 'undefined' && typeof (window as any).location !== 'undefined'
    ? ''
    : import.meta.env.VITE_IDENTITY_BASE_URL ?? 'http://localhost:8086'

const STORAGE_KEY = 'pios.identity'

/** `POST /v1/identities/register|login`'s own response shape (ADR-055 Decision 3). */
interface AuthResponse {
  identityId: string
  driverId: string | null
  token: string
  expiresAt: string
}

/**
 * `GET /v1/identities/me`'s own response shape (ADR-055 Decision 3).
 * `POST /v1/identities/{id}/driver` used to share this shape too, until
 * the ADR-055 Decision 6 addendum changed it to [AuthResponse] instead.
 */
interface IdentityApiResponse {
  id: string
  phone: string | null
  driverId: string | null
}

/**
 * Today's only [IdentityProvider] (ADR-039, extended by ADR-055). Storage
 * on this device holds a real, verifiable session — a signed token the
 * backend actually checks, not just an id it hands back to whoever asks
 * (that was the gap ADR-055 closed; `POST /v1/identities` with no
 * credential no longer exists).
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

  async register(phone: string, password: string): Promise<StoredIdentity> {
    const response = await request<AuthResponse>('/v1/identities/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone, password }),
      baseUrl: IDENTITY_BASE_URL,
    })
    return this.persistAuthResponse(response)
  }

  async login(phone: string, password: string): Promise<StoredIdentity> {
    const response = await request<AuthResponse>('/v1/identities/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone, password }),
      baseUrl: IDENTITY_BASE_URL,
    })
    return this.persistAuthResponse(response)
  }

  logout(): void {
    this.clear()
  }

  async attachDriver(driverId: string): Promise<StoredIdentity> {
    const current = this.getStoredIdentity()
    if (!current) {
      throw new Error('attachDriver called before a session exists')
    }
    // ADR-055 Decision 6 addendum: `POST /v1/identities/{id}/driver` now
    // returns a fresh `AuthResponse`, not `IdentityApiResponse` -- the
    // registration-time token's `drv` claim is frozen at `null` forever
    // otherwise, so every later `driverId`-scoped call this device makes
    // (e.g. Passenger Experience's `GET /v1/connections?driverId=`) would
    // keep failing with 403 for the rest of this session. Reuses
    // `persistAuthResponse`, the exact same helper `register`/`login`
    // already use, so the new token replaces the old one in storage the
    // same way theirs does.
    const response = await request<AuthResponse>(`/v1/identities/${current.identityId}/driver`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${current.token}` },
      body: JSON.stringify({ driverId }),
      baseUrl: IDENTITY_BASE_URL,
    })
    return this.persistAuthResponse(response)
  }

  /** See [IdentityProvider.restoreIdentity]'s own KDoc for the contract. */
  async restoreIdentity(): Promise<StoredIdentity | null> {
    const cached = this.getStoredIdentity()
    if (!cached) {
      return null
    }
    if (new Date(cached.expiresAt).getTime() <= Date.now()) {
      // Expired on this device's own clock -- no point spending a round
      // trip to learn what a local check already knows.
      this.clear()
      return null
    }
    try {
      const response = await request<IdentityApiResponse>('/v1/identities/me', {
        headers: { Authorization: `Bearer ${cached.token}` },
        baseUrl: IDENTITY_BASE_URL,
      })
      const identity: StoredIdentity = { ...cached, driverId: response.driverId }
      this.persist(identity)
      return identity
    } catch (error) {
      if (error instanceof ApiError && (error.status === 401 || error.status === 404)) {
        // This device's own session no longer resolves to anything the
        // backend accepts -- clear it so the welcome screen offers a fresh
        // sign-in instead of getting stuck pointing at nothing.
        this.clear()
        return null
      }
      // Any other failure (offline, backend briefly down): keep the
      // cached session and let the caller use it, rather than sign a
      // person out over a momentary network problem.
      return cached
    }
  }

  private persistAuthResponse(response: AuthResponse): StoredIdentity {
    const identity: StoredIdentity = {
      identityId: response.identityId,
      driverId: response.driverId,
      token: response.token,
      expiresAt: response.expiresAt,
    }
    this.persist(identity)
    return identity
  }

  private persist(identity: StoredIdentity): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(identity))
    } catch {
      // Storage may be unavailable (private browsing, quota exceeded). The
      // session still exists on the backend; only this device's own
      // pointer to it fails to survive a reload.
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
  return (
    typeof candidate.identityId === 'string' &&
    (candidate.driverId === null || typeof candidate.driverId === 'string') &&
    typeof candidate.token === 'string' &&
    typeof candidate.expiresAt === 'string'
  )
}
