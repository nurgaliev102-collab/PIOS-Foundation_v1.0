package com.pios.core.application

import com.pios.core.domain.ParticipantHistoryEvent
import com.pios.core.domain.ParticipantReference
import org.springframework.stereotype.Service

/**
 * The read side of Core's Slice 01 projection — the one query
 * `com.pios.core.api.ParticipantHistoryController` serves. Reads only
 * `pios_core`, through [ParticipantHistoryRepository]; never calls or
 * reads a Taxi module (ADR-067 Write Boundary / API).
 */
@Service
class ParticipantHistoryQueryService(
    private val participantHistoryRepository: ParticipantHistoryRepository
) {
    /**
     * The ordered history of [reference] — every recorded fact, oldest
     * first. An unknown participant returns an empty list (not an error):
     * Core simply has no history for them yet.
     */
    fun historyOf(reference: ParticipantReference): List<ParticipantHistoryEvent> =
        participantHistoryRepository.history(reference)
}
