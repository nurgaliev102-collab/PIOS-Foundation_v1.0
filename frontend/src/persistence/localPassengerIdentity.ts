/**
 * Sprint 3 — First Real Identity: local persistence abstraction.
 *
 * This is the only file in the frontend that touches `localStorage`.
 * Components call `getPassengerIdentity`/`savePassengerIdentity`, never
 * `localStorage` directly, so a future task can replace local-only
 * identity with a real backend-backed one (session/auth) by changing
 * only this file's implementation — no page or component would need to
 * change, since they only depend on the `PassengerIdentity` shape and
 * these two function signatures.
 *
 * Scope: one browser holds at most one local passenger identity today —
 * there is no concept of switching or logging out. No server, database,
 * or authentication is involved; this is storage on the passenger's own
 * device only.
 */

const STORAGE_KEY = 'pios.passenger-identity'

export interface PassengerIdentity {
  id: string
  name: string
  createdAt: string
}

export function getPassengerIdentity(): PassengerIdentity | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return null
    }
    const parsed: unknown = JSON.parse(raw)
    return isPassengerIdentity(parsed) ? parsed : null
  } catch {
    return null
  }
}

export function savePassengerIdentity(name: string): PassengerIdentity {
  const identity: PassengerIdentity = {
    id: generateId(),
    name,
    createdAt: new Date().toISOString(),
  }
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(identity))
  } catch {
    // Storage may be unavailable (private browsing, quota exceeded).
    // The identity is still returned for the current session; it simply
    // will not survive a reload.
  }
  return identity
}

function generateId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `local-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

function isPassengerIdentity(value: unknown): value is PassengerIdentity {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.id === 'string' &&
    typeof candidate.name === 'string' &&
    typeof candidate.createdAt === 'string'
  )
}
