package com.pios.ordermanagement.application

/**
 * The primitive-typed command this module's transport boundary
 * (`com.pios.ordermanagement.persistence.AssignmentCompletedListener`)
 * translates a received AssignmentCompleted message into, before calling
 * [AssignmentCompletedApplicationService]. Carries only [eventId] (for
 * idempotent processing) and [orderReference] -- exactly what ADR-041's
 * trigger needs to resolve and complete the referenced Order locally
 * (`OrderRepository.findById`), never a foreign key and never a
 * server-to-server call (ADR-027). [driverReference] is deliberately not
 * carried: nothing in ADR-041's Decision uses it, and `OrderCompleted`'s
 * payload stays v1 `{orderId}` (ADR-041 Q4). Mirrors
 * [AssignmentAcceptedUpdateCommand] exactly, minus the field this flow
 * does not need.
 */
data class AssignmentCompletedUpdateCommand(
    val eventId: String,
    val orderReference: String
)
