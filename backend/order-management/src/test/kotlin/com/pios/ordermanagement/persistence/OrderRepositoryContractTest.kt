package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.application.OrderRepository
import com.pios.ordermanagement.domain.Order
import com.pios.ordermanagement.domain.OrderId
import com.pios.ordermanagement.domain.OrderStatus
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A shared behavioral contract every [OrderRepository] implementation
 * must satisfy, run identically against [InMemoryOrderRepository] and
 * [PostgreSQLOrderRepository] (PostgreSQL Persistence Order Management
 * v1.0). Proves [com.pios.ordermanagement.application.OrderLifecycleApplicationService]
 * can depend on either without any change to its own code
 * (APPLICATION_ARCHITECTURE.md Section 2) — the repository interface is
 * unchanged and both adapters honor it identically.
 */
abstract class OrderRepositoryContractTest {

    abstract fun createRepository(): OrderRepository

    @Test
    fun `a saved order can be found by its id with its status intact`() {
        val repository = createRepository()
        val submitted = Order.submit()

        repository.save(submitted.order)

        assertEquals(OrderStatus.SUBMITTED, repository.findById(submitted.order.id)?.status)
    }

    @Test
    fun `an id that was never saved returns null rather than throwing`() {
        val repository = createRepository()

        assertNull(repository.findById(OrderId("order-repository-contract-test-never-saved")))
    }

    @Test
    fun `saving the same order id again overwrites its previously persisted status`() {
        val repository = createRepository()
        val submitted = Order.submit()
        repository.save(submitted.order)

        submitted.order.complete()
        repository.save(submitted.order)

        assertEquals(OrderStatus.COMPLETED, repository.findById(submitted.order.id)?.status)
    }
}

class InMemoryOrderRepositoryContractTest : OrderRepositoryContractTest() {
    override fun createRepository(): OrderRepository = InMemoryOrderRepository()
}

class PostgreSQLOrderRepositoryContractTest : OrderRepositoryContractTest() {
    override fun createRepository(): OrderRepository =
        PostgreSQLOrderRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
}
