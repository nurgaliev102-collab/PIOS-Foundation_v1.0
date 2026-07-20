package com.pios.dispatch.persistence

import com.pios.dispatch.application.DriverAvailabilityProjectionApplicationService
import com.pios.dispatch.application.DriverAvailabilityRecord
import com.pios.dispatch.application.DriverAvailabilityRepository
import com.pios.dispatch.application.DriverAvailabilityUpdateCommand
import com.pios.dispatch.domain.DriverReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Proves Test D: the local availability-state update and the eventId
 * idempotency recording must be transactionally consistent (ADR-032). If
 * applying the local effect fails, the eventId must not remain recorded
 * as processed -- otherwise a message that genuinely failed to apply
 * would be silently, permanently treated as an already-handled duplicate,
 * losing its business effect forever. Uses a real PostgreSQL transaction
 * and a real repository for the idempotency write (only the availability
 * write is faked to fail), exactly mirroring the rollback-proof pattern
 * already established for Order Management's and Driver Management's own
 * outbox transaction tests.
 */
class DriverAvailabilityProjectionTransactionTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val realRepository = PostgreSQLDriverAvailabilityRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))

    @Test
    fun `if applying the local availability effect fails, the eventId's idempotency record is rolled back too`() {
        val eventId = UUID.randomUUID().toString()
        val driverReference = "tx-driver-${UUID.randomUUID()}"

        val failingRepository = object : DriverAvailabilityRepository {
            override fun markProcessed(eventId: String): Boolean = realRepository.markProcessed(eventId)
            override fun upsert(record: DriverAvailabilityRecord): Unit =
                throw RuntimeException("simulated local persistence failure")
            override fun findByDriverReference(driverReference: DriverReference): DriverAvailabilityRecord? =
                realRepository.findByDriverReference(driverReference)
        }
        val failingService = DriverAvailabilityProjectionApplicationService(failingRepository, transactionRunner)

        assertFailsWith<RuntimeException> {
            failingService.handle(DriverAvailabilityUpdateCommand(eventId, driverReference, true))
        }

        // The failed attempt's markProcessed insert must have rolled back
        // together with the failed upsert -- proven against the real
        // repository/database: markProcessed for the same eventId still
        // behaves as "first time" (returns true), and no availability
        // record was ever committed.
        assertEquals(true, realRepository.markProcessed(eventId))
        assertNull(realRepository.findByDriverReference(DriverReference(driverReference)))
    }
}
