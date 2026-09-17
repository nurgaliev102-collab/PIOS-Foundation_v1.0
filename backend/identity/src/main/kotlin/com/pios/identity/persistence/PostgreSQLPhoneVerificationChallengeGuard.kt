package com.pios.identity.persistence

import com.pios.identity.application.PhoneVerificationChallengeGuard
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.PhoneVerificationPurpose
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * The real, PostgreSQL-backed [PhoneVerificationChallengeGuard] — insert
 * the guard row if absent, then lock it, mirroring
 * `com.pios.dispatch.persistence.PostgreSQLOrderGuard` exactly (ADR-080).
 */
@Repository
class PostgreSQLPhoneVerificationChallengeGuard(private val jdbc: JdbcTemplate) : PhoneVerificationChallengeGuard {
    override fun lock(identityId: IdentityId, purpose: PhoneVerificationPurpose) {
        val key = "${identityId.value}:${purpose.name}"
        jdbc.update(
            "INSERT INTO phone_verification_challenge_guards (guard_key) VALUES (?) ON CONFLICT DO NOTHING",
            key
        )
        jdbc.queryForObject(
            "SELECT guard_key FROM phone_verification_challenge_guards WHERE guard_key = ? FOR UPDATE",
            String::class.java,
            key
        ) ?: error("Missing phone verification challenge guard for $key")
    }
}
