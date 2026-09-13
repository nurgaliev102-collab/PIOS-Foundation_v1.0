package com.pios.dispatch.api

/**
 * The REST request body for `POST /v1/proposals/:id/messages` (Minimal
 * In-Ride Messaging MVP). [body] carries only the message text —
 * [com.pios.dispatch.domain.MessageSenderRole] is never a caller-supplied
 * field; `ProposalMessageController.sendMessage` derives it from which
 * identity the caller's own Bearer token names, exactly as no Proposal
 * action anywhere in this module lets a caller assert someone else's role.
 */
data class SendProposalMessageRequest(val body: String)
