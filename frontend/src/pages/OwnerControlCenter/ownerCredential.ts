/**
 * Owner Control Center — credential storage (ADR-044: Owner Authentication).
 *
 * There is no session token and no server-side session state (ADR-044
 * Decision 3, Decision 4): the login screen's only proof that a credential
 * is correct is a `200` from `GET /v1/health`. What this file stores is the
 * credential itself — held in `sessionStorage`, which the browser already
 * clears when the tab closes, satisfying ADR-044's "for the life of the
 * tab" requirement with no code of this file's own needed to enforce it.
 *
 * Logout ([clearOwnerCredential]) is a purely client-side action: nothing
 * is revoked on the server, because nothing was issued (ADR-044 Decision
 * 7) — withdrawal happens by changing `pios.owner.password-hash` on every
 * module and restarting, an operator action outside this file's scope.
 */

const STORAGE_KEY = 'pios.owner.credential'

export interface OwnerCredential {
  username: string
  password: string
}

/** The `sessionStorage`-persisted credential, or `null` if none is held. */
export function getStoredOwnerCredential(): OwnerCredential | null {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return null
  }
  try {
    const parsed = JSON.parse(raw) as Partial<OwnerCredential>
    if (typeof parsed.username === 'string' && typeof parsed.password === 'string') {
      return { username: parsed.username, password: parsed.password }
    }
    return null
  } catch {
    return null
  }
}

/** Persists [credential] in `sessionStorage`, overwriting any previous one. */
export function storeOwnerCredential(credential: OwnerCredential): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(credential))
}

/** Discards the held credential — the entire mechanics of "logout" here. */
export function clearOwnerCredential(): void {
  sessionStorage.removeItem(STORAGE_KEY)
}

/**
 * Builds the `Authorization: Basic ...` header value ADR-044 Decision 3
 * specifies. `btoa` is sufficient here (not a full UTF-8-safe encoder):
 * the username is deployment-configured and documented as colon-free
 * (ADR-044 Decision 3's own constraint), and an owner-chosen password is
 * expected to be ASCII in practice for this pilot-scale mechanism: if it
 * is not, the failure is a visible "wrong password" on login, not a
 * silent corruption, since the same encoding is used consistently on
 * every request.
 */
export function toBasicAuthorizationHeader(credential: OwnerCredential): string {
  return `Basic ${btoa(`${credential.username}:${credential.password}`)}`
}
