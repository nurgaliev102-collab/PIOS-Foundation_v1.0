package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.domain.Order
import com.pios.ordermanagement.domain.OrderId
import com.pios.ordermanagement.domain.OrderStatus
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Proves the save/load lifecycle described by [OrderRepository] against
 * a real PostgreSQL database, not merely in memory (PostgreSQL
 * Persistence Order Management v1.0; ADR-025).
 */
class PostgreSQLOrderRepositoryTest {

    private val repository = PostgreSQLOrderRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))

    @Test
    fun `an order saved to PostgreSQL can be loaded back with its identity and status preserved`() {
        val submitted = Order.submit()

        repository.save(submitted.order)
        val loaded = repository.findById(submitted.order.id)

        assertEquals(submitted.order.id, loaded?.id)
        assertEquals(OrderStatus.SUBMITTED, loaded?.status)
    }

    @Test
    fun `loading an id that was never saved returns null`() {
        assertNull(repository.findById(OrderId("postgres-repository-test-never-saved")))
    }

    @Test
    fun `saving again after a status change overwrites the previously persisted row`() {
        val submitted = Order.submit()
        repository.save(submitted.order)

        submitted.order.complete()
        repository.save(submitted.order)

        assertEquals(OrderStatus.COMPLETED, repository.findById(submitted.order.id)?.status)
    }
}
