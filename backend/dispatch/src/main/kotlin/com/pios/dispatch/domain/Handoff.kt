package com.pios.dispatch.domain

import java.time.Instant
import java.util.UUID

/**
 * The Handoff aggregate (D-07, Handoff Protocol —
 * `docs/PIOS_D07_HANDOFF_IMPLEMENTATION_SPEC.md`). Within Dispatch's
 * exclusive ownership, mirroring [Proposal]/[Trip]'s own module-isolation
 * conventions exactly (ADR-002, ADR-005, ADR-019).
 *
 * Records the transfer of *execution* of an already-existing commitment
 * to a different, explicitly named and explicitly consenting driver —
 * never a new commitment (DESIGN 1, locked): no second [Assignment], no
 * second [Trip] is ever created by anything in this class or its own
 * application service. [assignmentId]/[order] are a same-module
 * denormalization of the one [Assignment] this Handoff always refers to,
 * mirroring [Trip.assignmentId]'s own convention.
 *
 * [originalDriver] is fixed at [propose] time and never changes for the
 * life of this Handoff record — it answers "who initiated this specific
 * Handoff," always the Assignment's own committing driver (D-07
 * invariant: PIOS never selects the substitute, and chaining is
 * impossible because a substitute is never the Assignment's own
 * `driver`). [substituteDriver] is likewise fixed at [propose] time —
 * the specific, named driver the original driver chose; never PIOS.
 *
 * [passenger] is a same-module-only reference (denormalized from the
 * Assignment's own `ACCEPTED` Proposal's `passengerReference` by the
 * caller, at [propose] time) — this class never reads a [Proposal]
 * itself.
 */
