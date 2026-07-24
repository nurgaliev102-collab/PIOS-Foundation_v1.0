package com.pios.dispatch.api

/**
 * The REST response body for Assign Order (Sprint FR-004, Manual
 * Assignment). Mirrors the new [com.pios.dispatch.domain.Assignment]'s
 * own identity and status exactly — nothing else: this sprint's own
 * functional scenario only needs the coordinator to see that creation
 * succeeded and what the new assignment's id is, not a full projection
 * of the aggregate.
 */
data class AssignOrderResponse(val assignmentId: String, val status: String)
