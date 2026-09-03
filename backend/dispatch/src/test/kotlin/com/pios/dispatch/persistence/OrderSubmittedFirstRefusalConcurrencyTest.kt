package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.DriverAvailabilityRecord
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
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val proposalApplicationService =
        ProposalApplicationService(proposalRepository, transactionRunner, driverAvailabilityRepository)
    private val firstRefusalApplicationService =
        FirstRefusalApplicationService(primaryDriverRepository, proposalApplicationService, driverAvailabilityRepository)
    private val listener = OrderSubmittedFirstRefusalListener(firstRefusalApplicationService, ObjectMapper())
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
        driverAvailabilityRepository.upsert(DriverAvailabilityRecord(primaryDriver, available = true))
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
}
