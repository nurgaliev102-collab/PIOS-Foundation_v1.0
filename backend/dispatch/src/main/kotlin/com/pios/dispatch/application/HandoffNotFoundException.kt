package com.pios.dispatch.application

import com.pios.dispatch.domain.HandoffId

/**
 * Thrown when an application-layer operation needs the Handoff identified
 * by [handoffId], but [HandoffRepository.findById] could not locate one.
 * Mirrors [AssignmentNotFoundException]/[ProposalNotFoundException]
 * exactly — an application-layer error, not a domain or persistence one.
 */
class HandoffNotFoundException(val handoffId: HandoffId) :
    RuntimeException("Handoff ${handoffId.value} was not found")
