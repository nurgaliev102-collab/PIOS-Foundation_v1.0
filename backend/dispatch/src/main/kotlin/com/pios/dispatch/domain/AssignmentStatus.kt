package com.pios.dispatch.domain

/**
 * The lifecycle status of an Assignment (DOMAIN_MODEL.md Section 12,
 * "Assignment"). An assignment is made by Dispatch in connection with an
 * order, then accepted; it stands until its order is completed or
 * cancelled. Only the states exercised by this task's scope are modeled:
 * cancellation of an assignment (DOMAIN_MODEL.md Section 6,
 * "Cancellation") is a separate capability and is not represented here.
 */
enum class AssignmentStatus {
    CREATED,
    ACCEPTED
}
