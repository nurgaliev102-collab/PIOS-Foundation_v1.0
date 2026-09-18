package com.pios.dispatch.api

/**
 * The REST response body for every Handoff endpoint (D-07, Handoff
 * Protocol). Mirrors [AssignmentResponse]/[ProposalResponse]'s own
 * shape: identity, references, status, timestamps as ISO-8601
 * (`Instant.toString()`), the same explicit-`.toString()` convention
 * this codebase already uses throughout rather than depending on
 * Jackson's automatic `Instant` (de)serialization.
 */
data class HandoffResponse(
    val handoffId: String,
    val assignmentId: String,
    val orderId: String,
    val originalDriverId: String,
    val substituteDriverId: String,
    val status: String,
    val proposedAt: String,
    val substituteAcceptedAt: String?,
    val resolvedAt: String?
)
