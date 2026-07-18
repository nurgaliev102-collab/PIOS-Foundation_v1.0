package com.pios.ordermanagement.verification

import com.pios.dispatch.application.AcceptAssignmentCommand
import com.pios.dispatch.application.AssignOrderCommand
import com.pios.dispatch.application.DispatchAssignmentApplicationService
import com.pios.dispatch.application.DriverAvailabilityNotificationHandler
import com.pios.dispatch.application.OrderAssignedPublisher
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.drivermanagement.application.DeclareAvailabilityCommand
import com.pios.drivermanagement.application.DriverAvailabilityApplicationService
import com.pios.drivermanagement.application.DriverAvailabilityChangedPublisher
import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import com.pios.ordermanagement.application.CompleteOrderCommand
import com.pios.ordermanagement.application.OrderAssignmentRecognitionHandler
import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.application.OrderSubmissionRequestHandler
import com.pios.ordermanagement.application.SubmitOrderCommand
import com.pios.ordermanagement.domain.OrderStatus
import com.pios.passengerexperience.application.OrderSubmissionRequestedPublisher
import com.pios.passengerexperience.application.PassengerOrderSubmissionApplicationService
import com.pios.passengerexperience.application.SubmitOrderCommand as PassengerSubmitOrderCommand
import com.pios.passengerexperience.domain.PassengerReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end verification of the first MVP vertical slice
 * (INITIAL_IMPLEMENTATION_PLAN.md Section 6): a passenger submits an
 * order, a driver declares availability, Dispatch assigns and the driver
 * accepts, and Order Management completes the order. This is a
 * verification scenario only (ADR-027) — it creates no production
 * orchestration, and no class it exercises is changed by it.
 *
 * Hosted in Order Management because this module participates in the
 * most steps of the six (Create Order, Complete Order) and already holds
 * the consumer side of two of the three ADR-027 contracts exercised here.
 * Test-scoped dependencies on passenger-experience, driver-management,
 * and dispatch are declared in build.gradle.kts solely to support this
 * test (documented there); no production code in any of the four modules
 * gains a dependency on another as a result.
 *
 * Cross-module boundary discipline observed by this test:
 * - Every value that actually crosses from one module's output into
 *   another module's input does so only through the already-established
 *   ADR-027 primitive contract payloads and handlers (Flows 1-3;
 *   MVP Integration Contract Boundary v1.0) — never through a foreign
 *   module's domain class.
 * - Where a step is a module's own autonomous origination (Passenger
 *   Experience submitting, Driver Management declaring availability,
 *   Dispatch deciding to assign and accept), that module's own small,
 *   immutable domain value types (PassengerReference, DriverId,
 *   Availability, OrderReference, DriverReference) are constructed here
 *   solely to drive that module's own public application service, and
 *   the result is immediately translated to a primitive via that
 *   module's own publisher before being used for anything else. No
 *   module's aggregate (Order, Driver, Assignment) is ever constructed
 *   from outside its own module.
 * - The one exception is Dispatch's Assignment, which this test holds a
 *   reference to only to pass it back into Dispatch's own
 *   acceptAssignment call in the very next step — mirroring the "caller
 *   supplies the instance, since no repository exists" pattern already
 *   established for every module's own multi-step lifecycle (e.g. Order
 *   Management's own completeOrder). Its fields are never read here.
 * - Because ADR-027's contract handlers deliberately return only
 *   primitives, never the aggregate they created, the Order this test
 *   completes in Step 6 is a separate instance from the one the Step
 *   1->2 contract crossing creates internally, obtained through Order
 *   Management's own submitOrder — the same underlying mechanism
 *   OrderSubmissionRequestHandler itself uses. No repository exists (out
 *   of every prior task's established scope) to look up the
 *   contract-created order by the id it returns.
 */
class MvpVerticalSliceScenarioTest {

    @Test
    fun `a passenger's order is submitted, assigned, accepted, and completed across all four MVP modules`() {
        // Step 1 (Passenger Experience: Submit Order). Expected: OrderSubmissionRequested.
        val passengerService = PassengerOrderSubmissionApplicationService()
        val orderSubmissionRequestedPublisher = OrderSubmissionRequestedPublisher()
        val orderSubmissionRequested =
            passengerService.handle(PassengerSubmitOrderCommand(PassengerReference("passenger-1")))
        val step1Payload = orderSubmissionRequestedPublisher.toContractPayload(orderSubmissionRequested)
        assertEquals("passenger-1", step1Payload.passengerReference)

        // Step 2 (Order Management: Create Order), triggered through the
        // Flow 1 contract crossing. Expected: OrderSubmitted (fired
        // internally by Order.submit()).
        val orderSubmissionRequestHandler = OrderSubmissionRequestHandler(OrderLifecycleApplicationService())
        val contractCreatedOrderId = orderSubmissionRequestHandler.handle(step1Payload.passengerReference)
        assertTrue(contractCreatedOrderId.isNotBlank())

        // The order this scenario carries through Steps 4-6 (see class
        // KDoc for why it is a separate instance from the one just above).
        val orderLifecycleApplicationService = OrderLifecycleApplicationService()
        val submittedOrder = orderLifecycleApplicationService.submitOrder(SubmitOrderCommand())
        val orderReference = submittedOrder.order.id.value
        assertEquals(OrderStatus.SUBMITTED, submittedOrder.order.status)

        // Step 3 (Driver Management: Declare availability). Expected: DriverAvailabilityChanged.
        val driverAvailabilityService = DriverAvailabilityApplicationService()
        val driverAvailabilityChangedPublisher = DriverAvailabilityChangedPublisher()
        val driver = Driver(DriverId("driver-1"))
        val driverAvailabilityChanged = driverAvailabilityService.handle(
            driver,
            DeclareAvailabilityCommand(driver.id, Availability.AVAILABLE)
        )!!
        val step3Payload = driverAvailabilityChangedPublisher.toContractPayload(driverAvailabilityChanged)
        assertEquals("driver-1", step3Payload.driverReference)
        assertTrue(step3Payload.available)

        // Flow 2 contract crossing (Driver Management -> Dispatch),
        // proving Dispatch receives the availability information
        // (INTERFACE_CONTRACTS.md Section 5) ahead of assignment.
        val driverAvailabilityNotificationHandler = DriverAvailabilityNotificationHandler()
        val acknowledgedDriverReference =
            driverAvailabilityNotificationHandler.handle(step3Payload.driverReference, step3Payload.available)
        assertEquals("driver-1", acknowledgedDriverReference)

        // Step 4 (Dispatch: Create Assignment). Expected: OrderAssigned.
        // Uses the order and driver references established above as raw
        // identifiers only — no Order Management or Driver Management
        // object is passed into Dispatch.
        val dispatchAssignmentApplicationService = DispatchAssignmentApplicationService()
        val orderAssignedPublisher = OrderAssignedPublisher()
        val assignmentCreated = dispatchAssignmentApplicationService.handle(
            AssignOrderCommand(OrderReference(orderReference), DriverReference(step3Payload.driverReference))
        )
        val step4Payload = orderAssignedPublisher.toContractPayload(assignmentCreated.event)
        assertEquals(orderReference, step4Payload.orderReference)
        assertEquals("driver-1", step4Payload.driverReference)

        // Flow 3 contract crossing (Dispatch -> Order Management),
        // proving Order Management recognizes the assignment fact
        // (INTERFACE_CONTRACTS.md Section 5).
        val orderAssignmentRecognitionHandler = OrderAssignmentRecognitionHandler()
        val recognizedOrderReference =
            orderAssignmentRecognitionHandler.handle(step4Payload.orderReference, step4Payload.driverReference)
        assertEquals(orderReference, recognizedOrderReference)

        // Step 5 (Dispatch: Accept Assignment). Expected: AssignmentAccepted.
        val assignmentAccepted = dispatchAssignmentApplicationService.acceptAssignment(
            assignmentCreated.assignment,
            AcceptAssignmentCommand(assignmentCreated.assignment.id)
        )
        assertEquals(OrderReference(orderReference), assignmentAccepted.orderId)
        assertEquals(DriverReference("driver-1"), assignmentAccepted.driverId)

        // Step 6 (Order Management: Complete Order). Expected: OrderCompleted.
        val orderCompleted = orderLifecycleApplicationService.completeOrder(
            submittedOrder.order,
            CompleteOrderCommand(submittedOrder.order.id)
        )
        assertEquals(submittedOrder.order.id, orderCompleted.orderId)
        assertEquals(OrderStatus.COMPLETED, submittedOrder.order.status)
    }
}
