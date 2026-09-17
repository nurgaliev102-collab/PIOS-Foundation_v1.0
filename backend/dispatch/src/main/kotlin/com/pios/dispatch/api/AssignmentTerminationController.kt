package com.pios.dispatch.api

import com.pios.dispatch.application.AssignmentRepository
import com.pios.dispatch.application.CommitmentTerminationApplicationService
import com.pios.dispatch.application.TerminateCommitmentCommand
import com.pios.dispatch.application.TerminationOutcome
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.TerminationInitiator
import com.pios.dispatch.domain.TerminationReasonCode
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class TerminateAssignmentRequest(
    val requestId: String,
    val reasonCode: String,
    val note: String? = null,
    val initiator: String? = null
)

data class TerminateAssignmentResponse(val requestId: String, val outcome: String)

@RestController
@RequestMapping("/v1/assignments")
class AssignmentTerminationController(
    private val assignments: AssignmentRepository,
    private val sessions: SessionTokenVerifier,
    private val termination: CommitmentTerminationApplicationService
) {
    @PostMapping("/{assignmentId}/terminate")
    fun terminate(
        @PathVariable assignmentId: String,
        @RequestBody request: TerminateAssignmentRequest,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<TerminateAssignmentResponse> {
        val verified = sessions.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (request.initiator != null) return ResponseEntity.badRequest().build()
        return try {
            UUID.fromString(request.requestId)
            val reason = TerminationReasonCode.valueOf(request.reasonCode)
            val assignment = assignments.findById(AssignmentId(assignmentId))
                ?: return ResponseEntity.notFound().build()
            if (verified.drv != assignment.driver.driverId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            val result = termination.terminate(
                TerminateCommitmentCommand(
                    requestId = request.requestId,
                    orderId = assignment.order.orderId,
                    initiator = TerminationInitiator.DRIVER,
                    reasonCode = reason,
                    note = request.note,
                    assignmentId = assignmentId
                )
            )
            val response = TerminateAssignmentResponse(result.requestId, result.outcome.name)
            if (result.outcome == TerminationOutcome.TERMINATED) ResponseEntity.ok(response)
            else ResponseEntity.status(HttpStatus.CONFLICT).body(response)
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }
}
