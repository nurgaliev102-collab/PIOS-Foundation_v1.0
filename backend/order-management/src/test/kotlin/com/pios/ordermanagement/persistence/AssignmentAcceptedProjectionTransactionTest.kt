package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.application.AssignmentAcceptedProjectionApplicationService
import com.pios.ordermanagement.application.AssignmentAcceptedUpdateCommand
import com.pios.ordermanagement.application.OrderAssignmentRecognitionHandler
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves the eventId idempotency recording and the recognition handler's
 * own invocation are transactionally consistent (ADR-032): if recognizing
 * the assignment fails, the eventId must not remain recorded as
 * processed -- otherwise a message that genuinely failed to apply would
 * be silently, permanently treated as an already-handled duplicate,
 * losing its business effect forever. Mirrors Dispatch's own
 * already-proven `DriverAvailabilityProjectionTransactionTest` exactly.
 *
 * Uses the real [OrderAssignmentRecognitionHandler]'s own existing
 * validation (it throws [IllegalArgumentException] for a blank
 * reference) as the deterministic failure, rather than a fake or
 * subclassed handler -- [OrderAssignmentRecognitionHandler] is a final
 * class, and faking its failure this way requires no test-only
 * modification to it.
 */
class AssignmentAcceptedProjectionTransactionTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val realRepository = PostgreSQLAssignmentAcceptedRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val service = AssignmentAcceptedProjectionApplicationService(
        realRepository,
        OrderAssignmentRecognitionHandler(),
        transactionRunner
    )

    @Test
    fun `if recognizing the assignment fails, the eventId's idempotency record is rolled back too`() {
        val eventId = UUID.randomUUID().toString()

        assertFailsWith<IllegalArgumentException> {
            // A blank driverReference makes the real recognition
            // handler's own validation throw deterministically.
            service.handle(AssignmentAcceptedUpdateCommand(eventId, "tx-order", ""))
        }

        // The failed attempt's markProcessed insert must have rolled back
        // together with the failed recognition -- proven against the real
        // repository/database: markProcessed for the same eventId still
        // behaves as "first time" (returns true).
        assertEquals(true, realRepository.markProcessed(eventId))
    }
}
