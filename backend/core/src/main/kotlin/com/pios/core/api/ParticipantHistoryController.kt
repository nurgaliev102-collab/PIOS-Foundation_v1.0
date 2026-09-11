package com.pios.core.api

import com.pios.core.application.ParticipantHistoryQueryService
import com.pios.core.domain.ParticipantReference
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

/**
 * The one read endpoint ADR-067 Slice 01 exposes:
 * `GET /v1/core/participants/{participantReference}/history`.
 *
 * Reads only Core's own `pios_core` projection, through
 * [ParticipantHistoryQueryService] (ADR-067 API: "must read only
 * pios_core", "must not obtain to any other services"). Never calls a
 * Taxi module.
 *
 * Authorization (per `PIOS_CORE_SLICE_01_CONSTRAINTS.md`): a `Bearer`
 * session token whose `sub` equals `{participantReference}`, **or** the
 * owner `Basic` credential. Anything else is `401` with no
 * `WWW-Authenticate` header (same fail-closed posture as
 * [HealthController]). No frontend is wired to this endpoint (ADR-067
 * Explicit Non-Decisions: "no screen, no route, no apiClient change");
 * it exists for later consumption only.
 */
@RestController
class ParticipantHistoryController(
    private val participantHistoryQueryService: ParticipantHistoryQueryService,
    private val sessionTokenVerifier: SessionTokenVerifier,
    private val ownerCredentialGate: OwnerCredentialGate
) {

    @GetMapping("/v1/core/participants/{participantReference}/history")
    fun history(
        @PathVariable participantReference: String,
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<ParticipantHistoryResponse> {
        if (!isAuthorized(participantReference, authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val reference = ParticipantReference(participantReference)
        val events = participantHistoryQueryService.historyOf(reference).map {
            ParticipantHistoryEventResponse(
                kind = it.kind.name,
                occurredAt = it.occurredAt.toString(),
                orderReference = it.orderReference,
                driverReference = it.driverReference
            )
        }
        return ResponseEntity.ok(
            ParticipantHistoryResponse(participantReference = participantReference, events = events)
        )
    }

    private fun isAuthorized(participantReference: String, authorization: String?): Boolean {
        val verified = sessionTokenVerifier.verify(authorization)
        if (verified != null && verified.sub == participantReference) {
            return true
        }
        return ownerCredentialGate.verify(authorization)
    }
}
