package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.DispatchRequestApplicationService
import com.pios.dispatch.application.DriverAvailabilityRecord
import com.pios.dispatch.application.FallbackDispatchApplicationService
import com.pios.dispatch.application.FirstRefusalApplicationService
import com.pios.dispatch.application.PrimaryDriverRecord
import com.pios.dispatch.application.PrimaryDriverRepository
import com.pios.dispatch.application.ProposalApplicationService
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference
import com.pios.dispatch.domain.PassengerReference
import com.pios.dispatch.domain.ProposalStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Task 16 (First Refusal Runtime Integration), Test E. Proves, against
 * real PostgreSQL and real concurrent threads (not RabbitMQ's own
 * delivery ordering, not mocks), that
 * [OrderSubmittedFirstRefusalListener.onMessage] itself introduces no new
 * race beyond what [FirstRefusalApplicationService.attempt] already
 * closes (Task 15C's own `ProposalConcurrencyTest`) -- calling the
 * listener's own transport-boundary parsing and dispatch concurrently,
 * not only the service underneath it.
 *
 * The scenario: the identical `OrderSubmitted` message (same `eventId`,
 * same payload) delivered to two concurrently-running invocations of the
 * listener's own `onMessage` -- a genuinely realistic shape a redelivery
 * racing a still-in-flight original delivery could take (RabbitMQ is an
 * at-least-once broker; nothing here assumes it cannot deliver
 * concurrently to two channels/threads).
 */
class OrderSubmittedFirstRefusalConcurrencyTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val proposalRepository = PostgreSQLProposalRepository(JdbcTemplate(dataSource))
    private val primaryDriverRepository: PrimaryDriverRepository = PostgreSQLPrimaryDriverRepository(JdbcTemplate(dataSource))
    private val driverAvailabilityRepository = PostgreSQLDriverAvailabilityRepository(JdbcTemplate(dataSource))
    private val dispatchRequests = PostgreSQLDispatchRequestRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val proposalApplicationService =
        ProposalApplicationService(proposalRepository, transactionRunner, driverAvailabilityRepository, dispatchRequests)
    private val firstRefusalApplicationService =
        FirstRefusalApplicationService(primaryDriverRepository, proposalApplicationService, driverAvailabilityRepository)
    private val fallbackDispatchApplicationService =
        FallbackDispatchApplicationService(driverAvailabilityRepository, proposalApplicationService)
    private val dispatchRequestApplicationService = DispatchRequestApplicationService(
        dispatchRequests, proposalRepository, proposalApplicationService,
        firstRefusalApplicationService, fallbackDispatchApplicationService,
        PostgreSQLOutboxRepository(JdbcTemplate(dataSource)), transactionRunner, ObjectMapper()
    )
    private val listener = OrderSubmittedFirstRefusalListener(dispatchRequestApplicationService, ObjectMapper())
    private val objectMapper = ObjectMapper()

    private fun envelopeFor(orderId: String, passengerReference: String, eventId: String): String =
        objectMapper.writeValueAsString(
            mapOf(
                "eventId" to eventId,
                "eventType" to "OrderSubmitted",
                "eventVersion" to 1,
                "occurredAt" to Instant.now().toString(),
                "payload" to mapOf(
                    "orderId" to orderId,
                    "passengerReference" to passengerReference,
                    "explicitDriverIntent" to false,
                    "isTest" to false
                )
            )
        )

    @Test
    fun `Test E -- two concurrent listener invocations for the identical message produce exactly one OPEN proposal`() {
        val orderId = "order-${UUID.randomUUID()}"
        val passengerReference = "passenger-${UUID.randomUUID()}"
        val primaryDriver = DriverReference("driver-${UUID.randomUUID()}")
        val eventId = UUID.randomUUID().toString()
        primaryDriverRepository.upsert(PrimaryDriverRecord(PassengerReference(passengerReference), primaryDriver))
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true, isTest = false))
        val envelope = envelopeFor(orderId, passengerReference, eventId)

        val executor = Executors.newFixedThreadPool(2)
        val startLatch = CountDownLatch(1)
        try {
            val futures = (1..2).map {
                executor.submit {
                    startLatch.await()
                    listener.onMessage(envelope)
                }
            }
            startLatch.countDown()
            futures.forEach { it.get(15, TimeUnit.SECONDS) }
        } finally {
            executor.shutdown()
        }

        val proposals = proposalRepository.findByOrder(OrderReference(orderId))
        assertEquals(1, proposals.size, "exactly one OPEN proposal must exist after two concurrent listener invocations")
        assertEquals(ProposalStatus.OPEN, proposals.single().status)
        assertEquals(primaryDriver, proposals.single().driver)
    }

    /**
     * FR-003A's own analogue of Test E above: [FallbackDispatchApplicationService.attempt]
     * delegates to the identical [ProposalApplicationService.handle] ->
     * `Proposal.propose` path, so the same `proposals_one_open_per_order`
     * (V14) guarantee applies mechanically -- this test exercises it
     * directly for Fallback Dispatch rather than relying only on that
     * inherited proof.
     */
    @Test
    fun `Test G -- FR-003A -- two concurrent Fallback Dispatch attempts for the same order produce exactly one OPEN proposal`() {
        val order = OrderReference("order-${UUID.randomUUID()}")
        val passenger = PassengerReference("passenger-${UUID.randomUUID()}")
        // No PrimaryDriverRecord for this passenger -- both attempts must
        // reach FallbackDispatchApplicationService.attempt on equal footing.
        val fallbackDriver = DriverReference("driver-fallback-${UUID.randomUUID()}")
        try {
            // ADR-069 Part 3: this order's own isTest defaults to false, so
            // the fallback candidate must be an explicitly real driver.
            driverAvailabilityRepository.upsert(DriverAvailabilityRecord(fallbackDriver, available = true, isTest = false))
            // Forced far into the past (randomized -- see this module's own
            // OrderSubmittedFirstRefusalConsumerIntegrationTest "Test B"
            // KDoc for why a shared literal would collide) so this driver is
            // deterministically the "longest idle" candidate regardless of
            // any other available-driver row this shared pios_dispatch_test
            // database may already hold.
            JdbcTemplate(dataSource).update(
                "UPDATE driver_availability SET updated_at = ? WHERE driver_reference = ?",
                java.sql.Timestamp.from(Instant.parse("2000-01-01T00:00:00Z").minusSeconds((0..3_000_000_000L).random())),
                fallbackDriver.driverId
            )

            val executor = Executors.newFixedThreadPool(2)
            val startLatch = CountDownLatch(1)
            try {
                val futures = (1..2).map {
                    executor.submit {
                        startLatch.await()
                        fallbackDispatchApplicationService.attempt(order, passenger)
                    }
                }
                startLatch.countDown()
                futures.forEach { it.get(15, TimeUnit.SECONDS) }
            } finally {
                executor.shutdown()
            }

            val proposals = proposalRepository.findByOrder(order)
            assertEquals(1, proposals.size, "exactly one OPEN proposal must exist after two concurrent Fallback Dispatch attempts")
            assertEquals(ProposalStatus.OPEN, proposals.single().status)
            assertEquals(fallbackDriver, proposals.single().driver)
        } finally {
            JdbcTemplate(dataSource).update("DELETE FROM driver_availability WHERE driver_reference = ?", fallbackDriver.driverId)
        }
    }
}
