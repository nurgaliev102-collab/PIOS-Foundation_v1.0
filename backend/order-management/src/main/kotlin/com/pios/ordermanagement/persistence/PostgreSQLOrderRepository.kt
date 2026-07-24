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
 * compiled class before writing this adapter. Sprint FND-006 (Minimal
 * Order Model) adds [Order]'s `origin` constructor parameter, likewise a
 * `@JvmInline value class` wrapper around a single `String`; the same
 * unboxing/no-mangling behavior applies to it for the same reason, so
 * the reflected constructor's three parameter types are
 * `(String, OrderStatus, String)`, in that declared order — matching
 * [Order]'s own primary constructor exactly. This must be re-verified
 * against a real compiled build before this change is merged (no local
 * JDK was available to compile-check it while writing this adapter —
 * see this sprint's own report).
 *
 * `origin` is never included in the `ON CONFLICT ... UPDATE` clause
 * below: it is set once, at submission, and [Order] itself exposes no
 * way to change it afterward (see that class's own KDoc) — only `status`
 * is ever legitimately re-saved after the initial insert.
 *
 * A `destination` column was added and then dropped again within this
 * same sprint (backward-compatibility correction; see
 * `V5__revert_order_destination.sql`) — this class no longer reads or
 * writes it.
 *
 * [findAll] (Sprint FR-003, Order Query) reuses the same [reconstruct]
 * helper as [findById], row by row, over every order in the table — no
 * filtering, sorting, or pagination.
 */
@Repository
class PostgreSQLOrderRepository(
    private val jdbcTemplate: JdbcTemplate
) : OrderRepository {

    override fun save(order: Order) {
        jdbcTemplate.update(
            """
            INSERT INTO orders (id, status, origin) VALUES (?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status
            """.trimIndent(),
            order.id.value,
            order.status.name,
            order.origin.reference
        )
    }

    override fun findById(id: OrderId): Order? {
        val rows = jdbcTemplate.query(
            "SELECT id, status, origin FROM orders WHERE id = ?",
            { rs, _ ->
                reconstruct(
                    id = rs.getString("id"),
                    status = OrderStatus.valueOf(rs.getString("status")),
                    origin = rs.getString("origin")
                )
            },
            id.value
        )
        return rows.firstOrNull()
    }

    override fun findAll(): List<Order> =
        jdbcTemplate.query(
            "SELECT id, status, origin FROM orders"
        ) { rs, _ ->
            reconstruct(
                id = rs.getString("id"),
                status = OrderStatus.valueOf(rs.getString("status")),
                origin = rs.getString("origin")
            )
        }

    private fun reconstruct(id: String, status: OrderStatus, origin: String): Order {
        val constructor = Order::class.java.getDeclaredConstructor(
            String::class.java,
            OrderStatus::class.java,
            String::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(id, status, origin)
    }
}
