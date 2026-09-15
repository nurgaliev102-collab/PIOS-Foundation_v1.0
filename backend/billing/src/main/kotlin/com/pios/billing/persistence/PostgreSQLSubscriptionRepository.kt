package com.pios.billing.persistence

import com.pios.billing.application.SubscriptionRepository
import com.pios.billing.domain.DriverReference
import com.pios.billing.domain.Subscription
import com.pios.billing.domain.SubscriptionStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp

/**
 * The PostgreSQL-backed adapter for [SubscriptionRepository] (ADR-074 Part
 * 1). Persists exactly the Subscription logical entity this module owns,
 * in its own database (`pios_billing`) -- no other module's information,
 * no shared schema, no foreign key into `driver-management`. Schema is
 * version-controlled and applied exclusively through Flyway migrations
 * (`db/migration/billing`), never by this class.
 *
 * Uses plain JDBC via Spring's [JdbcTemplate] only -- no ORM, no entity
 * annotations, matching every other module's persistence style.
 * [Subscription] itself carries no persistence knowledge whatsoever; this
 * class owns database mapping and storage operations exclusively.
 *
 * `driver_id` is the table's primary key -- exactly one current
 * subscription record per driver (ADR-074 Part 2) -- so `save` is an
 * upsert, matching `PostgreSQLDriverRepository`'s own
 * `INSERT ... ON CONFLICT DO UPDATE` convention. `is_test` and
 * `created_at` are never updated on conflict: both are set once, at
 * [Subscription.start] time, and are immutable for the aggregate's
 * lifetime (see that class's own KDoc).
 */
@Repository
class PostgreSQLSubscriptionRepository(
    private val jdbcTemplate: JdbcTemplate
) : SubscriptionRepository {

    private val selectColumns = "driver_id, status, current_period_end, is_test, created_at, updated_at"

    override fun findByDriverId(driverId: DriverReference): Subscription? {
        val rows = jdbcTemplate.query(
            "SELECT $selectColumns FROM subscriptions WHERE driver_id = ?",
            { rs, _ -> reconstruct(rs) },
            driverId.driverId
        )
        return rows.firstOrNull()
    }

    override fun save(subscription: Subscription) {
        jdbcTemplate.update(
            """
            INSERT INTO subscriptions (driver_id, status, current_period_end, is_test, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (driver_id) DO UPDATE SET
                status = EXCLUDED.status,
                current_period_end = EXCLUDED.current_period_end,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
            subscription.driverId.driverId,
            subscription.status.name,
            subscription.currentPeriodEnd?.let { Timestamp.from(it) },
            subscription.isTest,
            Timestamp.from(subscription.createdAt),
            Timestamp.from(subscription.updatedAt)
        )
    }

    private fun reconstruct(rs: ResultSet): Subscription = Subscription.reconstruct(
        driverId = DriverReference(rs.getString("driver_id")),
        status = SubscriptionStatus.valueOf(rs.getString("status")),
        currentPeriodEnd = rs.getTimestamp("current_period_end")?.toInstant(),
        isTest = rs.getBoolean("is_test"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant()
    )
}
