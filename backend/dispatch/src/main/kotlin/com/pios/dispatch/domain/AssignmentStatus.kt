package com.pios.dispatch.domain

/**
 * The lifecycle status of an Assignment (DOMAIN_MODEL.md Section 12,
 * "Assignment"). An assignment is made by Dispatch in connection with an
 * order, then accepted; it stands until its order is completed or
 * cancelled. Cancellation of an assignment (DOMAIN_MODEL.md Section 6,
 * "Cancellation") is a separate capability and is not represented here.
 *
 * ADR-040 (Assignment Ride Lifecycle) adds [ARRIVED], [IN_PROGRESS], and
 * [COMPLETED] — the ride-progress states DOMAIN_MODEL.md Section 12
 * already names for Order ("carried through acceptance, the ride itself,
 * and completion"), placed here rather than on `Order` per that ADR's own
 * evidence: `Order.kt`'s KDoc already, deliberately, does not model them.
 * See [Assignment.arrive]'s own KDoc for why it accepts [CREATED] as a
 * precondition too, not just [ACCEPTED].
 */
enum class AssignmentStatus {
    CREATED,
    ACCEPTED,
    ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    TERMINATED
}