class Handoff private constructor(
    val id: HandoffId,
    val assignmentId: AssignmentId,
    val order: OrderReference,
    val originalDriver: DriverReference,
    val substituteDriver: DriverReference,
    val passenger: PassengerReference?,
    val proposedAt: Instant,
    val isTest: Boolean = false
) {
    var status: HandoffStatus = HandoffStatus.PROPOSED
        private set

    /** `null` until [acceptSubstitute] is called. */
    var substituteAcceptedAt: Instant? = null
        private set

    /** `null` until this Handoff reaches any of its four resolutions. */
    var resolvedAt: Instant? = null
        private set

    /**
     * The named substitute explicitly accepts this specific Handoff
     * (D-07 locked precondition — the passenger is never asked to
     * consent before this). Only a [HandoffStatus.PROPOSED] Handoff may
     * transition here.
     */
    fun acceptSubstitute(at: Instant = Instant.now()): HandoffSubstituteAccepted {
        check(status == HandoffStatus.PROPOSED) {
            "Handoff ${id.value} cannot accept a substitute from status $status"
        }
        status = HandoffStatus.SUBSTITUTE_ACCEPTED
        substituteAcceptedAt = at
        return HandoffSubstituteAccepted(assignmentId = assignmentId, orderId = order, driverId = substituteDriver, occurredAt = at)
    }

    /**
     * The passenger's own constitutive consent — the one and only
     * transition that makes the substitute the executing driver (applied
     * by the caller to the connected [Trip] via
     * [Trip.assignExecutingDriver], in the same transaction, since this
     * class has no reference to [Trip] and must not acquire one — D-07's
     * own module/aggregate-boundary reasoning). Only a
     * [HandoffStatus.SUBSTITUTE_ACCEPTED] Handoff may be committed —
     * never directly from [HandoffStatus.PROPOSED] (D-07 invariant: a
     * consent request cannot be constitutive of anything if the named
     * substitute has not themselves accepted first).
     */
    fun commit(at: Instant = Instant.now()): HandoffCommitted {
        check(status == HandoffStatus.SUBSTITUTE_ACCEPTED) {
            "Handoff ${id.value} cannot be committed from status $status"
        }
        status = HandoffStatus.COMMITTED
        resolvedAt = at
        return HandoffCommitted(assignmentId = assignmentId, orderId = order, originalDriverId = originalDriver, executingDriverId = substituteDriver, occurredAt = at)
    }

    /**
     * The passenger declines. Per D-07's own locked invariant, this has
     * no effect whatsoever on the underlying Assignment/Trip — the
     * caller must not, and this class provides no means to, cancel or
     * terminate anything as a side effect of this call.
     */
    fun refuse(at: Instant = Instant.now()): HandoffRefused {
        check(status == HandoffStatus.PROPOSED || status == HandoffStatus.SUBSTITUTE_ACCEPTED) {
            "Handoff ${id.value} cannot be refused from status $status"
        }
        status = HandoffStatus.REFUSED
        resolvedAt = at
        return HandoffRefused(assignmentId = assignmentId, orderId = order, occurredAt = at)
    }

    /**
     * The original driver retracts this Handoff. Only reachable from
     * [HandoffStatus.PROPOSED] or [HandoffStatus.SUBSTITUTE_ACCEPTED] —
     * **never** from [HandoffStatus.COMMITTED] (D-07's own unconditional
     * "original driver cannot simply withdraw after passenger consent"
     * invariant; this `check` is the exact place that rule is enforced,
     * regardless of concurrent timing — see this module's own
     * `HandoffApplicationService.withdraw` KDoc for the concurrency
     * consequence).
     */
    fun withdraw(at: Instant = Instant.now()): HandoffWithdrawn {
        check(status == HandoffStatus.PROPOSED || status == HandoffStatus.SUBSTITUTE_ACCEPTED) {
            "Handoff ${id.value} cannot be withdrawn from status $status"
        }
        status = HandoffStatus.WITHDRAWN
        resolvedAt = at
        return HandoffWithdrawn(assignmentId = assignmentId, orderId = order, occurredAt = at)
    }

    companion object {
        /**
         * Proposes a new Handoff for [assignmentId]. Rejects the
         * creation if [existingActiveHandoff] is non-null (a non-terminal
         * — [HandoffStatus.PROPOSED]/[HandoffStatus.SUBSTITUTE_ACCEPTED]
         * — Handoff already exists for this Assignment) — the caller is
         * responsible for supplying it, mirroring [Trip.create]'s own
         * `existingTrip` check exactly; `handoffs_one_active_per_assignment`'s
         * own partial `UNIQUE` index (D-07 migration) is the persistent
         * backstop this in-memory check alone cannot be.
         *
         * Rejects [substituteDriver] equal to [originalDriver] outright —
         * a degenerate, meaningless Handoff, not a real transfer of
         * execution.
         */
        fun propose(
            assignmentId: AssignmentId,
            order: OrderReference,
            originalDriver: DriverReference,
            substituteDriver: DriverReference,
            passenger: PassengerReference?,
            existingActiveHandoff: Handoff?,
            isTest: Boolean = false,
            at: Instant = Instant.now()
        ): HandoffCreated {
            check(existingActiveHandoff == null) {
                "Assignment ${assignmentId.value} already has an active Handoff"
            }
            require(substituteDriver != originalDriver) {
                "The substitute driver must differ from the original driver"
            }
            val handoff = Handoff(
                id = HandoffId(UUID.randomUUID().toString()),
                assignmentId = assignmentId,
                order = order,
                originalDriver = originalDriver,
                substituteDriver = substituteDriver,
                passenger = passenger,
                proposedAt = at,
                isTest = isTest
            )
            return HandoffCreated(handoff)
        }
    }
}

/**
 * The result of proposing a Handoff: the new [Handoff]. Mirrors
 * [TripCreated]'s own shape — no domain event beyond the aggregate
 * itself, since nothing outside this module, and nothing else inside it,
 * needs to react to "a Handoff was proposed" today (D-07 spec §9: no
 * server-side consumer is required initially).
 */
data class HandoffCreated(val handoff: Handoff)
