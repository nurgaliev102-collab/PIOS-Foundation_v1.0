/**
 * ADR-038/ADR-039/ADR-055: the seam that answers "who is this device, and
 * which driver profile does it manage." [BackendIdentityProvider] calls the
 * real `identity` module, which now issues a genuine, verifiable session
 * (ADR-055) rather than handing out a credential-less record to whoever
 * asks. The same provider serves both roles this app has — a passenger
 * never calls [attachDriver]; a driver does, once, during onboarding.
 */
export interface StoredIdentity {
  identityId: string
  driverId: string | null
  /** ADR-055: `Authorization: Bearer <token>` for every protected call this device makes. */
  token: string
  /** ISO-8601 — [IdentityProvider] itself never sends an expired token; callers still hit 401 if the backend disagrees. */
  expiresAt: string
}

export interface IdentityProvider {
  /** Reads this device's own remembered identity — no network call. */
  getStoredIdentity(): StoredIdentity | null
  /** Creates a new account (ADR-055: phone + password) and remembers the session on this device. */
  register(phone: string, password: string): Promise<StoredIdentity>
  /** Signs in to an existing account and remembers the session on this device. */
  login(phone: string, password: string): Promise<StoredIdentity>
  /** Forgets this device's own session — the account itself is untouched. */
  logout(): void
  /** Associates a just-created driver profile with this device's identity. */
  attachDriver(driverId: string): Promise<StoredIdentity>
  /**
   * Sprint 2 (Identity MVP), extended by ADR-055: re-enters using this
   * device's stored session — confirms it against the backend (`GET
   * /v1/identities/me`) rather than trusting the local cache indefinitely,
   * so "повторный вход" reflects this account's real, current state.
   * Returns `null` if no session is stored, the token has expired, or the
   * backend no longer accepts it (revoked account, server reset) — any of
   * these should fall back to the login/registration screen. A transient
   * failure (network down, backend unreachable) does not clear the local
   * session; it resolves with the cached value instead, so a person is not
   * signed out just because connectivity blipped.
   */
  restoreIdentity(): Promise<StoredIdentity | null>
}
