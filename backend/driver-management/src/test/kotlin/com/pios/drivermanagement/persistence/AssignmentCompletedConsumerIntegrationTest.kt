package com.pios.drivermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.drivermanagement.application.AssignmentCompletedApplicationService
import com.pios.drivermanagement.application.OrderSubmittedApplicationService
import com.pios.drivermanagement.domain.DriverId
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2)
 * and its Phase 2 extension (Section 2.1's own disclosed gap, now closed):
 * proves the real end-to-end path -- real AssignmentCompleted/OrderSubmitted
 * -shaped messages, published to the (test-vhost) exchanges exactly as
 * Dispatch's/Order Management's own publishers would, are consumed by
 * Driver Management's real listeners on their own, separate queues, and
 * grow the referenced driver's real milestone row (including
 * repeat-client detection) in the real (test) PostgreSQL database. Mirrors
 * Order Management's own `AssignmentCompletedConsumerIntegrationTest`
 * exactly, adapted to this module's own observable effect
 * (driver_milestones, not Order.status).
 */
class AssignmentCompletedConsumerIntegrationTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val assignmentCompletedRepository = PostgreSQLAssignmentCompletedRepository(JdbcTemplate(dataSource))
    private val driverMilestonesRepository = PostgreSQLDriverMilestonesRepository(JdbcTemplate(dataSource))
    private val orderPassengerRepository = PostgreSQLOrderPassengerRepository(JdbcTemplate(dataSource))
    private val driverClientsRepository = PostgreSQLDriverClientsRepository(JdbcTemplate(dataSource))
    private val driverRideStatedPricesRepository = PostgreSQLDriverRideStatedPricesRepository(JdbcTemplate(dataSource))
    private val orderSubmittedRepository = PostgreSQLOrderSubmittedRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val applicationService = AssignmentCompletedApplicationService(
        assignmentCompletedRepository,
        driverMilestonesRepository,
        orderPassengerRepository,
        driverClientsRepository,
        driverRideStatedPricesRepository,
        transactionRunner
    )
    private val orderSubmittedApplicationService =
        OrderSubmittedApplicationService(orderSubmittedRepository, orderPassengerRepository, transactionRunner)
    private val listener = AssignmentCompletedListener(applicationService, ObjectMapper())
    private val orderSubmittedListener = OrderSubmittedListener(orderSubmittedApplicationService, ObjectMapper())
    private val publisher = AssignmentCompletedMessagePublisher(RabbitMQTestConnection.connectionFactory)
    private val orderSubmittedPublisher = OrderSubmittedMessagePublisher(RabbitMQTestConnection.connectionFactory)
    private val harness = AssignmentCompletedTestListenerHarness(RabbitMQTestConnection.connectionFactory, listener)
    private val orderSubmittedHarness =
        OrderSubmittedTestListenerHarness(RabbitMQTestConnection.connectionFactory, orderSubmittedListener)

    private val jdbcTemplate = JdbcTemplate(dataSource)

    /** `driver_milestones.driver_id` has an FK to `drivers.id` -- a row must exist first. */
    private fun seedDriver(driverId: String) {
        jdbcTemplate.update("INSERT INTO drivers (id, availability) VALUES (?, 'UNAVAILABLE')", driverId)
    }

    @AfterTest
    fun stopHarnesses() {
        harness.stop()
        orderSubmittedHarness.stop()
    }

    @Test
    fun `a valid AssignmentCompleted message grows the referenced driver's real milestones in PostgreSQL`() {
        val driverId = "assignment-completed-it-${UUID.randomUUID()}"
        seedDriver(driverId)
        val eventId = UUID.randomUUID().toString()

        publisher.publishAssignmentCompleted(driverId = driverId, eventId = eventId)

        val processed = awaitUntilNotNull { assignmentCompletedRepository.isProcessed(eventId).takeIf { it } }
        assertEquals(true, processed)
        val milestones = driverMilestonesRepository.findByDriverId(DriverId(driverId))
        assertEquals(1L, milestones?.completedRidesCount)
        assertEquals(1, milestones?.currentStreakWeeks)
    }

    @Test
    fun `redelivering the same AssignmentCompleted eventId does not double-count the ride`() {
        val driverId = "assignment-completed-it-redelivered-${UUID.randomUUID()}"
        seedDriver(driverId)
        val eventId = UUID.randomUUID().toString()

        publisher.publishAssignmentCompleted(driverId = driverId, eventId = eventId)
        assertEquals(true, awaitUntilNotNull { assignmentCompletedRepository.isProcessed(eventId).takeIf { it } })

        publisher.publishAssignmentCompleted(driverId = driverId, eventId = eventId)
        Thread.sleep(500)

        assertEquals(1L, driverMilestonesRepository.findByDriverId(DriverId(driverId))?.completedRidesCount)
    }

    @Test
    fun `two distinct AssignmentCompleted messages for the same driver both count`() {
        val driverId = "assignment-completed-it-two-${UUID.randomUUID()}"
        seedDriver(driverId)
        val firstEventId = UUID.randomUUID().toString()
        val secondEventId = UUID.randomUUID().toString()

        publisher.publishAssignmentCompleted(driverId = driverId, eventId = firstEventId)
        publisher.publishAssignmentCompleted(driverId = driverId, eventId = secondEventId)

        assertEquals(true, awaitUntilNotNull { assignmentCompletedRepository.isProcessed(firstEventId).takeIf { it } })
        assertEquals(true, awaitUntilNotNull { assignmentCompletedRepository.isProcessed(secondEventId).takeIf { it } })
        assertEquals(2L, driverMilestonesRepository.findByDriverId(DriverId(driverId))?.completedRidesCount)
    }

    // --- Phase 2 extension: repeat clients, end to end (Section 2.1) ---

    @Test
    fun `a real OrderSubmitted followed by a real AssignmentCompleted for the same order attributes the ride to that order's passenger`() {
        val driverId = "assignment-completed-it-passenger-${UUID.randomUUID()}"
        seedDriver(driverId)
        val orderId = "order-${UUID.randomUUID()}"
        val passengerReference = "passenger-${UUID.randomUUID()}"

        orderSubmittedPublisher.publishOrderSubmitted(orderId = orderId, passengerReference = passengerReference)
        assertEquals(passengerReference, awaitUntilNotNull { orderPassengerRepository.findPassengerReference(orderId) })

        publisher.publishAssignmentCompleted(driverId = driverId, orderReference = orderId)

        awaitUntilNotNull { driverMilestonesRepository.findByDriverId(DriverId(driverId))?.takeIf { it.completedRidesCount == 1L } }
        assertEquals(1L, driverMilestonesRepository.findByDriverId(DriverId(driverId))?.completedRidesCount)
    }

    @Test
    fun `the same passenger completing two rides with one driver makes them a real, persisted repeat client`() {
        val driverId = "assignment-completed-it-repeat-${UUID.randomUUID()}"
        seedDriver(driverId)
        val passengerReference = "passenger-${UUID.randomUUID()}"
        val firstOrderId = "order-${UUID.randomUUID()}"
        val secondOrderId = "order-${UUID.randomUUID()}"

        orderSubmittedPublisher.publishOrderSubmitted(orderId = firstOrderId, passengerReference = passengerReference)
        orderSubmittedPublisher.publishOrderSubmitted(orderId = secondOrderId, passengerReference = passengerReference)
        awaitUntilNotNull { orderPassengerRepository.findPassengerReference(firstOrderId) }
        awaitUntilNotNull { orderPassengerRepository.findPassengerReference(secondOrderId) }

        publisher.publishAssignmentCompleted(driverId = driverId, orderReference = firstOrderId)
        awaitUntilNotNull { driverMilestonesRepository.findByDriverId(DriverId(driverId))?.takeIf { it.completedRidesCount == 1L } }
        assertEquals(0, driverMilestonesRepository.findByDriverId(DriverId(driverId))?.repeatClientsCount)

        publisher.publishAssignmentCompleted(driverId = driverId, orderReference = secondOrderId)
        awaitUntilNotNull { driverMilestonesRepository.findByDriverId(DriverId(driverId))?.takeIf { it.repeatClientsCount == 1 } }
        assertEquals(1, driverMilestonesRepository.findByDriverId(DriverId(driverId))?.repeatClientsCount)
        assertEquals(2L, driverMilestonesRepository.findByDriverId(DriverId(driverId))?.completedRidesCount)
    }

    @Test
    fun `an AssignmentCompleted for an order whose OrderSubmitted was never consumed still counts the ride`() {
        val driverId = "assignment-completed-it-no-passenger-${UUID.randomUUID()}"
        seedDriver(driverId)
        val orderId = "order-never-submitted-${UUID.randomUUID()}"

        publisher.publishAssignmentCompleted(driverId = driverId, orderReference = orderId)

        awaitUntilNotNull { driverMilestonesRepository.findByDriverId(DriverId(driverId))?.takeIf { it.completedRidesCount == 1L } }
        assertEquals(0, driverMilestonesRepository.findByDriverId(DriverId(driverId))?.repeatClientsCount)
    }

    // --- ADR-065: Driver Earnings from Self-Stated Prices, end to end ---

    @Test
    fun `a real AssignmentCompleted carrying a digits-only statedPrice grows the referenced driver's real earnings in PostgreSQL`() {
        val driverId = "assignment-completed-it-price-${UUID.randomUUID()}"
        seedDriver(driverId)

        publisher.publishAssignmentCompleted(driverId = driverId, statedPrice = "350")

        awaitUntilNotNull { driverMilestonesRepository.findByDriverId(DriverId(driverId))?.takeIf { it.completedRidesCount == 1L } }
        val milestones = driverMilestonesRepository.findByDriverId(DriverId(driverId))
        assertEquals(350L, milestones?.totalStatedEarnings)
        assertEquals(0, milestones?.unpricedRidesCount)
    }

    @Test
    fun `a real AssignmentCompleted carrying a non-numeric statedPrice does not grow earnings and counts as unpriced`() {
        val driverId = "assignment-completed-it-unpriced-${UUID.randomUUID()}"
        seedDriver(driverId)

        publisher.publishAssignmentCompleted(driverId = driverId, statedPrice = "договоримся")

        awaitUntilNotNull { driverMilestonesRepository.findByDriverId(DriverId(driverId))?.takeIf { it.completedRidesCount == 1L } }
        val milestones = driverMilestonesRepository.findByDriverId(DriverId(driverId))
        assertEquals(0L, milestones?.totalStatedEarnings)
        assertEquals(1, milestones?.unpricedRidesCount)
    }

    @Test
    fun `a real AssignmentCompleted with no statedPrice at all still completes the ride, counted as unpriced`() {
        val driverId = "assignment-completed-it-no-price-${UUID.randomUUID()}"
        seedDriver(driverId)

        publisher.publishAssignmentCompleted(driverId = driverId)

        awaitUntilNotNull { driverMilestonesRepository.findByDriverId(DriverId(driverId))?.takeIf { it.completedRidesCount == 1L } }
        val milestones = driverMilestonesRepository.findByDriverId(DriverId(driverId))
        assertEquals(0L, milestones?.totalStatedEarnings)
        assertEquals(1, milestones?.unpricedRidesCount)
    }
}
