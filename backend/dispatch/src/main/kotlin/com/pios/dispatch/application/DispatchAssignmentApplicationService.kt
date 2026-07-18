package com.pios.dispatch.application

import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentAccepted
import com.pios.dispatch.domain.AssignmentCreated
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for the Assign Order and Accept
 * Assignment commands (APPLICATION_ARCHITECTURE.md Section 6). This
 * service sequences each command into the Assignment aggregate's own
 * behavior; it does not decide the assignment or its acceptance itself
 * (APPLICATION_ARCHITECTURE.md Section 2, "Domain Decides Business
 * Meaning"). After each transition, the service persists the affected
 * [Assignment] through [assignmentRepository]
 * (PERSISTENCE_ARCHITECTURE.md Section 3, "Dispatch") — the only point in
 * this module where persistence is invoked; the [Assignment] aggregate
 * itself remains entirely unaware that a repository exists.
 * [existingAssignments] and the [Assignment] passed to [acceptAssignment]
 * are still supplied by the caller, since [AssignmentRepository] offers
 * lookup by id but no query capability beyond that.
 */
@Service
class DispatchAssignmentApplicationService(
    private val assignmentRepository: AssignmentRepository
) {

    fun handle(
        command: AssignOrderCommand,
        existingAssignments: Collection<Assignment> = emptyList()
    ): AssignmentCreated {
        val created = Assignment.create(
            order = command.order,
            driver = command.driver,
            existingAssignments = existingAssignments
        )
        assignmentRepository.save(created.assignment)
        return created
    }

    /**
     * Confirms the given [assignment], per the Accept Assignment command.
     * [assignment] must be the one referenced by [command] — the caller
     * is responsible for finding it, since [AssignmentRepository] offers
     * lookup by id but no query capability beyond that.
     */
    fun acceptAssignment(
        assignment: Assignment,
        command: AcceptAssignmentCommand
    ): AssignmentAccepted {
        require(assignment.id == command.assignmentId) {
            "Assignment ${assignment.id.value} does not match command target ${command.assignmentId.value}"
        }
        val event = assignment.accept()
        assignmentRepository.save(assignment)
        return event
    }
}
