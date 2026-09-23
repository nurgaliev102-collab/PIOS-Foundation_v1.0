package com.pios.identity.application

import com.pios.identity.domain.PhoneVerificationChallenge
import java.time.Instant

/**
 * Persistence boundary for [SmsOutboxRecord] (C-2 Decision Lock I.5).
 * Plain JDBC adapter in [com.pios.identity.persistence.PostgreSQLSmsOutboxRepository];
 * in-memory adapter in [com.pios.identity.persistence.InMemorySmsOutboxRepository]
 * for unit tests.
 */
interface SmsOutboxRepository {

    /**
     * Inserts a new PENDING outbox row. Must be called inside the same
     * transaction as the challenge INSERT in [PhoneVerificationChallengeIssuer]
     * so the two writes are atomic: either both commit or both roll back.
     */
    fun insert(record: SmsOutboxRecord)

    /**
     * Atomically claims a batch of up to [batchSize] reclaimable rows:
     * - status = 'PENDING', OR
     * - status = 'PROCESSING' AND lease_until < now() (expired lease recovery)
     *
     * Each claimed row transitions to PROCESSING with
     * `lease_until = now() + leaseDuration` and is returned.
     * Uses `FOR UPDATE SKIP LOCKED` to prevent concurrent relay instances
     * from claiming the same row (Decision Lock I.5).
     */
    fun claimBatch(batchSize: Int, leaseDuration: java.time.Duration): List<SmsOutboxRecord>

    /**
     * Establishes the relay's last possible authorization point before an
     * external submission. The implementation must atomically:
     *
     * - prove the row is still PROCESSING, fenced by [claimToken], and leased;
     * - lock and verify the linked challenge is live, unexpired, and has
     *   attempts remaining; and
     * - refresh the same fenced lease for [leaseDuration].
     *
     * A null result means either ownership was lost or the challenge was no
     * longer sendable. In either case the caller must not decrypt or submit.
     */
    fun authorizeDispatch(recordId: Long, claimToken: String, leaseDuration: java.time.Duration): SmsDispatchAuthorization?

    /**
     * Transitions the row with [recordId] to SENT, setting
     * [providerMessageId] and nulling the OTP ciphertext on the linked
     * challenge row — atomically in one transaction
     * (Decision Lock item 11: ciphertext destruction).
     */
    fun markSent(recordId: Long, claimToken: String, providerMessageId: Long, at: Instant): Boolean

    /**
     * Transitions the row with [recordId] to FAILED, recording
     * [failureReason]. No retry will be attempted (provider definitively
     * rejected). Also nulls the OTP ciphertext on the linked challenge row
     * — a definitive rejection makes the ciphertext permanently useless.
     */
    fun markFailed(recordId: Long, claimToken: String, failureReason: String, at: Instant): Boolean

    /**
     * Transitions the row with [recordId] back to PENDING with an
     * incremented [retryCount], recording [failureReason]. Called when
     * the send outcome is UNKNOWN (server error / timeout). The row becomes
     * reclaimable by the next [claimBatch] call that fires after the
     * computed backoff delay has elapsed.
     *
     * The OTP ciphertext on the challenge row is NOT nulled — the SMS may
     * not have been sent, so the relay must be able to decrypt it on retry.
     */
    fun markUnknownForRetry(
        recordId: Long,
        claimToken: String,
        failureReason: String,
        retryCount: Int,
        retryAfter: Instant,
        at: Instant
    ): Boolean

    /** Marks an uncertain submission terminal when no safe retry remains. */
    fun markUnknownTerminal(recordId: Long, claimToken: String, failureReason: String, at: Instant): Boolean

    /** Cancels unclaimed rows for challenges superseded by a newer issuance. */
    fun cancelPendingForSuperseded(identityId: String, purpose: String, at: Instant)

    /**
     * Deletes SENT rows older than [sentOlderThan] (30-day retention,
     * Decision Lock I.2) and FAILED/UNKNOWN rows older than [otherOlderThan]
     * (90-day retention). Also deletes expired PENDING rows whose linked
     * challenge has already expired (90 days from challenge expiry).
     * Safe to run concurrently — idempotent DELETE with WHERE clauses.
     */
    fun deleteByRetentionPolicy(sentOlderThan: Instant, otherOlderThan: Instant)

    /**
     * Finds the outbox record for a given [challengeId]. Used in tests
     * to assert state after relay operations.
     */
    fun findByChallengeId(challengeId: String): SmsOutboxRecord?

    /**
     * Finds the challenge referenced by [challengeId]. The relay needs the
     * challenge's OTP ciphertext and phone to decrypt and deliver.
     */
    fun findChallengeById(challengeId: String): PhoneVerificationChallenge?
}
