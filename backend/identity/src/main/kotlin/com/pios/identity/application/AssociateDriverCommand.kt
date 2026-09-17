package com.pios.identity.application

/**
 * [driverId] is a plain string, not validated against
 * `driver-management` in any way (ADR-039: the reference is one-directional
 * and `identity` never calls `driver-management` to confirm it exists —
 * the frontend, which just created that driver, supplies it here trusted).
 *
 * [presentedGeneration] (ADR-082, D-03.3) is the caller's token's own
 * `sgen` claim — checked against the loaded identity's live
 * `sessionGeneration` before anything else, so a token minted before a
 * successful recovery cannot associate a driver afterward. Defaults to
 * `0`, matching every identity that has never gone through recovery (the
 * only value a token for such an identity could ever legitimately carry).
 */
data class AssociateDriverCommand(val identityId: String, val driverId: String, val presentedGeneration: Int = 0)
