package com.pios.passengerexperience.persistence

import com.pios.passengerexperience.application.OutboxRecord
import com.pios.passengerexperience.application.OutboxRepository
import com.pios.passengerexperience.application.RemoveConnectionApplicationService
import com.pios.passengerexperience.application.SetPrimaryConnectionApplicationService
import com.pios.passengerexperience.domain.Connection
import com.pios.passengerexperience.domain.ConnectionId
import com.pios.passengerexperience.domain.DriverReference
import com.pios.passengerexperience.domain.PassengerReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task 14 (First Refusal Foundation), Phase 6/8: proves the primary
 * designation/clearing and its outbox record commit atomically against a
 * real PostgreSQL database (the isolated `pios_passenger_experience_test`
 * database) — mirroring `com.pios.dispatch.persistence.TripCreationTransactionTest`'s
 * own proof style exactly.
 */
class SetPrimaryConnectionTransactionTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val connectionRepository = PostgreSQLConnectionRepository(JdbcTemplate(dataSource))
    private val primaryConnectionRepository = PostgreSQLPrimaryConnectionRepository(JdbcTemplate(dataSource))
    private val outboxRepository = PostgreSQLOutboxRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val setPrimaryService = SetPrimaryConnectionApplicationService(
        connectionRepository,
        primaryConnectionRepository,
        transactionRunner,
        outboxRepository
    )

    private fun newConnection(suffix: String) = Connection(
        id = ConnectionId("tx-connection-$suffix-${UUID.randomUUID()}"),
        driverId = DriverReference("tx-driver-$suffix"),
        passengerReference = PassengerReference("tx-passenger-$suffix-${UUID.randomUUID()}"),
        createdAt = Instant.now()
    ).also { connectionRepository.save(it) }

    @Test
    fun `designating a primary persists the designation and its outbox record together, in one transaction`() {
        val connection = newConnection("1")

        setPrimaryService.handle(connection.id)

        assertEquals(connection.id, primaryConnectionRepository.findByPassenger(connection.passengerReference))
        val unpublished = outboxRepository.findUnpublished()
        assertTrue(unpublished.any { it.aggregateId == connection.passengerReference.passengerId && it.eventType == "PrimaryConnectionDesignated" })
    }

    @Test
    fun `a designation survives a fresh read through a brand new repository instance -- proving real persistence`() {
        val connection = newConnection("2")
        setPrimaryService.handle(connection.id)

        val freshRepository = PostgreSQLPrimaryConnectionRepository(JdbcTemplate(dataSource))

        assertEquals(connection.id, freshRepository.findByPassenger(connection.passengerReference))
    }

    @Test
    fun `if the outbox save fails, the primary designation is rolled back too -- shares the same atomicity`() {
        val connection = newConnection("3")
        val failingOutboxRepository = object : OutboxRepository {
            override fun save(record: OutboxRecord): OutboxRecord = throw RuntimeException("simulated outbox failure")
            override fun findUnpublished(): List<OutboxRecord> = emptyList()
            override fun markPublished(id: Long) = Unit
            override fun countUnpublished() = com.pios.passengerexperience.application.OutboxBacklog(0, null)
        }
        val failingService = SetPrimaryConnectionApplicationService(
            connectionRepository,
            primaryConnectionRepository,
            transactionRunner,
            failingOutboxRepository
        )

        assertFailsWith<RuntimeException> {
            failingService.handle(connection.id)
        }

        // The designation itself never committed either -- whole
        // transaction (designation + outbox) rolled back together.
        assertNull(primaryConnectionRepository.findByPassenger(connection.passengerReference))
    }

    @Test
    fun `clearing a primary through removal persists both the removal and its outbox record together`() {
        val connection = newConnection("4")
        setPrimaryService.handle(connection.id)
        val removeService = RemoveConnectionApplicationService(
            connectionRepository,
            primaryConnectionRepository,
            transactionRunner,
            outboxRepository
        )

        removeService.handle(connection.id)

        assertNull(primaryConnectionRepository.findByPassenger(connection.passengerReference))
        val unpublished = outboxRepository.findUnpublished()
        assertTrue(unpublished.any { it.aggregateId == connection.passengerReference.passengerId && it.eventType == "PrimaryConnectionCleared" })
    }
}
