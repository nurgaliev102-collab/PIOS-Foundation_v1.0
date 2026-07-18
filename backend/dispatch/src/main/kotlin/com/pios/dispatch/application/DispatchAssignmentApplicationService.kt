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
 * [existingAssignments] is still supplied by the caller, since
 * [AssignmentRepository] offers lookup by id but no query capability
 * beyond that. [acceptAssignment] is overloaded: one form still operates
 * on an [Assignment] instance supplied by the caller, the other restores
 * the [Assignment] from [assignmentRepository] by id first, proving the
 * aggregate can be saved, loaded back, and continue its own domain
 * operation exactly as it would if it had never left memory.
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

    /**
     * Confirms the assignment referenced by [command] by first restoring
     * it through [assignmentRepository]. Throws
     * [AssignmentNotFoundException] — an application-layer error, never a
     * persistence or domain one (see that class's own KDoc) — if no
     * Assignment identified by [AcceptAssignmentCommand.assignmentId] has
     * been saved.
     */
    fun acceptAssignment(command: AcceptAssignmentCommand): AssignmentAccepted {
        val assignment = assignmentRepository.findById(command.assignmentId)
            ?: throw AssignmentNotFoundException(command.assignmentId)
        return acceptAssignment(assignment, command)
    }
}
