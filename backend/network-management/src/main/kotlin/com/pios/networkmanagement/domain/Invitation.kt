package com.pios.networkmanagement.domain

import java.time.Instant

/**
 * An invitation created by a [Person] (Sprint 7A: PIOS Network
 * Foundation), sharable as [code] (for example, embedded in a link), that
 * produces a [Connection] once accepted
 * ([com.pios.networkmanagement.application.AcceptInvitationApplicationService]).
 *
 * Mutable state is limited to [status], following the same "single
 * current state, replaced never appended" invariant
 * [com.pios.drivermanagement.domain.Driver.availability] already
 * establishes for this codebase.
 */
class Invitation(
    val id: InvitationId,
    val creatorPersonId: PersonId,
    val code: String,
    status: InvitationStatus = InvitationStatus.CREATED,
    val createdAt: Instant
) {
    init {
        require(code.isNotBlank()) { "Invitation code must not be blank" }
    }

    var status: InvitationStatus = status
        private set

    /**
     * Marks this invitation used. Throws [IllegalStateException] if it is
     * not currently [InvitationStatus.CREATED] — an already-used or
     * expired invitation cannot be used again.
     */
    fun use() {
        check(status == InvitationStatus.CREATED) {
            "Invitation $code is not usable (status=$status)"
        }
        status = InvitationStatus.USED
    }
}
