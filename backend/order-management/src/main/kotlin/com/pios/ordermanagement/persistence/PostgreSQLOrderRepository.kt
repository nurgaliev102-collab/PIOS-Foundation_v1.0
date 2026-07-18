package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.application.OrderRepository
import com.pios.ordermanagement.domain.Order
import com.pios.ordermanagement.domain.OrderId
import com.pios.ordermanagement.domain.OrderStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [OrderRepository] (ADR-025;
 * PERSISTENCE_ARCHITECTURE.md Section 3, "Order Management"). Persists
 * exactly the Order logical entity this module owns, in its own
 * database (`pios_order_management`) — no other module's information,
 * no shared schema. Schema is version-controlled and applied exclusively
 * through Flyway migrations (`db/migration`), never by this class.
 *
 * Uses plain JDBC via Spring's [JdbcTemplate] only — no ORM, no entity
 * annotations, no framework-specific mapping. [Order] itself carries no
 * persistence knowledge whatsoever; this class owns database mapping and
 * storage operations exclusively, exactly as [OrderRepository]'s own
 * KDoc requires.
 *
 * ## Reconstruction and the private constructor
 *
 * [Order]'s constructor is private, reachable only through
 * [Order.submit] — which always generates a fresh random id and starts
 * an order at [OrderStatus.SUBMITTED]. That is correct for the domain's
 * own creation use case, but a real database adapter must also
 * reconstruct an [Order] already carrying a *specific, previously
 * persisted* id and status (including [OrderStatus.COMPLETED] and
 * [OrderStatus.CANCELLED]) when [findById] is called — something no
 * public domain API can do, and which this task's own rules correctly
 * forbid solving by changing the domain package.
 *
 * The only technique that reconstructs the aggregate without touching
 * `Order.kt` is reflection into its private constructor, confined
 * entirely to this persistence-layer class. This is the same technique
 * real object-relational mapping tools use internally to populate
 * entities with private or protected constructors — the difference here
 * is that no ORM framework is introduced; a single, explicit
 * `java.lang.reflect.Constructor` call is used directly, is documented,
 * and touches nothing beyond this one file. It does not bypass any
 * invariant: the private constructor itself performs no validation
 * beyond what already-persisted, previously-valid data satisfies by
 * construction.
 *
 * `OrderId` is a `@JvmInline value class`. Kotlin represents it at the
 * JVM level using its own unboxed underlying `String` in constructor
 * position (constructors are not name-mangled the way regular functions
 * are), so the reflectively-invoked constructor's declared parameter
 * type is `String`, not `OrderId` — confirmed empirically against the
 * compiled class before writing this adapter.
 */
@Repository
class PostgreSQLOrderRepository(
    private val jdbcTemplate: JdbcTemplate
) : OrderRepository {

    override fun save(order: Order) {
        jdbcTemplate.update(
            """
            INSERT INTO orders (id, status) VALUES (?, ?)
            ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status
            """.trimIndent(),
            order.id.value,
            order.status.name
        )
    }

    override fun findById(id: OrderId): Order? {
        val rows = jdbcTemplate.query(
            "SELECT id, status FROM orders WHERE id = ?",
            { rs, _ -> reconstruct(rs.getString("id"), OrderStatus.valueOf(rs.getString("status"))) },
            id.value
        )
        return rows.firstOrNull()
    }

    private fun reconstruct(id: String, status: OrderStatus): Order {
        val constructor = Order::class.java.getDeclaredConstructor(String::class.java, OrderStatus::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(id, status)
    }
}
