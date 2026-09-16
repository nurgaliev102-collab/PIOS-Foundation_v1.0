package com.pios.dispatch.persistence

import com.pios.dispatch.application.DispatchRequestRecord
import com.pios.dispatch.application.DispatchRequestState
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

class PostgreSQLDispatchRequestRepositoryTest {
    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val repository = PostgreSQLDispatchRequestRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val now = Instant.parse("2099-09-16T12:00:00Z")

    @Test
    fun `a pending request is durable, due, and idempotently offered`() {
        val orderId = "pending-${UUID.randomUUID()}"
        transactionRunner.run {
            repository.insertIfAbsent(record(orderId))
            repository.insertIfAbsent(record(orderId))
        }
        assertEquals(DispatchRequestState.PENDING, transactionRunner.run { repository.findForUpdate(orderId)?.state })
        assertTrue(repository.findDueOrderIds(now, 10_000).contains(orderId))

        transactionRunner.run { repository.markOffered(orderId) }

        assertEquals(DispatchRequestState.OFFERED, transactionRunner.run { repository.findForUpdate(orderId)?.state })
        assertTrue(repository.findDueOrderIds(now, 10_000).none { it == orderId })
    }

    @Test
    fun `cancellation tombstone wins if OrderCancelled arrives before OrderSubmitted`() {
        val orderId = "cancelled-first-${UUID.randomUUID()}"
        transactionRunner.run { repository.markCancelled(orderId) }
        transactionRunner.run { repository.insertIfAbsent(record(orderId)) }

        assertEquals(DispatchRequestState.CANCELLED, transactionRunner.run { repository.findForUpdate(orderId)?.state })
        assertTrue(repository.findDueOrderIds(now, 10_000).none { it == orderId })
    }

    @Test
    fun `a failed routing transaction does not leave a pending request`() {
        val orderId = "rolled-back-${UUID.randomUUID()}"
        assertFailsWith<IllegalStateException> {
            transactionRunner.run {
                repository.insertIfAbsent(record(orderId))
                error("simulated failure after insert")
            }
        }

        assertNull(transactionRunner.run { repository.findForUpdate(orderId) })
    }

    private fun record(orderId: String) = DispatchRequestRecord(
        orderId = orderId,
        passengerReference = "passenger-qa",
        isTest = true,
        explicitDriverIntent = false,
        requestedDriverId = null,
        requestedPickupAt = null,
        submittedAt = now,
        expiresAt = now.plusSeconds(120),
        nextAttemptAt = now
    )
}
