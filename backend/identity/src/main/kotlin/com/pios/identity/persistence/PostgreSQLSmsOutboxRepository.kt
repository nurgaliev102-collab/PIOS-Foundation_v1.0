package com.pios.identity.persistence

import com.pios.identity.application.SmsOutboxRecord
import com.pios.identity.application.SmsDispatchAuthorization
import com.pios.identity.application.SmsOutboxRepository
import com.pios.identity.application.SmsOutboxStatus
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.Phone
import com.pios.identity.domain.PhoneVerificationChallenge
import com.pios.identity.domain.PhoneVerificationPurpose
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** PostgreSQL persistence for the durable C-2 SMS relay outbox. */
@Repository
class PostgreSQLSmsOutboxRepository(
    private val jdbcTemplate: JdbcTemplate
) : SmsOutboxRepository {

    override fun insert(record: SmsOutboxRecord) {
        jdbcTemplate.update(
            """
            INSERT INTO identity_sms_outbox
                (challenge_id, status, lease_until, claim_token, next_attempt_at,
                 provider_message_id, sent_at, failure_reason, retry_count, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            record.challengeId, record.status.name, record.leaseUntil?.let(Timestamp::from), record.claimToken,
            Timestamp.from(record.nextAttemptAt), record.providerMessageId, record.sentAt?.let(Timestamp::from),
            record.failureReason, record.retryCount, Timestamp.from(record.createdAt), Timestamp.from(record.updatedAt)
        )
    }

    override fun claimBatch(batchSize: Int, leaseDuration: Duration): List<SmsOutboxRecord> {
        require(batchSize > 0) { "batchSize must be positive" }
        require(!leaseDuration.isNegative && !leaseDuration.isZero) { "leaseDuration must be positive" }
        val token = UUID.randomUUID().toString()
        return jdbcTemplate.query(
            """
            WITH candidates AS (
                SELECT id
                FROM identity_sms_outbox
                WHERE (status = 'PENDING' AND next_attempt_at <= now())
                   OR (status = 'PROCESSING' AND lease_until < now())
                ORDER BY created_at ASC
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            )
            , claimed AS (
                UPDATE identity_sms_outbox o
                SET status = 'PROCESSING',
                    lease_until = now() + (? * INTERVAL '1 millisecond'),
                    claim_token = ?,
                    updated_at = now()
                FROM candidates c
                WHERE o.id = c.id
                RETURNING o.*
            )
            SELECT * FROM claimed ORDER BY created_at ASC
            """.trimIndent(),
            { rs, _ -> mapOutbox(rs) },
            batchSize, leaseDuration.toMillis(), token
        )
    }

    /**
     * The durable dispatch-authorization linearization point. The CTE locks
     * both the fenced outbox row and its challenge, evaluates liveness while
     * those locks are held, and renews the lease before returning the
     * challenge snapshot. A concurrent confirm/supersession therefore either
     * commits before this statement (authorization is denied) or after it
     * (the external submission was already authorized). No lock is held for
     * the subsequent network call.
     */
    override fun authorizeDispatch(recordId: Long, claimToken: String, leaseDuration: Duration): SmsDispatchAuthorization? {
        require(!leaseDuration.isNegative && !leaseDuration.isZero) { "leaseDuration must be positive" }
        val dispatchToken = UUID.randomUUID().toString()
        return jdbcTemplate.query(
            """
            WITH owned AS MATERIALIZED (
                SELECT o.id AS outbox_id,
                       c.id AS challenge_id, c.identity_id, c.phone, c.purpose,
                       c.code_hash, c.code_salt, c.iterations, c.attempt_count,
                       c.max_attempts, c.created_at, c.expires_at, c.consumed_at,
                       c.superseded_at, c.otp_ciphertext, c.otp_nonce,
                       (c.consumed_at IS NULL
                        AND c.superseded_at IS NULL
                        AND c.expires_at > now()
                        AND c.attempt_count < c.max_attempts
                        AND c.otp_ciphertext IS NOT NULL
                        AND c.otp_nonce IS NOT NULL) AS dispatchable
                FROM identity_sms_outbox o
                JOIN phone_verification_challenges c ON c.id = o.challenge_id
                WHERE o.id = ?
                  AND o.status = 'PROCESSING'
                  AND o.claim_token = ?
                  AND o.lease_until > now()
                FOR UPDATE OF o, c
            ), rejected AS (
                UPDATE identity_sms_outbox o
                SET status = 'FAILED', failure_reason = 'CHALLENGE_NOT_SENDABLE',
                    lease_until = NULL, claim_token = NULL, updated_at = now()
                FROM owned x
                WHERE o.id = x.outbox_id AND NOT x.dispatchable
                RETURNING x.challenge_id
            ), cleared AS (
                UPDATE phone_verification_challenges c
                SET otp_ciphertext = NULL, otp_nonce = NULL
                FROM rejected r
                WHERE c.id = r.challenge_id
            ), authorized AS (
                UPDATE identity_sms_outbox o
                SET lease_until = now() + (? * INTERVAL '1 millisecond'), claim_token = ?, updated_at = now()
                FROM owned x
                WHERE o.id = x.outbox_id AND x.dispatchable
                RETURNING o.id, o.lease_until, o.claim_token
            )
            SELECT x.challenge_id, x.identity_id, x.phone, x.purpose,
                   x.code_hash, x.code_salt, x.iterations, x.attempt_count,
                   x.max_attempts, x.created_at, x.expires_at, x.consumed_at,
                   x.superseded_at, x.otp_ciphertext, x.otp_nonce,
                   a.lease_until, a.claim_token
            FROM owned x
            JOIN authorized a ON a.id = x.outbox_id
            """.trimIndent(),
            { rs, _ ->
                SmsDispatchAuthorization(
                    challenge = mapAuthorizedChallenge(rs),
                    leaseUntil = rs.getTimestamp("lease_until").toInstant(),
                    claimToken = rs.getString("claim_token")
                )
            },
            recordId, claimToken, leaseDuration.toMillis(), dispatchToken
        ).firstOrNull()
    }

    override fun markSent(recordId: Long, claimToken: String, providerMessageId: Long, at: Instant): Boolean =
        jdbcTemplate.update(
            """
            WITH transitioned AS (
                UPDATE identity_sms_outbox
                SET status = 'SENT', provider_message_id = ?, sent_at = ?,
                    lease_until = NULL, claim_token = NULL, updated_at = ?
                WHERE id = ? AND status = 'PROCESSING' AND claim_token = ? AND lease_until > now()
                RETURNING challenge_id
            )
            UPDATE phone_verification_challenges c
            SET otp_ciphertext = NULL, otp_nonce = NULL
            FROM transitioned t
            WHERE c.id = t.challenge_id
            """.trimIndent(),
            providerMessageId, Timestamp.from(at), Timestamp.from(at), recordId, claimToken
        ) == 1

    override fun markFailed(recordId: Long, claimToken: String, failureReason: String, at: Instant): Boolean =
        jdbcTemplate.update(
            """
            WITH transitioned AS (
                UPDATE identity_sms_outbox
                SET status = 'FAILED', failure_reason = ?, lease_until = NULL,
                    claim_token = NULL, updated_at = ?
                WHERE id = ? AND status = 'PROCESSING' AND claim_token = ? AND lease_until > now()
                RETURNING challenge_id
            )
            UPDATE phone_verification_challenges c
            SET otp_ciphertext = NULL, otp_nonce = NULL
            FROM transitioned t
            WHERE c.id = t.challenge_id
            """.trimIndent(),
            failureReason, Timestamp.from(at), recordId, claimToken
        ) == 1

    override fun markUnknownForRetry(
        recordId: Long,
        claimToken: String,
        failureReason: String,
        retryCount: Int,
        retryAfter: Instant,
        at: Instant
    ): Boolean = jdbcTemplate.update(
        """
        UPDATE identity_sms_outbox
        SET status = 'PENDING', failure_reason = ?, retry_count = ?, next_attempt_at = ?,
            lease_until = NULL, claim_token = NULL, updated_at = ?
        WHERE id = ? AND status = 'PROCESSING' AND claim_token = ? AND lease_until > now()
        """.trimIndent(),
        failureReason, retryCount, Timestamp.from(retryAfter), Timestamp.from(at), recordId, claimToken
    ) == 1

    override fun markUnknownTerminal(recordId: Long, claimToken: String, failureReason: String, at: Instant): Boolean =
        jdbcTemplate.update(
            """
            UPDATE identity_sms_outbox
            SET status = 'UNKNOWN', failure_reason = ?, lease_until = NULL,
                claim_token = NULL, updated_at = ?
            WHERE id = ? AND status = 'PROCESSING' AND claim_token = ? AND lease_until > now()
            """.trimIndent(),
            failureReason, Timestamp.from(at), recordId, claimToken
        ) == 1

    override fun cancelPendingForSuperseded(identityId: String, purpose: String, at: Instant) {
        jdbcTemplate.update(
            """
            UPDATE identity_sms_outbox o
            SET status = 'FAILED', failure_reason = 'SUPERSEDED', updated_at = ?
            FROM phone_verification_challenges c
            WHERE o.challenge_id = c.id
              AND o.status = 'PENDING'
              AND c.identity_id = ? AND c.purpose = ? AND c.superseded_at IS NOT NULL
            """.trimIndent(),
            Timestamp.from(at), identityId, purpose
        )
    }

    override fun deleteByRetentionPolicy(sentOlderThan: Instant, otherOlderThan: Instant) {
        jdbcTemplate.update(
            "DELETE FROM identity_sms_outbox WHERE status = 'SENT' AND sent_at < ?",
            Timestamp.from(sentOlderThan)
        )
        jdbcTemplate.update(
            "DELETE FROM identity_sms_outbox WHERE status IN ('FAILED', 'UNKNOWN') AND updated_at < ?",
            Timestamp.from(otherOlderThan)
        )
        jdbcTemplate.update(
            """
            DELETE FROM identity_sms_outbox o
            USING phone_verification_challenges c
            WHERE o.challenge_id = c.id AND o.status = 'PENDING' AND c.expires_at < ?
            """.trimIndent(),
            Timestamp.from(otherOlderThan)
        )
    }

    override fun findByChallengeId(challengeId: String): SmsOutboxRecord? = jdbcTemplate.query(
        "SELECT * FROM identity_sms_outbox WHERE challenge_id = ? ORDER BY id DESC LIMIT 1",
        { rs, _ -> mapOutbox(rs) }, challengeId
    ).firstOrNull()

    override fun findChallengeById(challengeId: String): PhoneVerificationChallenge? = jdbcTemplate.query(
        """
        SELECT id, identity_id, phone, purpose, code_hash, code_salt, iterations,
               attempt_count, max_attempts, created_at, expires_at, consumed_at,
               superseded_at, otp_ciphertext, otp_nonce
        FROM phone_verification_challenges WHERE id = ?
        """.trimIndent(),
        { rs, _ -> mapChallenge(rs) }, challengeId
    ).firstOrNull()

    private fun mapOutbox(rs: ResultSet) = SmsOutboxRecord(
        id = rs.getLong("id"), challengeId = rs.getString("challenge_id"),
        status = SmsOutboxStatus.valueOf(rs.getString("status")),
        leaseUntil = rs.getTimestamp("lease_until")?.toInstant(), claimToken = rs.getString("claim_token"),
        nextAttemptAt = rs.getTimestamp("next_attempt_at").toInstant(),
        providerMessageId = rs.getLong("provider_message_id").takeUnless { rs.wasNull() },
        sentAt = rs.getTimestamp("sent_at")?.toInstant(), failureReason = rs.getString("failure_reason"),
        retryCount = rs.getInt("retry_count"), createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant()
    )

    private fun mapChallenge(rs: ResultSet) = PhoneVerificationChallenge(
        id = rs.getString("id"), identityId = IdentityId(rs.getString("identity_id")),
        phone = Phone(rs.getString("phone")), purpose = PhoneVerificationPurpose.valueOf(rs.getString("purpose")),
        codeHash = rs.getString("code_hash"), codeSalt = rs.getString("code_salt"), iterations = rs.getInt("iterations"),
        attemptCount = rs.getInt("attempt_count"), maxAttempts = rs.getInt("max_attempts"),
        createdAt = rs.getTimestamp("created_at").toInstant(), expiresAt = rs.getTimestamp("expires_at").toInstant(),
        consumedAt = rs.getTimestamp("consumed_at")?.toInstant(), supersededAt = rs.getTimestamp("superseded_at")?.toInstant(),
        otpCiphertext = rs.getString("otp_ciphertext"), otpNonce = rs.getString("otp_nonce")
    )

    private fun mapAuthorizedChallenge(rs: ResultSet) = PhoneVerificationChallenge(
        id = rs.getString("challenge_id"), identityId = IdentityId(rs.getString("identity_id")),
        phone = Phone(rs.getString("phone")), purpose = PhoneVerificationPurpose.valueOf(rs.getString("purpose")),
        codeHash = rs.getString("code_hash"), codeSalt = rs.getString("code_salt"), iterations = rs.getInt("iterations"),
        attemptCount = rs.getInt("attempt_count"), maxAttempts = rs.getInt("max_attempts"),
        createdAt = rs.getTimestamp("created_at").toInstant(), expiresAt = rs.getTimestamp("expires_at").toInstant(),
        consumedAt = rs.getTimestamp("consumed_at")?.toInstant(), supersededAt = rs.getTimestamp("superseded_at")?.toInstant(),
        otpCiphertext = rs.getString("otp_ciphertext"), otpNonce = rs.getString("otp_nonce")
    )
}
