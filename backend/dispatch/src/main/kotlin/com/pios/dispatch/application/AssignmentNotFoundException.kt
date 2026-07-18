package com.pios.dispatch.application

import com.pios.dispatch.domain.AssignmentId

/**
 * Thrown when an application-layer operation needs the Assignment
 * identified by [assignmentId], but [AssignmentRepository.findById] could
 * not locate one (PERSISTENCE_ARCHITECTURE.md Section 3, "Dispatch").
 *
 * This is an application-layer error, not a domain error: the Assignment
 * aggregate itself has no notion of "not found" — that only exists at
 * the point something tries to look one up, which is an application
 * concern (APPLICATION_ARCHITECTURE.md Section 2). It is also not a
 * persistence error: the repository itself never throws for a miss
 * ([com.pios.dispatch.persistence.InMemoryAssignmentRepository.findById]
 * simply returns null); this exception is raised exactly once, here, at
 * the point the application decides it cannot proceed without the
 * aggregate.
 */
class AssignmentNotFoundException(val assignmentId: AssignmentId) :
    RuntimeException("Assignment ${assignmentId.value} was not found")
