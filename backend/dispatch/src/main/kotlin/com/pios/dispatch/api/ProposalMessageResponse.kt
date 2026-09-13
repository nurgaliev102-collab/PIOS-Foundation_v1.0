package com.pios.dispatch.api

/**
 * The REST response body for every Minimal In-Ride Messaging endpoint —
 * mirrors [com.pios.dispatch.domain.ProposalMessage]'s own identity,
 * sender, body, and send time. Never carries [proposalId]: a caller
 * already knows which proposal's own thread it requested (the path
 * variable), and this shape is used identically for a single freshly-sent
 * message and for a full `GET` list, neither of which needs to repeat it.
 */
data class ProposalMessageResponse(
    val id: String,
    val senderRole: String,
    val body: String,
    val sentAt: String
)
