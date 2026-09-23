package com.pios.identity.persistence

import com.pios.identity.application.PhoneVerificationChallengeRepository
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.PhoneVerificationChallenge
import com.pios.identity.domain.PhoneVerificationPurpose
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/**
 * The PostgreSQL-backed adapter for [PhoneVerificationChallengeRepository]
 * (ADR-082, D-03), in this module's own database (`pios_identity`). Plain
 * JDBC via [JdbcTemplate] only, no ORM, mirroring
 * [PostgreSQLIdentityRepository]'s own style.
 *
 * C-2: [save] now persists [PhoneVerificationChallenge.otpCiphertext] and
 * [PhoneVerificationChallenge.otpNonce] in the V6 columns. [findLiveForUpdate]
 * and [findById] read them back so the relay can decrypt the OTP.
 * [update] does NOT change ciphertext/nonce — those are nulled separately
 * by [PostgreSQLSmsOutboxRepository.markSent] in the same atomic transition.
 *
 * [findLiveForUpdate] issues a real `SELECT ... FOR UPDATE`, the exact
 * per-row lock discipline `ADR-080`'s `PostgreSQLOrderGuard` already
 * proved in this codebase — it must be called inside the same
 * [com.pios.identity.application.TransactionRunner] boundary as the
 * subsequent compare/[update], so the row lock is held by the same
 * connection for the whole attempt.
 */
@Repository
class PostgreSQLPhoneVerificationChallengeRepository(
    private val jdbcTemplate: JdbcTemplate
) : PhoneVerificationChallengeRepository {

    override fun save(challenge: PhoneVerificationChallenge) {
        jdbcTemplate.update(
            """
            INSERT INTO phone_verification_challenges
                (id, identity_id, phone, purpose, code_hash, code_salt, iterations,
                 attempt_count, max_attempts, created_at, expires_at, consumed_at,
                 superseded_at, otp_ciphertext, otp_nonce)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            challenge.id,
            challenge.identityId.value,
            challenge.phone.value,
            challenge.purpose.name,
            challenge.codeHash,
            challenge.codeSalt,
            challenge.iterations,
            challenge.attemptCount,
            challenge.maxAttempts,
            Timestamp.from(challenge.createdAt),
            Timestamp.from(challenge.expiresAt),
            challenge.consumedAt?.let(Timestamp::from),
            challenge.supersededAt?.let(Timestamp::from),
            challenge.otpCiphertext,
            challenge.otpNonce
        )
    }

    override fun supersedeLive(identityId: IdentityId, purpose: PhoneVerificationPurpose, at: Instant) {
        jdbcTemplate.update(
            """
            UPDATE phone_verification_challenges
            SET superseded_at = ?
            WHERE identity_id = ? AND purpose = ? AND consumed_at IS NULL AND superseded_at IS NULL
            """.trimIndent(),
            Timestamp.from(at),
            identityId.value,
            purpose.name
        )
    }

    override fun findLiveForUpdate(identityId: IdentityId, purpose: PhoneVerificationPurpose): PhoneVerificationChallenge? {
        val rows = jdbcTemplate.query(
            """
            SELECT id, identity_id, phone, purpose, code_hash, code_salt, iterations,
                   attempt_count, max_attempts, created_at, expires_at, consumed_at,
                   superseded_at, otp_ciphertext, otp_nonce
            FROM phone_verification_challenges
            WHERE identity_id = ? AND purpose = ? AND consumed_at IS NULL AND superseded_at IS NULL
            FOR UPDATE
            """.trimIndent(),
            { rs, _ -> mapRow(rs) },
            identityId.value,
            purpose.name
        )
        return rows.firstOrNull()
    }

    /**
     * Finds a challenge by its primary key. Used by [PostgreSQLSmsOutboxRepository]
     * to read the OTP ciphertext for relay decryption and the phone for SMS delivery.
     */
    fun findById(id: String): PhoneVerificationChallenge? {
        val rows = jdbcTemplate.query(
            """
            SELECT id, identity_id, phone, purpose, code_hash, code_salt, iterations,
                   attempt_count, max_attempts, created_at, expires_at, consumed_at,
                   superseded_at, otp_ciphertext, otp_nonce
            FROM phone_verification_challenges
            WHERE id = ?
            """.trimIndent(),
            { rs, _ -> mapRow(rs) },
            id
        )
        return rows.firstOrNull()
    }

    /**
     * Nulls otp_ciphertext and otp_nonce on the challenge row with [id].
     * Called atomically with the SENT transition in [PostgreSQLSmsOutboxRepository.markSent]
     * (Decision Lock item 11: ciphertext destruction).
     */
    fun nullCiphertext(id: String) {
        jdbcTemplate.update(
            "UPDATE phone_verification_challenges SET otp_ciphertext = NULL, otp_nonce = NULL WHERE id = ?",
            id
        )
    }

    override fun update(challenge: PhoneVerificationChallenge) {
        jdbcTemplate.update(
            """
            UPDATE phone_verification_challenges
            SET attempt_count = ?, consumed_at = ?, superseded_at = ?
            WHERE id = ?
            """.trimIndent(),
            challenge.attemptCount,
            challenge.consumedAt?.let(Timestamp::from),
            challenge.supersededAt?.let(Timestamp::from),
            challenge.id
        )
        // otp_ciphertext and otp_nonce are NOT updated here — they are
        // nulled only by nullCiphertext(), called from markSent() and
        // markFailed() in PostgreSQLSmsOutboxRepository (Decision Lock item 11).
    }

    private fun mapRow(rs: ResultSet): PhoneVerificationChallenge = PhoneVerificationChallenge(
        id = rs.getString("id"),
        identityId = IdentityId(rs.getString("identity_id")),
        phone = com.pios.identity.domain.Phone(rs.getString("phone")),
        purpose = PhoneVerificationPurpose.valueOf(rs.getString("purpose")),
        codeHash = rs.getString("code_hash"),
        codeSalt = rs.getString("code_salt"),
        iterations = rs.getInt("iterations"),
        attemptCount = rs.getInt("attempt_count"),
        maxAttempts = rs.getInt("max_attempts"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        expiresAt = rs.getTimestamp("expires_at").toInstant(),
        consumedAt = rs.getTimestamp("consumed_at")?.toInstant(),
        supersededAt = rs.getTimestamp("superseded_at")?.toInstant(),
        otpCiphertext = rs.getString("otp_ciphertext"),
        otpNonce = rs.getString("otp_nonce")
    )
}
