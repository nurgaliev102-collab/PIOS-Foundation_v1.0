package com.pios.dispatch.contract

import com.pios.dispatch.application.AssignOrderCommand
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.application.OrderAssignedPublisher
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.persistence.InMemoryAssignmentRepository
import com.pios.ordermanagement.application.OrderAssignmentRecognitionHandler
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Contract verification for Dispatch -> Order Management
 * (INTERFACE_CONTRACTS.md Section 5; ADR-027). Proves that this module's
 * provider representation of OrderAssigned (`OrderAssignedPublisher`) and
 * Order Management's consumer handler
 * (`OrderAssignmentRecognitionHandler`) agree on the same primitive
 * contract shape.
 *
 * This test alone justifies dispatch's test-scoped dependency on
 * order-management (see build.gradle.kts); no production code in either
 * module depends on the other (ADR-027).
 */
class OrderAssignmentContractVerificationTest {

    private val dispatchService = DispatchAssignmentApplicationService(InMemoryAssignmentRepository())
    private val publisher = OrderAssignedPublisher()
    private val consumerHandler = OrderAssignmentRecognitionHandler()

    @Test
    fun `dispatch's provider payload is accepted by Order Management's consumer handler`() {
        val created = dispatchService.handle(AssignOrderCommand(OrderReference("order-1"), DriverReference("driver-1")))

        val payload = publisher.toContractPayload(created.event)
        val result = consumerHandler.handle(payload.orderReference, payload.driverReference)

        assertEquals("order-1", result)
    }

    @Test
    fun `the contract payload carries the assigned order and driver references`() {
        val created = dispatchService.handle(AssignOrderCommand(OrderReference("order-2"), DriverReference("driver-2")))

        val payload = publisher.toContractPayload(created.event)

        assertEquals("order-2", payload.orderReference)
        assertEquals("driver-2", payload.driverReference)
    }
}
