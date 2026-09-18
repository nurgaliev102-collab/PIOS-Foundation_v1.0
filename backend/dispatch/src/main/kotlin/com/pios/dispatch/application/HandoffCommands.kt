package com.pios.dispatch.application

import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.HandoffId

/**
 * The five D-07 Handoff commands, grouped in one file since each is a
 * small, single-purpose data holder — mirroring the shape (not the
 * per-file split) of [AssignOrderCommand]/[AcceptProposalCommand]'s own
 * neighbors. Actor identity is deliberately **not** a field on any of
 * these: every operation's own caller (`HandoffController`) derives and
 * verifies the acting identity from the request's own verified session
 * token *before* constructing and passing the command, the same
 * controller-authorizes / service-trusts-the-caller split already
 * established by [AssignmentTerminationController.terminate] and
 * `AssignmentController.arrive/start/complete` — see
 * `HandoffApplicationService`'s own KDoc for why authorization does not
 * belong on these commands.
 */
data class ProposeHandoffCommand(
    val assignmentId: AssignmentId,
    val substituteDriverId: String
)

data class AcceptHandoffSubstituteCommand(val handoffId: HandoffId)

data class ConsentHandoffCommand(val handoffId: HandoffId)

data class RefuseHandoffCommand(val handoffId: HandoffId)

data class WithdrawHandoffCommand(val handoffId: HandoffId)
