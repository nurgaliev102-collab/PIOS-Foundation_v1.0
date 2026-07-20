package com.pios.passengerexperience.application

import org.springframework.stereotype.Service

/**
 * The real caller composing Passenger Experience's Submit Order workflow
 * end-to-end (Tranche 2: Passenger Experience REST Transport): raises the
 * [com.pios.passengerexperience.domain.OrderSubmissionRequested]
 * representation through [PassengerOrderSubmissionApplicationService],
 * translates it to the already-fixed ADR-027 contract payload through
 * [OrderSubmissionRequestedPublisher], and submits it to Order Management
 * through [OrderSubmissionClient] (ADR-028's REST transport category).
 * Introduces no new business logic -- every step it sequences already
 * existed; this class only wires them together with a real transport
 * call, closing the gap
 * IMPLEMENTATION_PLAN_TRANCHE_2_PASSENGER_REST_TRANSPORT.md identified.
 *
 * [client] has no default -- mirroring [com.pios.ordermanagement.application.EventPublisher]'s
 * own precedent on `OutboxRelay` (Hardening Review v1.0): a Kotlin
 * default here would let a production context missing a real
 * [OrderSubmissionClient] bean silently fall back to it, claiming an
 * order was submitted when it was not. Requiring the argument means a
 * production context missing a real [OrderSubmissionClient] bean fails to
 * start instead.
 */
@Service
class OrderSubmissionCoordinator(
    private val applicationService: PassengerOrderSubmissionApplicationService,
    private val publisher: OrderSubmissionRequestedPublisher,
    private val client: OrderSubmissionClient
) {
    fun submitOrder(command: SubmitOrderCommand): String {
        val requested = applicationService.handle(command)
        val payload = publisher.toContractPayload(requested)
        return client.submit(payload.passengerReference)
    }
}
