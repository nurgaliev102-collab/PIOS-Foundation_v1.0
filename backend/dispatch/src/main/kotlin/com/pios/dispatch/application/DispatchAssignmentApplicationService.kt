package com.pios.dispatch.application

import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentCreated
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for the Assign Order command
 * (APPLICATION_ARCHITECTURE.md Section 6). This service sequences the
 * command into the Assignment aggregate's own creation behavior; it does
 * not decide the assignment itself (APPLICATION_ARCHITECTURE.md Section 2,
 * "Domain Decides Business Meaning") and contains no persistence, since
 * that remains a separate, later concern (PERSISTENCE_ARCHITECTURE.md),
 * out of scope for this capability. [existingAssignments] is supplied by
 * the caller, since no repository exists yet to look it up.
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
}
