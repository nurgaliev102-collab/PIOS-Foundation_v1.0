package com.pios.dispatch.domain

/**
 * The lifecycle status of a Trip (ADR-063: Trip as a New Dispatch-Owned
 * Aggregate, Distinct from Assignment). A Trip is created the moment
 * [Assignment.create] connects a driver to an order — via the
 * authoritative `OrderAssigned` trigger, corrected in Task 11A from the
 * originally-specified `AssignmentAccepted` after call-graph verification
 * showed that event is never published by any live production path (see
 * `ADR-063`'s own Status section for the full history) — and therefore
 * starts in [CREATED], **not** [ARRIVED]: creation does not mean the
 * ride has started, only that a driver now exists for this order.
 *
 * [ARRIVED], [IN_PROGRESS], and [COMPLETED] carry forward, unrenamed,
 * the same three ride-progress facts `AssignmentStatus` already models
 * today (ADR-040) — this is a relocation of meaning onto a new,
 * Dispatch-internal aggregate, not a redefinition of what any of the
 * three states means. Task 11's own "Backward Compatibility" scope keeps
 * `AssignmentStatus`'s own identical three states, and `Assignment`'s
 * own `arrive()`/`start()`/`complete()` methods, entirely unchanged and
 * unremoved alongside this — the two lifecycles coexist deliberately,
 * per that task's own explicit instruction, until a later task performs
 * the full migration `ADR-063`'s own target design describes.
 */
enum class TripStatus {
    CREATED,
    ARRIVED,
    IN_PROGRESS,
    COMPLETED
}
