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
 * A `destination` column was added and then dropped again within Sprint
 * FND-006 (backward-compatibility correction; see
 * `V5__revert_order_destination.sql`). Sprint 3B (MVR Pilot Enablement —
 * Optional Destination) reintroduces it, this time nullable
 * (`V6__add_optional_order_destination.sql`) — read and written below,
 * excluded from `ON CONFLICT ... UPDATE` for the same reason `origin`
 * is: [Order.destination] is set once, at submission, and never changes.
 *
 * [destination] is a plain, nullable `String` (see [Order]'s own KDoc for
 * why it is not a `@JvmInline value class` the way `origin`/`id` are) —
 * its JVM-erased type is already `java.lang.String`, identical to
 * `origin`'s own erased type, so the reflected constructor below simply
 * gains a fourth `String::class.java` parameter; `null` is a valid
 * argument for a reference-typed reflective constructor parameter
 * regardless of the Kotlin-level nullability annotation, and
 * `ResultSet.getString` already returns `null` for a SQL `NULL` column,
 * so no special-casing is needed here.
 *
 * [findAll] (Sprint FR-003, Order Query) reuses the same [reconstruct]
 * helper as [findById], row by row, over every order in the table — no
 * filtering, sorting, or pagination.
 *
 * First-pilot feedback adds `passenger_name`/`created_at`, read and
 * written below exactly like `destination` — excluded from
 * `ON CONFLICT ... UPDATE` since [Order.passengerName]/[Order.createdAt]
 * are likewise set once, at submission, and never change. `created_at` is
 * stored as `TIMESTAMPTZ`; [java.sql.Timestamp.toInstant] converts the
 * driver's own representation to [java.time.Instant], the reflected
 * constructor's declared type for that parameter (a regular class, not a
 * `@JvmInline value class`, so no unboxing subtlety applies the way it
 * does for `origin`/`id`).
 *
 * Sprint H5 (Entrepreneur Working Cycle Integrity) adds `pickup_address`,
 * read and written below exactly like `destination` — excluded from
 * `ON CONFLICT ... UPDATE` since [Order.pickupAddress] is likewise set
 * once, at submission, and never changes. It is [Order]'s own last
 * constructor parameter (declared after `createdAt`), so the reflected
 * constructor below gains a seventh, trailing `String::class.java`
 * parameter, matching [Order]'s own declaration order exactly.
 *
 * ADR-058 (Scheduled Pickup Time) adds `requested_pickup_at`, read and
 * written below exactly like `created_at` — a `TIMESTAMPTZ` column,
 * excluded from `ON CONFLICT ... UPDATE` since [Order.requestedPickupAt]
 * is likewise set once, at submission, and never changes. It is [Order]'s
 * own new last constructor parameter (declared after [Order.pickupAddress]),
 * so the reflected constructor below gains an eighth, trailing
 * `java.time.Instant::class.java` parameter, matching [Order]'s own
 * declaration order exactly.
 *
 * Owner Control Center test/production data separation (2026-08-17) adds
 * `is_test`, excluded from `ON CONFLICT ... UPDATE` since [Order.isTest]
 * is set once, at submission, and never changes, mirroring `origin`'s own
 * treatment. It is [Order]'s own new last constructor parameter (declared
 * after [Order.requestedPickupAt]), a non-nullable `Boolean`, so the
 * reflected constructor below gains a ninth, trailing
 * `Boolean::class.java` parameter — Kotlin represents a non-nullable
 * `Boolean` constructor parameter as the JVM primitive `boolean`, the same
 * unboxed representation `Boolean::class.java` resolves to, so no
 * boxed-type mismatch applies here the way it would for a nullable one.
 *
 * Task 15C (First Refusal Contract Completion and Concurrency Safety)
 * adds `explicit_driver_intent`, read and written below exactly like
 * `is_test` — excluded from `ON CONFLICT ... UPDATE` since
 * [Order.explicitDriverIntent] is likewise set once, at submission, and
 * never changes. It is [Order]'s own new last constructor parameter
 * (declared after [Order.isTest]), a non-nullable `Boolean`, so the
 * reflected constructor below gains a tenth, trailing
 * `Boolean::class.java` parameter, for the identical unboxing reason
 * `is_test`'s own paragraph above already explains.
 *
 * PIOS Group and Long-Distance Rides Roadmap, Stage 2 adds
 * `passenger_count`, excluded from `ON CONFLICT ... UPDATE` since
 * [Order.passengerCount] is likewise set once, at submission, and never
 * changes. It is [Order]'s own new last constructor parameter (declared
 * after [Order.explicitDriverIntent]) — unlike `is_test`/`explicit_driver_intent`,
 * this one is a **nullable** `Int?`, which, unlike ADR-066's own
 * `PassengerReference?` lesson on Dispatch's `Proposal` (a value class
 * wrapping `String`, which does not box since `String` already represents
 * `null`), genuinely does box here: `Int` is a primitive with no `null`
 * representation of its own, so a nullable `Int?` constructor parameter's
 * real JVM type is `java.lang.Integer`, not the primitive `int`. The
 * reflected constructor below therefore gains an eleventh, trailing
 * `Integer::class.java` parameter — confirmed empirically against the
 * compiled class (`javap`) before this repository's own tests were run,
 * not merely asserted from the general rule.
 *
 * Product Cycle (Passenger Ride Requirements) adds `notes`, excluded from
 * `ON CONFLICT ... UPDATE` since [Order.notes] is likewise set once, at
 * submission, and never changes. It is [Order]'s own new last constructor
 * parameter (declared after [Order.passengerCount]) -- a plain, nullable
 * `String`, identical in erased JVM type to `destination`/`pickup_address`
 * (no boxing subtlety the way the preceding `passenger_count: Int?`
 * parameter has), so the reflected constructor below simply gains a
 * twelfth, trailing `String::class.java` parameter.
 */
