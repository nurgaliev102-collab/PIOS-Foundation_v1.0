package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.DriverAvailabilityRecord
import com.pios.dispatch.application.FirstRefusalApplicationService
import com.pios.dispatch.application.PrimaryDriverRecord
import com.pios.dispatch.application.PrimaryDriverRepository
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.application.ProposeDriverCommand
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
import kotlin.test.assertTrue

/**
 * Task 16 (First Refusal Runtime Integration), Tests A, B, C (observable
 * half), D, F. Proves, against the real, locally-running RabbitMQ broker
 * (isolated `pios-test` vhost) and the real, isolated `pios_dispatch_test`
 * PostgreSQL database -- not mocks -- that a real `OrderSubmitted`-shaped
 * message, published exactly as Order Management's own publisher would,
 * is consumed by Dispatch's real [OrderSubmittedFirstRefusalListener] and
 * results in the correct, observable First Refusal outcome. Mirrors
 * [PrimaryConnectionConsumerIntegrationTest]'s own exact shape.
 */
class OrderSubmittedFirstRefusalConsumerIntegrationTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val proposalRepository = PostgreSQLProposalRepository(JdbcTemplate(dataSource))
    private val primaryDriverRepository: PrimaryDriverRepository = PostgreSQLPrimaryDriverRepository(JdbcTemplate(dataSource))
    private val driverAvailabilityRepository = PostgreSQLDriverAvailabilityRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val proposalApplicationService =
        ProposalApplicationService(proposalRepository, transactionRunner, driverAvailabilityRepository)
    private val firstRefusalApplicationService =
        FirstRefusalApplicationService(primaryDriverRepository, proposalApplicationService, driverAvailabilityRepository)
    private val listener = OrderSubmittedFirstRefusalListener(firstRefusalApplicationService, ObjectMapper())
    private val publisher = OrderSubmittedMessagePublisher(RabbitMQTestConnection.connectionFactory)
    private val harness = OrderSubmittedFirstRefusalTestListenerHarness(RabbitMQTestConnection.connectionFactory, listener)

    @AfterTest
    fun stopHarness() {
        harness.stop()
    }

    @Test
    fun `Test A -- a real OrderSubmitted message with an eligible primary driver produces exactly one OPEN proposal for that driver`() {
        val orderId = "order-${UUID.randomUUID()}"
        val passengerReference = "passenger-${UUID.randomUUID()}"
        val primaryDriver = DriverReference("driver-${UUID.randomUUID()}")
        primaryDriverRepository.upsert(PrimaryDriverRecord(PassengerReference(passengerReference), primaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true))

        publisher.publish(orderId = orderId, passengerReference = passengerReference)

        val proposal = awaitUntilNotNull {
            proposalRepository.findByOrder(OrderReference(orderId)).firstOrNull()
        }

        kotlin.test.assertNotNull(proposal)
        assertEquals(primaryDriver, proposal.driver)
        assertEquals(ProposalStatus.OPEN, proposal.status)
        assertEquals(1, proposalRepository.findByOrder(OrderReference(orderId)).size)
    }

    @Test
    fun `Test A -- isTest is carried from the event through to the created proposal`() {
        val orderId = "order-${UUID.randomUUID()}"
        val passengerReference = "passenger-${UUID.randomUUID()}"
        val primaryDriver = DriverReference("driver-${UUID.randomUUID()}")
        primaryDriverRepository.upsert(PrimaryDriverRecord(PassengerReference(passengerReference), primaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true))

        publisher.publish(orderId = orderId, passengerReference = passengerReference, isTest = true)

        val proposal = awaitUntilNotNull { proposalRepository.findByOrder(OrderReference(orderId)).firstOrNull() }

        kotlin.test.assertNotNull(proposal)
        assertEquals(true, proposal.isTest)
    }

    @Test
    fun `Test B -- a real OrderSubmitted message with no primary driver creates no proposal and processes without error`() {
        val orderId = "order-${UUID.randomUUID()}"
        val passengerReference = "passenger-${UUID.randomUUID()}"
        // No PrimaryDriverRecord upserted for this passenger at all.

        publisher.publish(orderId = orderId, passengerReference = passengerReference)

        // No proposal will ever appear -- awaitUntilNotNull would time out
        // waiting for something that never arrives, so instead prove the
        // *negative* by giving the (real, asynchronous) consumer ample time
        // to have processed the message, then asserting nothing was created.
        // A second, real message for a *different* order is published and
        // awaited afterward -- its own successful, fast processing is what
        // proves the listener's own queue was not stuck or backed up by
        // this test's own message.
        val canaryOrderId = "order-canary-${UUID.randomUUID()}"
        val canaryPassenger = "passenger-canary-${UUID.randomUUID()}"
        val canaryDriver = DriverReference("driver-canary-${UUID.randomUUID()}")
        primaryDriverRepository.upsert(PrimaryDriverRecord(PassengerReference(canaryPassenger), canaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(canaryDriver, available = true))
        publisher.publish(orderId = canaryOrderId, passengerReference = canaryPassenger)
        val canaryProposal = awaitUntilNotNull { proposalRepository.findByOrder(OrderReference(canaryOrderId)).firstOrNull() }
        kotlin.test.assertNotNull(canaryProposal, "the canary message must have been processed -- proves the queue is not stuck")

        assertEquals(emptyList(), proposalRepository.findByOrder(OrderReference(orderId)))
    }

    @Test
    fun `Test C -- explicit driver intent declared creates no automatic proposal even with an eligible primary driver`() {
        val orderId = "order-${UUID.randomUUID()}"
        val passengerReference = "passenger-${UUID.randomUUID()}"
        val primaryDriver = DriverReference("driver-${UUID.randomUUID()}")
        primaryDriverRepository.upsert(PrimaryDriverRecord(PassengerReference(passengerReference), primaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true))

        publisher.publish(orderId = orderId, passengerReference = passengerReference, explicitDriverIntent = true)

        // Same "prove the negative via a canary" technique as Test B.
        val canaryOrderId = "order-canary-${UUID.randomUUID()}"
        publisher.publish(orderId = canaryOrderId, passengerReference = passengerReference, explicitDriverIntent = false)
        awaitUntilNotNull { proposalRepository.findByOrder(OrderReference(canaryOrderId)).firstOrNull() }

        assertEquals(emptyList(), proposalRepository.findByOrder(OrderReference(orderId)))
    }

    @Test
    fun `Task 17 Test 3 -- explicit choice of a different driver suppresses First Refusal for the primary driver, and the explicit proposal itself still succeeds`() {
        // Mirrors the real production shape exactly: a passenger reaches
        // this driver through a personal invitation link (driverB) while a
        // *different* driver (driverA) is separately their own Primary
        // Driver (Circle of Trust, ADR-062) -- the real scenario
        // `RideRequest.tsx`'s own KDoc (Task 17) describes.
        val orderId = "order-${UUID.randomUUID()}"
        val passengerReference = "passenger-${UUID.randomUUID()}"
        val primaryDriverA = DriverReference("driver-a-${UUID.randomUUID()}")
        val explicitlyChosenDriverB = DriverReference("driver-b-${UUID.randomUUID()}")
        primaryDriverRepository.upsert(PrimaryDriverRecord(PassengerReference(passengerReference), primaryDriverA))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriverA, available = true))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(explicitlyChosenDriverB, available = true))

        // Step 1 -- the real Order Management -> Dispatch path: OrderSubmitted
        // with explicitDriverIntent = true (what RideRequest.tsx's own
        // POST /v1/orders now sends, per Task 17).
        publisher.publish(orderId = orderId, passengerReference = passengerReference, explicitDriverIntent = true)

        // Step 2 -- the real RideRequest.tsx -> Dispatch path: its own
        // separate POST /v1/proposals call, proposing the exact driver
        // (driverB) whose link the passenger arrived through --
        // `attemptProposal` in RideRequest.tsx, called directly here since
        // this test exercises Dispatch's own application layer, not the
        // frontend or its HTTP transport.
        val explicitProposal = proposalApplicationService.handle(
            ProposeDriverCommand(order = OrderReference(orderId), driver = explicitlyChosenDriverB)
        )

        // Give the (real, asynchronous) OrderSubmitted delivery ample time
        // to have reached the listener before asserting no proposal for
        // the primary driver was ever created -- same "prove the negative"
        // discipline as Test B/Test C above, but timed rather than
        // canary-based since the explicit proposal above already gives a
        // real, independent signal that the queue is not stuck.
        Thread.sleep(2000L)

        val proposalsForOrder = proposalRepository.findByOrder(OrderReference(orderId))
        assertEquals(1, proposalsForOrder.size, "exactly one proposal must exist for this order -- the explicit one, never a competing First Refusal proposal")
        assertEquals(explicitlyChosenDriverB, proposalsForOrder.single().driver)
        assertEquals(explicitProposal.proposal.id, proposalsForOrder.single().id)
        assertEquals(ProposalStatus.OPEN, proposalsForOrder.single().status)
    }

    @Test
    fun `Test D -- redelivering the same OrderSubmitted eventId does not create a second proposal`() {
        val orderId = "order-${UUID.randomUUID()}"
        val passengerReference = "passenger-${UUID.randomUUID()}"
        val primaryDriver = DriverReference("driver-${UUID.randomUUID()}")
        val eventId = UUID.randomUUID().toString()
        primaryDriverRepository.upsert(PrimaryDriverRecord(PassengerReference(passengerReference), primaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true))

        publisher.publish(orderId = orderId, passengerReference = passengerReference, eventId = eventId)
        val first = awaitUntilNotNull { proposalRepository.findByOrder(OrderReference(orderId)).firstOrNull() }
        kotlin.test.assertNotNull(first)

        // Redeliver the identical eventId -- Proposal.propose's own root
        // invariant (an OPEN proposal already exists for this order) is
        // what rejects the duplicate; no eventId ledger is involved (this
        // consumer deliberately has none -- see the listener's own KDoc).
        publisher.publish(orderId = orderId, passengerReference = passengerReference, eventId = eventId)

        // Give the redelivery time to reach the listener before asserting
        // the proposal count never changed.
        Thread.sleep(2000L)

        val proposals = proposalRepository.findByOrder(OrderReference(orderId))
        assertEquals(1, proposals.size, "a redelivered eventId must not create a second proposal")
        assertEquals(first.id, proposals.single().id)
    }

    @Test
    fun `Test F -- the full real pipeline (publish, real RabbitMQ, real consumer, real PrimaryDriverRecord lookup, real Proposal creation) works end to end`() {
        // Restates Test A's own assertions, explicitly named to match this
        // task's own required Test F -- the same test satisfies both,
        // since Test A already is the full real pipeline; this is not
        // duplicated logic, only a second, explicit acknowledgment that
        // every real layer (RabbitMQ, PrimaryDriverRecord, Proposal) was
        // exercised, not mocked, anywhere in this file.
        val orderId = "order-${UUID.randomUUID()}"
        val passengerReference = "passenger-${UUID.randomUUID()}"
        val primaryDriver = DriverReference("driver-${UUID.randomUUID()}")
        primaryDriverRepository.upsert(PrimaryDriverRecord(PassengerReference(passengerReference), primaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true))

        publisher.publish(orderId = orderId, passengerReference = passengerReference)

        val proposal = awaitUntilNotNull { proposalRepository.findByOrder(OrderReference(orderId)).firstOrNull() }
        assertTrue(proposal != null && proposal.status == ProposalStatus.OPEN && proposal.driver == primaryDriver)
    }
}
