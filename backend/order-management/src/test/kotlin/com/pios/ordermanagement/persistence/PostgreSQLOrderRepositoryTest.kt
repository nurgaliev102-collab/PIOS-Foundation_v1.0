package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.domain.Order
import com.pios.ordermanagement.domain.OrderId
import com.pios.ordermanagement.domain.OrderOrigin
import com.pios.ordermanagement.domain.OrderStatus
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Proves the save/load lifecycle described by [OrderRepository] against
 * a real PostgreSQL database, not merely in memory (PostgreSQL
 * Persistence Order Management v1.0; ADR-025).
 */
class PostgreSQLOrderRepositoryTest {

    private val repository = PostgreSQLOrderRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
    private val origin = OrderOrigin("origin-1")

    @Test
    fun `an order saved to PostgreSQL can be loaded back with its identity and status preserved`() {
        val submitted = Order.submit(origin)

        repository.save(submitted.order)
        val loaded = repository.findById(submitted.order.id)

        assertEquals(submitted.order.id, loaded?.id)
        assertEquals(OrderStatus.SUBMITTED, loaded?.status)
    }

    @Test
    fun `an order saved to PostgreSQL can be loaded back with its origin preserved`() {
        val submitted = Order.submit(origin)

        repository.save(submitted.order)
        val loaded = repository.findById(submitted.order.id)

        assertEquals(origin, loaded?.origin)
    }

    // --- Explicit driver intent (Task 15C: First Refusal Contract Completion and Concurrency Safety) ---

    @Test
    fun `an order saved with explicit driver intent survives a real round trip through PostgreSQL`() {
        val submitted = Order.submit(origin, explicitDriverIntent = true)

        repository.save(submitted.order)
        val loaded = repository.findById(submitted.order.id)

        assertEquals(true, loaded?.explicitDriverIntent)
    }

    @Test
    fun `an order saved with no explicit driver intent loads back false, not null or a default placeholder`() {
        val submitted = Order.submit(origin, explicitDriverIntent = false)

        repository.save(submitted.order)
        val loaded = repository.findById(submitted.order.id)

        assertEquals(false, loaded?.explicitDriverIntent)
    }

    @Test
    fun `explicit driver intent survives a fresh read through a brand new repository instance -- proving real persistence`() {
        val submitted = Order.submit(origin, explicitDriverIntent = true)
        repository.save(submitted.order)

        val freshRepository = PostgreSQLOrderRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))

        assertEquals(true, freshRepository.findById(submitted.order.id)?.explicitDriverIntent)
    }

    @Test
    fun `loading an id that was never saved returns null`() {
        assertNull(repository.findById(OrderId("postgres-repository-test-never-saved")))
    }

    // --- Destination (Sprint 3B: MVR Pilot Enablement -- Optional Destination) ---

    @Test
    fun `an order saved to PostgreSQL with a destination can be loaded back with it preserved`() {
        val submitted = Order.submit(origin, "Аэропорт")

        repository.save(submitted.order)
        val loaded = repository.findById(submitted.order.id)

        assertEquals("Аэропорт", loaded?.destination)
    }

    @Test
    fun `an order saved to PostgreSQL without a destination loads back with a null destination`() {
        val submitted = Order.submit(origin)

        repository.save(submitted.order)
        val loaded = repository.findById(submitted.order.id)

        assertNull(loaded?.destination)
    }

    // --- Pickup address (Sprint H5: Entrepreneur Working Cycle Integrity) ---

    @Test
    fun `an order saved to PostgreSQL with a pickup address can be loaded back with it preserved`() {
        val submitted = Order.submit(origin, pickupAddress = "ул. Ленина, 10")

        repository.save(submitted.order)
        val loaded = repository.findById(submitted.order.id)

        assertEquals("ул. Ленина, 10", loaded?.pickupAddress)
    }

    @Test
    fun `an order saved to PostgreSQL without a pickup address loads back with a null pickup address`() {
        val submitted = Order.submit(origin)

        repository.save(submitted.order)
        val loaded = repository.findById(submitted.order.id)

        assertNull(loaded?.pickupAddress)
    }

    @Test
    fun `saving again after a status change overwrites the previously persisted row`() {
        val submitted = Order.submit(origin)
        repository.save(submitted.order)

        submitted.order.complete()
        repository.save(submitted.order)

        assertEquals(OrderStatus.COMPLETED, repository.findById(submitted.order.id)?.status)
    }

    @Test
    fun `findAll includes an order saved to PostgreSQL`() {
        val submitted = Order.submit(origin)

        repository.save(submitted.order)

        assertTrue(repository.findAll().any { it.id == submitted.order.id && it.origin == origin })
    }

    // --- isTest (Owner Control Center test/production data separation, 2026-08-17) ---

    @Test
    fun `isTest true round-trips through PostgreSQL`() {
        val submitted = Order.submit(origin, isTest = true)

        repository.save(submitted.order)

        assertEquals(true, repository.findById(submitted.order.id)?.isTest)
    }

    @Test
    fun `an order saved without isTest loads back with isTest false`() {
        val submitted = Order.submit(origin)

        repository.save(submitted.order)

        assertEquals(false, repository.findById(submitted.order.id)?.isTest)
    }

    // --- Passenger count (PIOS Group and Long-Distance Rides Roadmap, Stage 2) ---

    @Test
    fun `an order saved to PostgreSQL with a passenger count can be loaded back with it preserved`() {
        val submitted = Order.submit(origin, passengerCount = 4)

        repository.save(submitted.order)
        val loaded = repository.findById(submitted.order.id)

        assertEquals(4, loaded?.passengerCount)
    }

    @Test
    fun `an order saved to PostgreSQL without a passenger count loads back with a null passenger count`() {
        val submitted = Order.submit(origin)

        repository.save(submitted.order)
        val loaded = repository.findById(submitted.order.id)

        assertNull(loaded?.passengerCount)
    }

    @Test
    fun `passenger count survives a fresh read through a brand new repository instance -- proving real persistence`() {
        val submitted = Order.submit(origin, passengerCount = 4)
        repository.save(submitted.order)

        val freshRepository = PostgreSQLOrderRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))

        assertEquals(4, freshRepository.findById(submitted.order.id)?.passengerCount)
    }
}
