package com.pios.identity.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class PostgreSQLPushSubscriptionRepository(
    private val jdbcTemplate: JdbcTemplate
) {
    fun upsert(identityId: String, endpoint: String, p256dhKey: String, authKey: String, userAgent: String?) {
        val now = Instant.now()
        jdbcTemplate.update(
            """
            INSERT INTO push_subscriptions (id, identity_id, endpoint, p256dh_key, auth_key, user_agent, created_at, last_seen_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (endpoint) DO UPDATE SET
                identity_id = EXCLUDED.identity_id,
                p256dh_key = EXCLUDED.p256dh_key,
                auth_key = EXCLUDED.auth_key,
                user_agent = EXCLUDED.user_agent,
                last_seen_at = EXCLUDED.last_seen_at
            """.trimIndent(),
            UUID.randomUUID(), identityId, endpoint, p256dhKey, authKey, userAgent, now, now
        )
    }

    fun delete(identityId: String, endpoint: String) {
        jdbcTemplate.update(
            "DELETE FROM push_subscriptions WHERE identity_id = ? AND endpoint = ?",
            identityId,
            endpoint,
        )
    }
}
