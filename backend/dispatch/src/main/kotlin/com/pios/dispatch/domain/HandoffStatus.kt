package com.pios.dispatch.domain

/**
 * The lifecycle status of a Handoff (D-07, Handoff Protocol; PO decision
 * lock, `docs/PIOS_D07_HANDOFF_IMPLEMENTATION_SPEC.md` §5). Distinct
 * from, and layered on top of, [TripStatus]/[AssignmentStatus] -- a
 * Handoff never re-enters or duplicates either.
 *
 * [PROPOSED] — the original committing driver has named a substitute.
 * Nothing about the ride itself has changed yet.
 *
 * [SUBSTITUTE_ACCEPTED] — the named substitute has explicitly accepted
 * this specific Handoff (locked precondition; a passenger is never asked
 * to consent to a substitute who has not themselves agreed). Still
 * nothing about the ride itself has changed.
 *
 * [COMMITTED] — the passenger has positively, constitutively consented
 * to the exact named, already-substitute-accepted Handoff. This is the
 * one and only transition that changes [Trip.executingDriver].
 *
 * [REFUSED] — the passenger declined. The existing Assignment/Trip is
 * completely unaffected.
 *
 * [WITHDRAWN] — the original driver retracted the Handoff. Reachable
 * only from [PROPOSED] or [SUBSTITUTE_ACCEPTED], never from [COMMITTED]
 * — once constitutive consent is recorded, withdrawal is unconditionally
 * forbidden (locked decision; not merely a race-ordering outcome).
 *
 * No `EXPIRED` state exists, by design, not by omission: D-07 locks "no
 * artificial Handoff TTL" — a pending Handoff resolves only through one
 * of the four states above, or by the underlying Trip leaving the
 * `CREATED`/`ARRIVED` window it is confined to (enforced structurally by
 * [Trip.assignExecutingDriver]'s own precondition, not by a fifth state
 * here).
 */
enum class HandoffStatus {
    PROPOSED,
    SUBSTITUTE_ACCEPTED,
    COMMITTED,
    REFUSED,
    WITHDRAWN
}
