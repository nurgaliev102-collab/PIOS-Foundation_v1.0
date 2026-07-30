/**
 * ADR-038/ADR-039: the seam that answers "who is this device, and which
 * driver profile does it manage." [BackendIdentityProvider] is the swap
 * ADR-038 itself anticipated ("swapping local storage for a real, verified
 * backend session later touches only the implementation behind this
 * interface") — it now calls the real `identity` module rather than
 * fabricating an id purely client-side, though it still stores nothing on
 * the server beyond an id and an optional, unverified phone (no
 * authentication exists yet; ADR-039's own explicit scope boundary).
 */
export interface StoredIdentity {
  identityId: string
  driverId: string | null
}

export interface IdentityProvider {
  /** Reads this device's own remembered identity — no network call. */
  getStoredIdentity(): StoredIdentity | null
  /** Creates a real Identity on the backend and remembers it on this device. */
  createIdentity(): Promise<StoredIdentity>
  /** Associates a just-created driver profile with this device's identity. */
  attachDriver(driverId: string): Promise<StoredIdentity>
  /**
   * Sprint 2 (Identity MVP): re-enters using this device's stored pointer —
   * re-fetches the Identity from the backend rather than trusting
   * [getStoredIdentity]'s local cache indefinitely, so "повторный вход"
   * reflects this Identity's real, current state (in particular its
   * `driverId`) rather than whatever this device happened to cache last.
   * Returns `null` if no pointer is stored, or if the backend no longer
   * recognizes the stored identity (it was reset server-side) — either way
   * the caller should fall back to first-run onboarding. A transient
   * failure (network down, backend unreachable) does not clear the local
   * pointer; it resolves with the cached value instead, so a person is not
   * forced back into onboarding just because connectivity blipped.
   */
  restoreIdentity(): Promise<StoredIdentity | null>
}
