package com.pios.dispatch.api

/**
 * The REST request body for Propose Price (Product Owner instruction,
 * 2026-09-05: a driver must state a price before a ride is confirmed).
 * Unlike [AcceptProposalRequest], [statedPrice] here is mandatory --
 * a missing or blank value is a malformed request (HTTP 400), not "no
 * amount stated": stating one is the entire point of this endpoint.
 */
data class ProposePriceRequest(val statedPrice: String, val statedEtaMinutes: Int? = null)
