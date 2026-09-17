package com.pios.dispatch.domain

import java.time.Instant

enum class TerminationInitiator { PASSENGER, DRIVER, SYSTEM }

enum class TerminationReasonCode {
    PLANS_CHANGED, FOUND_ANOTHER_DRIVER, DRIVER_UNRESPONSIVE, CANNOT_CONTINUE,
    CANNOT_FULFILL, PASSENGER_UNRESPONSIVE, PASSENGER_NO_SHOW,
    TRIP_CONDITIONS_CHANGED, OTHER, SYSTEM_TIMEOUT
}

data class Termination(
    val requestId: String,
    val initiator: TerminationInitiator,
    val reasonCode: TerminationReasonCode,
    val terminatedAt: Instant,
    val note: String? = null
) {
    init {
        require(requestId.isNotBlank()) { "Termination requestId is required" }
        require(note == null || note.length <= 2000) { "Termination note is too long" }
        require(
            when (initiator) {
                TerminationInitiator.PASSENGER -> reasonCode in PASSENGER_REASONS
                TerminationInitiator.DRIVER -> reasonCode in DRIVER_REASONS
                TerminationInitiator.SYSTEM -> reasonCode == TerminationReasonCode.SYSTEM_TIMEOUT
            }
        ) { "Reason $reasonCode is not valid for $initiator" }
    }

    companion object {
        val PASSENGER_REASONS: Set<TerminationReasonCode> = setOf(
            TerminationReasonCode.PLANS_CHANGED,
            TerminationReasonCode.FOUND_ANOTHER_DRIVER,
            TerminationReasonCode.DRIVER_UNRESPONSIVE,
            TerminationReasonCode.CANNOT_CONTINUE,
            TerminationReasonCode.OTHER
        )
        val DRIVER_REASONS: Set<TerminationReasonCode> = setOf(
            TerminationReasonCode.CANNOT_FULFILL,
            TerminationReasonCode.PASSENGER_UNRESPONSIVE,
            TerminationReasonCode.PASSENGER_NO_SHOW,
            TerminationReasonCode.TRIP_CONDITIONS_CHANGED,
            TerminationReasonCode.OTHER
        )
    }
}
