package com.pios.dispatch.application

import com.pios.dispatch.domain.AssignmentId

/**
 * The Accept Assignment command (DOMAIN_MODEL.md Section 9;
 * MODULE_STRUCTURE.md, "Dispatch Module", Owned capabilities). Represents
 * a driver's confirmation that a proposed assignment will proceed. It
 * does not itself decide whether that confirmation is valid — that
 * remains the Assignment aggregate's own decision
 * (APPLICATION_ARCHITECTURE.md Section 2, "Domain Decides Business
 * Meaning"). [assignmentId] identifies which assignment the command
 * applies to; the assignment instance itself is supplied separately by
 * the caller, since no repository exists yet for Dispatch to look it up.
 */
data class AcceptAssignmentCommand(
    val assignmentId: AssignmentId
)
