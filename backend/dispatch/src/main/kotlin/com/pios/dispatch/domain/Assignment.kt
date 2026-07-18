package com.pios.dispatch.domain

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
     * Confirms this assignment, per the Accept Assignment command
     * (DOMAIN_MODEL.md Section 9). Only a [AssignmentStatus.CREATED]
     * assignment may be accepted; an already-accepted assignment cannot
     * be accepted again.
     */
    fun accept(): AssignmentAccepted {
        check(status == AssignmentStatus.CREATED) {
            "Assignment ${id.value} cannot be accepted from status $status"
        }
        status = AssignmentStatus.ACCEPTED
        return AssignmentAccepted(orderId = order, driverId = driver)
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
