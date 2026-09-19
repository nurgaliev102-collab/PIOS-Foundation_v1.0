package com.pios.dispatch.persistence

import com.pios.dispatch.application.DriverPushSubscription
import com.pios.dispatch.application.DriverPushSubscriptionRepository
import com.pios.dispatch.domain.DriverReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The PostgreSQL-backed adapter for [DriverPushSubscriptionRepository]
 * (ADR-083, D-10), mirroring [PostgreSQLDriverAvailabilityRepository]'s own
 * plain-JDBC style exactly -- no ORM, no entity annotations. `endpoint` is
 * the table's own primary key (`V25__driver_push_subscriptions.sql`), so
 * [upsert] is a plain `ON CONFLICT (endpoint) DO UPDATE` -- a
 * re-registration under a different driver correctly reassigns the row
 * (ADR-083 Part 6).
 */
@Repository
class PostgreSQLDriverPushSubscriptionRepository(
    private val jdbcTemplate: JdbcTemplate
) : DriverPushSubscriptionRepository {

    override fun upsert(subscription: DriverPushSubscription) {
        jdbcTemplate.update(
            """
            INSERT INTO driver_push_subscriptions (endpoint, driver_reference, p256dh, auth)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (endpoint) DO UPDATE SET
                driver_reference = EXCLUDED.driver_reference,
                p256dh = EXCLUDED.p256dh,
                auth = EXCLUDED.auth,
                updated_at = now()
            """.trimIndent(),
            subscription.endpoint,
            subscription.driverReference.driverId,
            subscription.p256dh,
            subscription.auth
        )
    }

    override fun findByDriver(driver: DriverReference): List<DriverPushSubscription> =
        jdbcTemplate.query(
            "SELECT endpoint, driver_reference, p256dh, auth FROM driver_push_subscriptions WHERE driver_reference = ?",
            { rs, _ ->
                DriverPushSubscription(
                    endpoint = rs.getString("endpoint"),
                    driverReference = DriverReference(rs.getString("driver_reference")),
                    p256dh = rs.getString("p256dh"),
                    auth = rs.getString("auth")
                )
            },
            driver.driverId
        )

    override fun deleteByDriverAndEndpoint(driver: DriverReference, endpoint: String) {
        jdbcTemplate.update(
            "DELETE FROM driver_push_subscriptions WHERE endpoint = ? AND driver_reference = ?",
            endpoint,
            driver.driverId
        )
    }

    override fun deleteByEndpoint(endpoint: String) {
        jdbcTemplate.update("DELETE FROM driver_push_subscriptions WHERE endpoint = ?", endpoint)
    }
}
