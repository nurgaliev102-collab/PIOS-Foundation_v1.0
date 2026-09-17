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
  /** A short-lived, device-local passenger session created without registration. */
  guest?: boolean
  /**
   * ADR-082 (D-03) — whether this account's phone is verified, i.e.
   * recovery-eligible. Deliberately carried on [StoredIdentity] itself
   * rather than fetched separately: [restoreIdentity] already makes the
   * one `GET /v1/identities/me` round trip this needs, once per app
   * mount, and [confirmPhoneVerification] already knows the answer from
   * its own response — a second, dedicated fetch would duplicate that
   * call for no new information. `undefined` on a token minted before
   * this field existed (register/login predating a `restoreIdentity`
   * round trip); a caller must treat that the same as `false` (unknown ⇒
   * do not claim verified), never the reverse.
   */
  phoneVerified?: boolean
}

export interface IdentityProvider {
  /** Reads this device's own remembered identity — no network call. */
  getStoredIdentity(): StoredIdentity | null
  /** Creates a new account (ADR-055: phone + password) and remembers the session on this device. */
  register(phone: string, password: string): Promise<StoredIdentity>
  /** Signs in to an existing account and remembers the session on this device. */
  login(phone: string, password: string): Promise<StoredIdentity>
  /** Starts the passenger journey without collecting credentials. */
  createGuest(): Promise<StoredIdentity>
  /** Converts the current guest in place, preserving its orders and relationships. */
  upgradeGuest(phone: string, password: string): Promise<StoredIdentity>
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

  /**
   * ADR-082 (D-03) — starts phone-verified recovery for a registered
   * account. Always resolves (never distinguishes whether the phone is
   * known, registered, or verified — D-03.7's own generic-response
   * requirement, carried through to this layer so no caller can
   * accidentally build an enumeration-capable UI on top of it).
   */
  requestRecovery(phone: string): Promise<void>

  /**
   * ADR-082 — completes recovery: a correct [code] replaces the account's
   * password with [newPassword] and returns a fresh session, already
   * persisted on this device exactly like [register]/[login]. Throws
   * [com.pios.identity.api.ApiError]-shaped errors from `apiClient` on
   * failure — 401 for any wrong/expired/unknown reason (never
   * distinguished), 400 for a malformed new password.
   */
  confirmRecovery(phone: string, code: string, newPassword: string): Promise<StoredIdentity>

  /**
   * ADR-082 Part 2 (D-03.2) — the legacy-enrolment request step: proves
   * the phone already on this account's own record. Requires an existing
   * session (registered, non-guest) — there is no anonymous variant,
   * unlike [requestRecovery].
   */
  requestPhoneVerification(): Promise<void>

  /**
   * ADR-082 Part 2 — completes legacy enrolment: a correct [code] sets
   * this account's phone as verified and returns the updated
   * [StoredIdentity] (same session token — this action does not mint a
   * new one, see [confirmPhoneVerification]'s implementation KDoc).
   */
  confirmPhoneVerification(code: string): Promise<StoredIdentity>
}
