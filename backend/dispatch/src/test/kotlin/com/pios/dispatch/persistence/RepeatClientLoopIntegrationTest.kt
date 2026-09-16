package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.DispatchRequestApplicationService
import com.pios.dispatch.application.DriverAvailabilityRecord
import com.pios.dispatch.application.FallbackDispatchApplicationService
import com.pios.dispatch.application.FirstRefusalApplicationService
import com.pios.dispatch.application.PrimaryDriverProjectionApplicationService
import com.pios.dispatch.application.PrimaryDriverRepository
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.PassengerReference
import com.pios.dispatch.domain.ProposalStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Repeat Client Loop (Product Cycle): proves the full chain end to end --
 * Connection -> Primary -> First Refusal -> new order -- against the real,
 * locally-running RabbitMQ broker and the real, isolated
 * `pios_dispatch_test` PostgreSQL database, not mocks, and not a seeded
 * shortcut.
 *
 * [PrimaryConnectionConsumerIntegrationTest] already proves "Connection ->
 * Primary" (a real `PrimaryConnectionDesignated` message updates Dispatch's
 * own [com.pios.dispatch.application.PrimaryDriverRecord] projection).
 * [OrderSubmittedFirstRefusalConsumerIntegrationTest] already proves
 * "Primary -> First Refusal -> new order" (a real `OrderSubmitted` message,
 * against an *already-seeded* [PrimaryDriverRepository] row, produces a
 * Proposal for that primary driver). Neither file chains the two together
 * -- each starts its own half from a state the other file's own real event
 * flow would have produced. This file closes exactly that seam: one test,
 * two real published events, in the same order a genuine repeat-client
 * sequence produces them --
 *
 * 1. `RideRequest.tsx`'s own [com.pios.dispatch.api] equivalent on the
 *    passenger side: after a COMPLETED ride with a driver not yet in the
 *    passenger's circle of trust, `handleSaveDriver` calls the existing
 *    `POST /v1/connections` then `POST /v1/connections/:id/primary`
 *    (Passenger Experience) -- which is what publishes the real
 *    `PrimaryConnectionDesignated` event this test simulates with
 *    [PrimaryConnectionMessagePublisher], exactly as that real caller
 *    would.
 * 2. A later, brand-new order for that same passenger, submitted the way
 *    Order Management would for a caller that does not already know a
 *    specific driver (`explicitDriverIntent = false` -- the one condition
 *    under which First Refusal is meant to act at all; contrast
 *    `RideRequest.tsx`'s own always-explicit submissions, which
 *    deliberately never reach this path -- see
 *    [OrderSubmittedFirstRefusalConsumerIntegrationTest]'s own "Test C").
 *
 * No new API, no new model, no new production code: this test only
 * composes the two already-existing, already-proven publisher/listener
 * pairs [PrimaryConnectionMessagePublisher]/[PrimaryConnectionEventListener]
 * and [OrderSubmittedMessagePublisher]/[OrderSubmittedFirstRefusalListener]
 * in sequence, mirroring both existing files' own constructor-based,
 * no-Spring-context convention exactly.
 */
class RepeatClientLoopIntegrationTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))

    // Step 1: Connection -> Primary.
    private val primaryDriverRepository: PrimaryDriverRepository = PostgreSQLPrimaryDriverRepository(JdbcTemplate(dataSource))
    private val primaryDriverProjectionApplicationService = PrimaryDriverProjectionApplicationService(primaryDriverRepository, transactionRunner)
    private val primaryConnectionListener = PrimaryConnectionEventListener(primaryDriverProjectionApplicationService, ObjectMapper())
    private val primaryConnectionPublisher = PrimaryConnectionMessagePublisher(RabbitMQTestConnection.connectionFactory)
    private val primaryConnectionHarness = PrimaryConnectionTestListenerHarness(RabbitMQTestConnection.connectionFactory, primaryConnectionListener)

    // Step 2: First Refusal -> new order.
    private val proposalRepository = PostgreSQLProposalRepository(JdbcTemplate(dataSource))
    private val driverAvailabilityRepository = PostgreSQLDriverAvailabilityRepository(JdbcTemplate(dataSource))
    private val dispatchRequests = PostgreSQLDispatchRequestRepository(JdbcTemplate(dataSource))
    private val proposalApplicationService = ProposalApplicationService(proposalRepository, transactionRunner, driverAvailabilityRepository, dispatchRequests)
    private val firstRefusalApplicationService = FirstRefusalApplicationService(primaryDriverRepository, proposalApplicationService, driverAvailabilityRepository)
    private val fallbackDispatchApplicationService = FallbackDispatchApplicationService(driverAvailabilityRepository, proposalApplicationService)
    private val dispatchRequestApplicationService = DispatchRequestApplicationService(
        dispatchRequests, proposalRepository, proposalApplicationService,
        firstRefusalApplicationService, fallbackDispatchApplicationService,
        PostgreSQLOutboxRepository(JdbcTemplate(dataSource)), transactionRunner, ObjectMapper()
    )
    private val firstRefusalListener = OrderSubmittedFirstRefusalListener(dispatchRequestApplicationService, ObjectMapper())
    private val orderSubmittedPublisher = OrderSubmittedMessagePublisher(RabbitMQTestConnection.connectionFactory)
    private val firstRefusalHarness = OrderSubmittedFirstRefusalTestListenerHarness(RabbitMQTestConnection.connectionFactory, firstRefusalListener)

    @AfterTest
    fun stopHarnesses() {
        primaryConnectionHarness.stop()
        firstRefusalHarness.stop()
    }

    @Test
    fun `saving a driver as primary through the real Connection event, then submitting a new order, proposes exactly that driver`() {
        val passengerReference = "repeat-client-passenger-${UUID.randomUUID()}"
        val savedDriver = DriverReference("repeat-client-driver-${UUID.randomUUID()}")
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(savedDriver, available = true, isTest = false))

        // Step 1 -- Connection -> Primary, via the real event Passenger
        // Experience's own `POST /v1/connections/:id/primary` publishes.
        primaryConnectionPublisher.publishDesignated(passengerReference = passengerReference, driverId = savedDriver.driverId)
        val record = awaitUntilNotNull { primaryDriverRepository.findByPassenger(PassengerReference(passengerReference)) }
        assertNotNull(record)
        assertEquals(savedDriver, record.primaryDriverId)

        // Step 2 -- First Refusal -> new order, via the real event Order
        // Management publishes for a normal (non-explicit-driver) submission.
        val orderId = "repeat-client-order-${UUID.randomUUID()}"
        orderSubmittedPublisher.publish(orderId = orderId, passengerReference = passengerReference)
        val proposal = awaitUntilNotNull { proposalRepository.findByOrder(OrderReference(orderId)).firstOrNull() }

        assertNotNull(proposal)
        assertEquals(savedDriver, proposal.driver)
        assertEquals(ProposalStatus.OPEN, proposal.status)
        assertEquals(1, proposalRepository.findByOrder(OrderReference(orderId)).size)
    }

    @Test
    fun `clearing the saved primary driver stops the loop -- a later order is no longer proposed to them`() {
        val passengerReference = "repeat-client-cleared-passenger-${UUID.randomUUID()}"
        val savedDriver = DriverReference("repeat-client-cleared-driver-${UUID.randomUUID()}")
        // Unavailable, deliberately: this test's only claim is that First
        // Refusal no longer targets [savedDriver] once cleared -- making
        // them unavailable also keeps FR-003A's own Fallback Dispatch (a
        // separate, pre-existing mechanism this test does not own or
        // exercise) from independently proposing to them for an unrelated
        // reason, which would make this assertion ambiguous about *why* a
        // proposal did or did not name them.
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(savedDriver, available = false, isTest = false))

        primaryConnectionPublisher.publishDesignated(passengerReference = passengerReference, driverId = savedDriver.driverId)
        awaitUntilNotNull { primaryDriverRepository.findByPassenger(PassengerReference(passengerReference)) }

        primaryConnectionPublisher.publishCleared(passengerReference = passengerReference)
        awaitUntilNotNull {
            if (primaryDriverRepository.findByPassenger(PassengerReference(passengerReference)) == null) Unit else null
        }

        val orderId = "repeat-client-cleared-order-${UUID.randomUUID()}"
        // Prove the negative via a canary, the same discipline this
        // module's own Test B/Test C (OrderSubmittedFirstRefusalConsumerIntegrationTest)
        // already establish: publish a second, independent order for a
        // different passenger with its own (available) primary, and wait
        // for *that* proposal, which proves the queue is not simply stuck
        // before asserting the cleared passenger's own order named no
        // proposal for [savedDriver].
        val canaryPassenger = "repeat-client-canary-passenger-${UUID.randomUUID()}"
        val canaryDriver = DriverReference("repeat-client-canary-driver-${UUID.randomUUID()}")
        val canaryOrderId = "repeat-client-canary-order-${UUID.randomUUID()}"
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(canaryDriver, available = true, isTest = false))
        primaryConnectionPublisher.publishDesignated(passengerReference = canaryPassenger, driverId = canaryDriver.driverId)
        awaitUntilNotNull { primaryDriverRepository.findByPassenger(PassengerReference(canaryPassenger)) }

        orderSubmittedPublisher.publish(orderId = orderId, passengerReference = passengerReference)
        orderSubmittedPublisher.publish(orderId = canaryOrderId, passengerReference = canaryPassenger)
        awaitUntilNotNull { proposalRepository.findByOrder(OrderReference(canaryOrderId)).firstOrNull() }

        assertEquals(
            emptyList(),
            proposalRepository.findByOrder(OrderReference(orderId)).filter { it.driver == savedDriver }
        )
    }
}
