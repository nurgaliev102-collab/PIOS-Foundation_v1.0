package com.pios.networkmanagement.domain

/**
 * An Invitation's lifecycle state (Sprint 7A: PIOS Network Foundation).
 * `EXPIRED` is named here because the founder's own specification names
 * it, but nothing in Sprint 7A ever transitions an Invitation to it — no
 * time-to-live, expiry job, or expiry check is implemented this sprint.
 * This is a disclosed limitation, not a silent gap: an Invitation created
 * today remains `CREATED` (usable) indefinitely until accepted.
 */
enum class InvitationStatus {
    CREATED,
    USED,
    EXPIRED
}
