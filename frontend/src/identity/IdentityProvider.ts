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
}
