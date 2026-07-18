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
 * Meaning") and contains no persistence, since that remains a separate,
 * later concern (PERSISTENCE_ARCHITECTURE.md), out of scope for this
 * capability. [existingAssignments] and the [Assignment] passed to
 * [acceptAssignment] are supplied by the caller, since no repository
 * exists yet to look them up.
 */
@Service
class DispatchAssignmentApplicationService {

    fun handle(
        command: AssignOrderCommand,
        existingAssignments: Collection<Assignment> = emptyList()
    ): AssignmentCreated {
        return Assignment.create(
            order = command.order,
            driver = command.driver,
            existingAssignments = existingAssignments
        )
    }

    /**
     * Confirms the given [assignment], per the Accept Assignment command.
     * [assignment] must be the one referenced by [command] — the caller
     * is responsible for finding it, since no repository exists yet for
     * this service to look it up itself.
     */
    fun acceptAssignment(
        assignment: Assignment,
        command: AcceptAssignmentCommand
    ): AssignmentAccepted {
        require(assignment.id == command.assignmentId) {
            "Assignment ${assignment.id.value} does not match command target ${command.assignmentId.value}"
        }
        return assignment.accept()
    }
}
