package com.pios.identity.domain

import java.time.Instant

/**
 * The Identity aggregate (ADR-038) — an authentication anchor for one human,
 * independent of any role (Driver, Passenger) and independent of
 * `network-management`'s own [com.pios.networkmanagement.domain.Person]
 * (a *relationship* identity, not an authentication one — see ADR-038's
 * self-critique section for why the two are kept separate rather than
 * merged).
 *
 * [phone] is optional and, today, unverified — creating an Identity does
 * not prove anyone controls the number supplied. Verifying it is exactly
 * the capability this ADR does *not* authorize; [phone] exists here only so
 * the shape an eventual verification flow would update already exists,
 * rather than being bolted on later as a schema migration against live
 * data.
 *
 * [driverId] (ADR-039) is this Identity's first real cross-module
 * reference: a plain string pointing at a `driver-management`-owned
 * `DriverId`, never a foreign key and never ownership — the same
 * "reference, not ownership" rule `ADR-037` already established for
 * `network-management`. `driver-management` itself has no column, table,
 * or API knowledge of `Identity` at all; the reference is legible only
 * from this side.
 *
 * No [Credential], [Device], or [Session] reference lives on this class:
 * those remain unpersisted domain shapes (see the sibling files in this
 * package) until a future ADR actually wires one of them to a real
 * application service.
 *
 * [phoneVerifiedAt] and [sessionGeneration] (ADR-082, D-03) are Phone-Verified
 * Identity Recovery's own additions. [phoneVerifiedAt] is `null` for every
 * identity by default — including every row that existed before ADR-082 —
 * and only ever transitions `null` -> non-null, once, via [withPhoneVerified];
 * its presence, not a separate boolean, *is* the "verified" fact (the same
 * single-source-of-truth-via-timestamp convention `ADR-054`'s
 * `designated_at` already established for this codebase). [sessionGeneration]
 * defaults to `0` and is bumped by exactly one, via
 * [withSessionGenerationBumped], only on a successful recovery
 * (`ConfirmRecoveryApplicationService`) — see that class's own KDoc, and
 * ADR-082 Part 3/§8, for the disclosed scope of what bumping it actually
 * invalidates.
 */
class Identity(
    val id: IdentityId,
    val phone: Phone?,
    val driverId: String?,
    val createdAt: Instant,
    val phoneVerifiedAt: Instant? = null,
    val sessionGeneration: Int = 0
) {
    /**
     * Returns a copy with [driverId] set (ADR-039) — an Identity's driver
     * reference is set once, at driver-profile creation, and this method
     * exists so [com.pios.identity.application.AssociateDriverApplicationService]
     * does not need to reach into this class's private state to change it.
     */
    fun withDriverId(driverId: String): Identity = Identity(id, phone, driverId, createdAt, phoneVerifiedAt, sessionGeneration)

    fun withPhone(phone: Phone): Identity = Identity(id, phone, driverId, createdAt, phoneVerifiedAt, sessionGeneration)

    /**
     * ADR-082 Part 2/D-03.2 — marks [phone] as proven, never inferred.
     * Callers (`RequestPhoneVerificationApplicationService`/legacy enrolment
     * confirm) are responsible for only calling this after a real OTP
     * match; this method itself enforces only the one-way-transition
     * invariant, mirroring [com.pios.identity.application.UpgradeGuestIdentityApplicationService]'s
     * own `check()`-before-mutate style rather than re-deriving the rule here.
     */
    fun withPhoneVerified(verifiedAt: Instant): Identity {
        check(phoneVerifiedAt == null) { "phone is already verified" }
        return Identity(id, phone, driverId, createdAt, verifiedAt, sessionGeneration)
    }

    /**
     * ADR-082 Part 3/D-03.3 — called only by a successful
     * `ConfirmRecoveryApplicationService.handle()`, once, in the same
     * transaction as the credential replacement. Monotonic: always exactly
     * `sessionGeneration + 1`, never reset, never settable to an arbitrary
     * value.
     */
    fun withSessionGenerationBumped(): Identity = Identity(id, phone, driverId, createdAt, phoneVerifiedAt, sessionGeneration + 1)
}
