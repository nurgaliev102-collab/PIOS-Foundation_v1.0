package com.pios.passengerexperience.verification

import com.pios.passengerexperience.application.OrderSubmissionCoordinator
import com.pios.passengerexperience.application.OrderSubmissionRequestedPublisher
import com.pios.passengerexperience.application.PassengerOrderSubmissionApplicationService
import com.pios.passengerexperience.application.SubmitOrderCommand
import com.pios.passengerexperience.domain.PassengerReference
import com.pios.passengerexperience.persistence.OrderManagementTestServer
import com.pios.passengerexperience.persistence.RestClientOrderSubmissionClient
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertFailsWith

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
 *
 * Order Provenance / Authentication Remediation (P0,
 * `docs/PIOS_DATA_FLOW_CODE_AUDIT.md` Section 5): this whole chain sends no
 * session token -- [RestClientOrderSubmissionClientTest]'s own KDoc
 * explains why that is correct (this Tranche 2 REST path was already
 * superseded as the live product's own order-submission caller). What this
 * scenario now verifies end-to-end is that the same 401 rejection reaches
 * this application layer's own collaborators intact, as a real
 * [HttpClientErrorException.Unauthorized] thrown from
 * [RestClientOrderSubmissionClient.submit], not a silently-created order.
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
    fun `a passenger's submit order request reaches Order Management over real HTTP and is rejected with 401, since this path sends no session token`() {
        assertFailsWith<HttpClientErrorException.Unauthorized> {
            coordinator.submitOrder(SubmitOrderCommand(PassengerReference("passenger-e2e-1")))
        }
    }

    @Test
    fun `repeated attempts are each independently rejected -- not a one-time or fluky failure`() {
        assertFailsWith<HttpClientErrorException.Unauthorized> {
            coordinator.submitOrder(SubmitOrderCommand(PassengerReference("passenger-e2e-2")))
        }
        assertFailsWith<HttpClientErrorException.Unauthorized> {
            coordinator.submitOrder(SubmitOrderCommand(PassengerReference("passenger-e2e-3")))
        }
    }
}
