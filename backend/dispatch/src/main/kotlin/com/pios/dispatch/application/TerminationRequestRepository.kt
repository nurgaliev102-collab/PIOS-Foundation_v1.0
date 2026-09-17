package com.pios.dispatch.application

import com.pios.dispatch.domain.TerminationInitiator
import com.pios.dispatch.domain.TerminationReasonCode

enum class TerminationOutcome {
    TERMINATED, NO_COMMITMENT, REASON_REQUIRED, ALREADY_COMPLETED, ALREADY_TERMINATED
}

data class TerminationRequestRecord(
    val requestId: String,
    val orderId: String,
    val assignmentId: String?,
    val initiator: TerminationInitiator,
    val reasonCode: TerminationReasonCode?,
    val note: String?,
    val outcome: TerminationOutcome
)

interface TerminationRequestRepository {
    fun findById(requestId: String): TerminationRequestRecord?
    fun save(record: TerminationRequestRecord)
}
