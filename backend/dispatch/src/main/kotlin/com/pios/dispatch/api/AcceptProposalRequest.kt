package com.pios.dispatch.api

/**
 * The REST request body for Accept Proposal (ADR-042, Stated Ride Price
 * Minimal Model — Decision Revised R2/R5). `POST /v1/proposals/{id}/accept`
 * previously took no request body at all; this type is entirely optional
 * on the wire, mirroring [ProposeDriverRequest]'s own shape but with every
 * field defaulted so the existing bodyless caller
 * (`frontend/src/pages/DriverHome/DriverHome.tsx`'s decline path, and any
 * caller that predates this field) keeps working unmodified — a missing
 * body, a present body with [statedPrice] absent, and a present body with
 * [statedPrice] `null` are all valid and all mean "no amount stated"
 * (ADR-026; [com.pios.ordermanagement.api.SubmitOrderRequest]'s own KDoc
 * records this exact class of caller-breaking mistake happening once
 * already).
 *
 * [ProposalController.acceptProposal] converts a blank (but present)
 * [statedPrice] to HTTP 400, the same way it already converts a blank
 * `orderId`/`driverId` — see [AcceptProposalCommand]'s own validation.
 */
data class AcceptProposalRequest(val statedPrice: String? = null, val statedEtaMinutes: Int? = null)
