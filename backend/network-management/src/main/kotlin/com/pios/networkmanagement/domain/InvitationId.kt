package com.pios.networkmanagement.domain

/**
 * Identity of an Invitation (Sprint 7A: PIOS Network Foundation), generated
 * on creation, never caller-supplied — same reasoning as [PersonId]. Not
 * to be confused with [Invitation.code], the short, shareable value a
 * person actually sends (for example, in a link); [InvitationId] never
 * appears in a URL.
 */
@JvmInline
value class InvitationId(val value: String) {
    init {
        require(value.isNotBlank()) { "InvitationId must not be blank" }
    }
}