@Repository
class PostgreSQLOrderRepository(
    private val jdbcTemplate: JdbcTemplate
) : OrderRepository {

    override fun save(order: Order) {
        jdbcTemplate.update(
            """
            INSERT INTO orders (id, status, origin, destination, passenger_name, created_at, pickup_address, requested_pickup_at, is_test, explicit_driver_intent, passenger_count, notes, requested_driver_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status
            """.trimIndent(),
            order.id.value,
            order.status.name,
            order.origin.reference,
            order.destination,
            order.passengerName,
            order.createdAt?.let { java.sql.Timestamp.from(it) },
            order.pickupAddress,
            order.requestedPickupAt?.let { java.sql.Timestamp.from(it) },
            order.isTest,
            order.explicitDriverIntent,
            order.passengerCount,
            order.notes,
            order.requestedDriverId
        )
    }

    override fun findById(id: OrderId): Order? = findById(id, forUpdate = false)

    override fun findByIdForUpdate(id: OrderId): Order? = findById(id, forUpdate = true)

    private fun findById(id: OrderId, forUpdate: Boolean): Order? {
        val rows = jdbcTemplate.query(
            "SELECT id, status, origin, destination, passenger_name, created_at, pickup_address, requested_pickup_at, is_test, explicit_driver_intent, passenger_count, notes, requested_driver_id FROM orders WHERE id = ?" +
                if (forUpdate) " FOR UPDATE" else "",
            { rs, _ ->
                reconstruct(
                    id = rs.getString("id"),
                    status = OrderStatus.valueOf(rs.getString("status")),
                    origin = rs.getString("origin"),
                    destination = rs.getString("destination"),
                    passengerName = rs.getString("passenger_name"),
                    createdAt = rs.getTimestamp("created_at"),
                    pickupAddress = rs.getString("pickup_address"),
                    requestedPickupAt = rs.getTimestamp("requested_pickup_at"),
                    isTest = rs.getBoolean("is_test"),
                    explicitDriverIntent = rs.getBoolean("explicit_driver_intent"),
                    passengerCount = rs.getInt("passenger_count").let { if (rs.wasNull()) null else it },
                    notes = rs.getString("notes"),
                    requestedDriverId = rs.getString("requested_driver_id")
                )
            },
            id.value
        )
        return rows.firstOrNull()
    }

    override fun findAll(): List<Order> =
        jdbcTemplate.query(
            "SELECT id, status, origin, destination, passenger_name, created_at, pickup_address, requested_pickup_at, is_test, explicit_driver_intent, passenger_count, notes, requested_driver_id FROM orders"
        ) { rs, _ ->
            reconstruct(
                id = rs.getString("id"),
                status = OrderStatus.valueOf(rs.getString("status")),
                origin = rs.getString("origin"),
                destination = rs.getString("destination"),
                passengerName = rs.getString("passenger_name"),
                createdAt = rs.getTimestamp("created_at"),
                pickupAddress = rs.getString("pickup_address"),
                requestedPickupAt = rs.getTimestamp("requested_pickup_at"),
                isTest = rs.getBoolean("is_test"),
                explicitDriverIntent = rs.getBoolean("explicit_driver_intent"),
                passengerCount = rs.getInt("passenger_count").let { if (rs.wasNull()) null else it },
                notes = rs.getString("notes"),
                requestedDriverId = rs.getString("requested_driver_id")
            )
        }

    private fun reconstruct(
        id: String,
        status: OrderStatus,
        origin: String,
        destination: String?,
        passengerName: String?,
        createdAt: java.sql.Timestamp?,
        pickupAddress: String?,
        requestedPickupAt: java.sql.Timestamp?,
        isTest: Boolean,
        explicitDriverIntent: Boolean,
        passengerCount: Int?,
        notes: String?,
        requestedDriverId: String?
    ): Order {
        val constructor = Order::class.java.getDeclaredConstructor(
            String::class.java,
            OrderStatus::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            java.time.Instant::class.java,
            String::class.java,
            java.time.Instant::class.java,
            Boolean::class.java,
            Boolean::class.java,
            Integer::class.java,
            String::class.java,
            String::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            id,
            status,
            origin,
            destination,
            passengerName,
            createdAt?.toInstant(),
            pickupAddress,
            requestedPickupAt?.toInstant(),
            isTest,
            explicitDriverIntent,
            passengerCount,
            notes,
            requestedDriverId
        )
    }
}
