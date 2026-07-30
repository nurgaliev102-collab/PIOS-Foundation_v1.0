package com.pios.dispatch.domain

import java.time.Instant
import java.util.UUID

/**
 * The Assignment aggregate (DOMAIN_MODEL.md Section 4), within Dispatch's
 * exclusive ownership (ADR-002, ADR-005, ADR-019).
 *
 * This implementation covers assignment creation and acceptance
 * (DOMAIN_MODEL.md Section 12, "Assignment": "made by Dispatch in
 * connection with an order, then accepted"). Cancellation of an
 * assignment (DOMAIN_MODEL.md Section 6, "Cancellation") is a separate
 * capability and is explicitly out of this task's scope — [status]
 * therefore distinguishes only [AssignmentStatus.CREATED] from
 * [AssignmentStatus.ACCEPTED]. Every Assignment that exists, regardless
 * of status, is treated as active for the purposes of the invariant
 * below, since the invariant (an order cannot be connected to more than
 * one active assignment) is not stated to lapse upon acceptance.
 *
 * Invariant (DOMAIN_MODEL.md Section 11): an order cannot be connected to
 * more than one active assignment at a time. This invariant spans more
 * than one Assignment instance, and no repository exists yet (out of this
 * task's scope) to check against a persisted store; [create] enforces it
 * against a collection of currently known assignments supplied by the
 * caller instead.
 *
 * Invariant: only Dispatch may create or change an assignment
 * (DOMAIN_MODEL.md Section 11) — upheld structurally, since the private
 * constructor makes [create] the only means by which an Assignment comes
 * into existence, and both [create] and [accept] live exclusively within
 * the Dispatch module.
 */
class Assignment private constructor(
    val id: AssignmentId,
    val order: OrderReference,
    val driver: DriverReference
) {
    var status: AssignmentStatus = AssignmentStatus.CREATED
        private set

    /**
     * The time of this assignment's most recent status transition
     * (ADR-040, Assignment Ride Lifecycle) — `null` until the first one
     * happens, since a freshly-created assignment has not transitioned
     * yet. Every transition method below sets it; none of them read it.
     */
    var statusChangedAt: Instant? = null
        private set

    /**
     * Confirms this assignment, per the Accept Assignment command
     * (DOMAIN_MODEL.md Section 9). Only a [AssignmentStatus.CREATED]
     * assignment may be accepted; an already-accepted assignment cannot
     * be accepted again.
     *
     * [at] defaults to the current time for real callers; overridable so
     * [com.pios.dispatch.persistence.PostgreSQLAssignmentRepository] can
     * replay this transition during reconstruction with the originally
     * persisted timestamp instead of the moment of the read (see that
     * class's own KDoc). Every transition method below takes the same
     * parameter for the same reason.
     */
    fun accept(at: Instant = Instant.now()): AssignmentAccepted {
        check(status == AssignmentStatus.CREATED) {
            "Assignment ${id.value} cannot be accepted from status $status"
        }
        status = AssignmentStatus.ACCEPTED
        statusChangedAt = at
        return AssignmentAccepted(orderId = order, driverId = driver)
    }

    /**
     * Records that the driver has reached the passenger (ADR-040).
     * Accepts either [AssignmentStatus.CREATED] or [AssignmentStatus.ACCEPTED]
     * as its precondition — not [AssignmentStatus.ACCEPTED] alone. See
     * ADR-040's own "Decision" item 2 for why: the one real path that
     * creates an Assignment today (accepting a Proposal) never separately
     * calls [accept], so every Assignment reaching this point in practice
     * is [AssignmentStatus.CREATED].
     */
    fun arrive(at: Instant = Instant.now()): AssignmentArrived {
        check(status == AssignmentStatus.CREATED || status == AssignmentStatus.ACCEPTED) {
            "Assignment ${id.value} cannot be marked arrived from status $status"
        }
        status = AssignmentStatus.ARRIVED
        statusChangedAt = at
        return AssignmentArrived(orderId = order, driverId = driver)
    }

    /**
     * Records that the ride itself has begun (ADR-040). Only an
     * [AssignmentStatus.ARRIVED] assignment may start.
     */
    fun start(at: Instant = Instant.now()): AssignmentStarted {
        check(status == AssignmentStatus.ARRIVED) {
            "Assignment ${id.value} cannot be started from status $status"
        }
        status = AssignmentStatus.IN_PROGRESS
        statusChangedAt = at
        return AssignmentStarted(orderId = order, driverId = driver)
    }

    /**
     * Records that the ride has finished (ADR-040). Only an
     * [AssignmentStatus.IN_PROGRESS] assignment may complete. Does not
     * touch the connected Order's own status (ADR-040's own "Decision"
     * item 5) — Dispatch does not own that information.
     */
    fun complete(at: Instant = Instant.now()): AssignmentCompleted {
        check(status == AssignmentStatus.IN_PROGRESS) {
            "Assignment ${id.value} cannot be completed from status $status"
        }
        status = AssignmentStatus.COMPLETED
        statusChangedAt = at
        return AssignmentCompleted(orderId = order, driverId = driver)
    }

    companion object {
        /**
         * Creates a new assignment connecting [order] to [driver], per the
         * Assign Order command (DOMAIN_MODEL.md Section 9). Rejects the
         * creation if [existingAssignments] already contains an assignment
         * for the same [order].
         *
         * The specific criteria for selecting [driver] are explicitly not
         * an architectural or domain concern (ADR-002); this factory only
         * records a decision already made elsewhere, given to it as
         * [driver].
         */
        fun create(
            order: OrderReference,
            driver: DriverReference,
            existingAssignments: Collection<Assignment> = emptyList()
        ): AssignmentCreated {
            check(existingAssignments.none { it.order == order }) {
                "Order ${order.orderId} already has an active assignment"
            }
            val assignment = Assignment(
                id = AssignmentId(UUID.randomUUID().toString()),
                order = order,
                driver = driver
            )
            val event = OrderAssigned(orderId = order, driverId = driver)
            return AssignmentCreated(assignment = assignment, event = event)
        }
    }
}

/**
 * The result of creating an assignment: the new [Assignment] and the
 * [OrderAssigned] event recording its creation.
 */
data class AssignmentCreated(val assignment: Assignment, val event: OrderAssigned)
