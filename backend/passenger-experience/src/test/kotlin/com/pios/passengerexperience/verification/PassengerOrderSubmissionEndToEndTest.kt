package com.pios.passengerexperience.verification

import com.pios.passengerexperience.application.OrderSubmissionCoordinator
import com.pios.passengerexperience.application.OrderSubmissionRequestedPublisher
import com.pios.passengerexperience.application.PassengerOrderSubmissionApplicationService
import com.pios.passengerexperience.application.SubmitOrderCommand
import com.pios.passengerexperience.domain.PassengerReference
import com.pios.passengerexperience.persistence.OrderManagementTestServer
import com.pios.passengerexperience.persistence.RestClientOrderSubmissionClient
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * End-to-end verification that Passenger Experience's own application
 * layer can reach a real, running Order Management instance over a real
 * HTTP call (Tranche 2: Passenger Experience REST Transport, Part 8 step
 * 6; Part 10 Acceptance Criteria). Exercises every collaborator this
 * tranche adds together -- [PassengerOrderSubmissionApplicationService],
 * [OrderSubmissionRequestedPublisher], [OrderSubmissionCoordinator],
 * [RestClientOrderSubmissionClient] -- against
 * [OrderManagementTestServer]'s real, unmodified
 * `OrderSubmissionRequestHandler`/`OrderLifecycleApplicationService`.
 *
 * This is a verification scenario only (ADR-027's own precedent,
 * `MvpVerticalSliceScenarioTest`): it creates no production
 * orchestration, and no class it exercises is changed by it.
 */
class PassengerOrderSubmissionEndToEndTest {

    private val client = RestClientOrderSubmissionClient(
        RestClient.builder().baseUrl(OrderManagementTestServer.baseUrl).build()
    )
    private val coordinator = OrderSubmissionCoordinator(
        PassengerOrderSubmissionApplicationService(),
        OrderSubmissionRequestedPublisher(),
        client
    )

    @Test
    fun `a passenger's submit order request reaches Order Management over real HTTP and a real order is created`() {
        val orderId = coordinator.submitOrder(SubmitOrderCommand(PassengerReference("passenger-e2e-1")))

        assertTrue(orderId.isNotBlank())
    }

    @Test
    fun `distinct submissions create distinct orders, proving a real order is created each time`() {
        val first = coordinator.submitOrder(SubmitOrderCommand(PassengerReference("passenger-e2e-2")))
        val second = coordinator.submitOrder(SubmitOrderCommand(PassengerReference("passenger-e2e-3")))

        assertNotEquals(first, second)
    }
}
