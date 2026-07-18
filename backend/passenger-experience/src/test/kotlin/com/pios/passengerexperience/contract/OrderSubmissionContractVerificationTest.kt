package com.pios.passengerexperience.contract

import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.application.OrderSubmissionRequestHandler
import com.pios.passengerexperience.application.OrderSubmissionRequestedPublisher
import com.pios.passengerexperience.application.PassengerOrderSubmissionApplicationService
import com.pios.passengerexperience.application.SubmitOrderCommand
import com.pios.passengerexperience.domain.PassengerReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract verification for Passenger Experience -> Order Management
 * (INTERFACE_CONTRACTS.md Section 5; ADR-027). Proves that this module's
 * provider representation of OrderSubmissionRequested
 * (`OrderSubmissionRequestedPublisher`) and Order Management's consumer
 * handler (`OrderSubmissionRequestHandler`) agree on the same primitive
 * contract shape.
 *
 * This test alone justifies passenger-experience's test-scoped dependency
 * on order-management (see build.gradle.kts); no production code in
 * either module depends on the other (ADR-027).
 */
class OrderSubmissionContractVerificationTest {

    private val passengerService = PassengerOrderSubmissionApplicationService()
    private val publisher = OrderSubmissionRequestedPublisher()
    private val consumerHandler = OrderSubmissionRequestHandler(OrderLifecycleApplicationService())

    @Test
    fun `passenger experience's provider payload is accepted by Order Management's consumer handler`() {
        val requested = passengerService.handle(SubmitOrderCommand(PassengerReference("passenger-1")))
        val payload = publisher.toContractPayload(requested)

        val submittedOrderId = consumerHandler.handle(payload.passengerReference)

        assertTrue(submittedOrderId.isNotBlank())
    }

    @Test
    fun `the contract payload carries exactly the passenger reference submitted`() {
        val requested = passengerService.handle(SubmitOrderCommand(PassengerReference("passenger-42")))

        val payload = publisher.toContractPayload(requested)

        assertEquals("passenger-42", payload.passengerReference)
    }
}
