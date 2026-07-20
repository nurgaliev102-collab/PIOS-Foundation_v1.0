package com.pios.passengerexperience.persistence

import com.pios.passengerexperience.application.OrderSubmissionClient
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * The REST-backed adapter for [OrderSubmissionClient] (ADR-004, ADR-028;
 * Tranche 2: Passenger Experience REST Transport). Submits
 * [passengerReference] to Order Management's Submit Order endpoint and
 * returns the created order's id.
 *
 * A connection failure or timeout surfaces as
 * [org.springframework.web.client.ResourceAccessException]; a non-2xx
 * response (for example, Order Management's own HTTP 400 for a blank
 * passenger reference) surfaces as
 * [org.springframework.web.client.RestClientResponseException] --
 * [RestClient]'s own default behavior, left unmodified here so neither
 * failure is ever silently converted into a success.
 */
@Component
class RestClientOrderSubmissionClient(
    private val orderManagementRestClient: RestClient
) : OrderSubmissionClient {

    override fun submit(passengerReference: String): String {
        val response = orderManagementRestClient.post()
            .uri("/v1/orders")
            .body(SubmitOrderRequestBody(passengerReference))
            .retrieve()
            .body(SubmitOrderResponseBody::class.java)
            ?: error("Order Management returned no response body for Submit Order")
        return response.orderId
    }
}

private data class SubmitOrderRequestBody(val passengerReference: String)
private data class SubmitOrderResponseBody(val orderId: String)
