package com.pios.identity.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.min
import kotlin.math.pow

/**
 * SMS outbox relay (C-2 Decision Lock I.4, I.5).
 *
 * On each [relay] call (triggered by [SmsOutboxRelayScheduler]):
 * 1. Claims a batch of reclaimable rows (PENDING, or PROCESSING with expired
 *    lease) via [SmsOutboxRepository.claimBatch] — atomic, FOR UPDATE SKIP
 *    LOCKED, no two concurrent relay instances can claim the same row.
 * 2. For each row: finds the linked challenge, decrypts the OTP ciphertext,
 *    calls [OutboundSmsPort.sendVerificationCode].
 * 3. Transitions to SENT / FAILED / UNKNOWN based on the outcome.
 *    SENT: [SmsOutboxRepository.markSent] — also nulls the ciphertext (item 11).
 *    FAILED: [SmsOutboxRepository.markFailed] — permanent, no retry.
 *    UNKNOWN: [SmsOutboxRepository.markUnknownForRetry] — schedules a retry
 *             if within the retry window; otherwise marks FAILED definitively.
 *
 * Backoff policy (Decision Lock I.4):
 * - Initial delay: [initialBackoffMs] = 30 000 ms (30 s)
 * - Multiplier: 2.0
 * - Cap: [maxBackoffMs] = 120 000 ms (120 s)
 * - Jitter: full jitter — retry_after = uniform(0, computed_delay)
 * - Max window: [maxRetryWindowMs] = 300 000 ms (300 s) OR challenge expiry,
 *   whichever comes first.
 *
 * Security invariants (never violated here):
 * - The decrypted OTP plaintext is never logged (D-03.7).
 * - The challenge phone is read from the DB, never from the outbox row.
 * - ciphertext is null-checked before decryption; a null ciphertext means
 *   the OTP was already delivered or the challenge is exhausted — skip row.
 */
@Component
class SmsOutboxRelay(
    private val smsOutboxRepository: SmsOutboxRepository,
    private val outboundSmsPort: OutboundSmsPort,
    private val otpCipher: OtpCipher,
    private val clock: Clock,
    private val retryJitter: SmsRetryJitter,
    @Value("\${pios.identity.sms.relay.batch-size:50}") private val batchSize: Int,
    @Value("\${pios.identity.sms.relay.lease-seconds:60}") private val leaseSeconds: Long,
    @Value("\${pios.identity.sms.relay.backoff.initial-delay-ms:30000}") private val initialBackoffMs: Long,
    @Value("\${pios.identity.sms.relay.backoff.max-delay-ms:120000}") private val maxBackoffMs: Long,
    @Value("\${pios.identity.sms.relay.backoff.max-window-ms:300000}") private val maxRetryWindowMs: Long
) {
    private val logger = LoggerFactory.getLogger(SmsOutboxRelay::class.java)
    private val leaseDuration = Duration.ofSeconds(leaseSeconds)

    fun relay() {
        val batch = smsOutboxRepository.claimBatch(batchSize, leaseDuration)
        for (record in batch) {
            processRow(record)
        }
    }

    /** Test seam for a row already claimed by a real repository. */
    internal fun relayClaimed(record: SmsOutboxRecord) {
        processRow(record)
    }

    private fun processRow(record: SmsOutboxRecord) {
        val claimToken = record.claimToken
        if (claimToken == null) {
            logger.error("SmsOutboxRelay: claimed row {} has no claim token", record.id)
            return
        }
        val authorization = smsOutboxRepository.authorizeDispatch(record.id!!, claimToken, leaseDuration)
        if (authorization == null) {
            logger.info("SmsOutboxRelay: dispatch authorization denied for outbox row {}; not submitting", record.id)
            return
        }
        val challenge = authorization.challenge
        val dispatchToken = authorization.claimToken
        val now = clock.instant()

        // Challenge deleted or not found — skip, cannot deliver.
        if (challenge == null) {
            logger.warn("SmsOutboxRelay: challenge {} not found for outbox row {}; skipping", record.challengeId, record.id)
            markFailed(record, dispatchToken, "CHALLENGE_NOT_FOUND", clock.instant())
            return
        }

        // A consumed, superseded, exhausted, or expired challenge must never
        // be decrypted or sent. Pending superseded rows are cancelled in the
        // issuer transaction too; this check closes the relay race window.
        if (!challenge.isLive || challenge.isExpired(now) || challenge.attemptsExhausted) {
            logger.info("SmsOutboxRelay: challenge {} is no longer sendable", record.challengeId)
            markFailed(record, dispatchToken, "CHALLENGE_NOT_LIVE", clock.instant())
            return
        }

        // Max retry window check (300 s from first creation, Decision Lock I.4).
        val elapsedSinceCreation = Duration.between(record.createdAt, now).toMillis()
        if (record.retryCount > 0 && elapsedSinceCreation > maxRetryWindowMs) {
            logger.info(
                "SmsOutboxRelay: max retry window exceeded for outbox row {} ({}ms elapsed); marking FAILED",
                record.id, elapsedSinceCreation
            )
            markUnknownTerminal(record, dispatchToken, "MAX_RETRY_WINDOW_EXCEEDED", clock.instant())
            return
        }

        // Ciphertext check: null means prior SENT/FAILED already nulled it.
        val ciphertext = challenge.otpCiphertext
        val nonce = challenge.otpNonce
        if (ciphertext == null || nonce == null) {
            logger.warn(
                "SmsOutboxRelay: OTP ciphertext already nulled for challenge {} (outbox row {}); skipping",
                record.challengeId, record.id
            )
            // Row is in an inconsistent state (shouldn't happen in steady state).
            // Mark FAILED to prevent perpetual re-claim.
            markFailed(record, dispatchToken, "CIPHERTEXT_ALREADY_NULLED", clock.instant())
            return
        }

        val plaintext = try {
            otpCipher.decrypt(ciphertext, nonce)
        } catch (ex: Exception) {
            // Decryption failure: wrong key or tampered ciphertext.
            // Never log the ciphertext or any decryption detail.
            logger.error("SmsOutboxRelay: OTP decryption failed for outbox row {}; marking FAILED", record.id)
            markFailed(record, dispatchToken, "DECRYPTION_FAILED", clock.instant())
            return
        }

        val providerMessageId: Long
        try {
            // plaintext (the OTP) must never be logged — D-03.7.
            providerMessageId = outboundSmsPort.sendVerificationCode(challenge.phone, plaintext)
        } catch (ex: OutboundSmsDeliveryException) {
            when (ex.state) {
                SmsSubmissionState.FAILED -> {
                    // Provider definitively rejected — no retry.
                    logger.warn(
                        "SmsOutboxRelay: FAILED outbox row {} reason={} httpStatus={}",
                        record.id, ex.failure, ex.httpStatus
                    )
                    markFailed(record, dispatchToken, "${ex.failure}:${ex.httpStatus}", clock.instant())
                }
                SmsSubmissionState.UNKNOWN -> {
                    handleUnknown(record, dispatchToken, "${ex.failure}:${ex.httpStatus}", challenge, clock.instant())
                }
            }
            return
        } catch (ex: Exception) {
            // Unexpected adapter error — treat as UNKNOWN.
            logger.warn(
                "SmsOutboxRelay: unexpected exception for outbox row {}; treating as UNKNOWN",
                record.id
            )
            handleUnknown(record, dispatchToken, "UNEXPECTED_EXCEPTION", challenge, clock.instant())
            return
        }

        if (providerMessageId <= 0) {
            // Ports must only report a concrete provider acceptance ID. Treat
            // a broken adapter contract as uncertain, never as a successful
            // delivery submission.
            handleUnknown(record, dispatchToken, "INVALID_PROVIDER_MESSAGE_ID", challenge, clock.instant())
            return
        }

        // Success: provider accepted.
        if (smsOutboxRepository.markSent(record.id!!, dispatchToken, providerMessageId, clock.instant())) {
            logger.info(
                "SmsOutboxRelay: SENT outbox row {} challenge {} providerMessageId={}",
                record.id, record.challengeId, providerMessageId
            )
        } else {
            logger.warn("SmsOutboxRelay: lost fenced ownership before SENT transition for outbox row {}", record.id)
        }
    }

    /**
     * Schedules an UNKNOWN retry or gives up if the max window is exceeded
     * or the OTP has expired.
     *
     * Backoff = full jitter: `retry_after = now + uniform(0, min(initial * 2^n, cap))`
     * where n = current [SmsOutboxRecord.retryCount].
     * Full jitter prevents simultaneous retry storms when multiple rows failed
     * at the same time (e.g., SMS Aero was down for 60 s).
     */
    private fun handleUnknown(
        record: SmsOutboxRecord,
        claimToken: String,
        reason: String,
        challenge: com.pios.identity.domain.PhoneVerificationChallenge,
        now: Instant
    ) {
        val nextRetryCount = record.retryCount + 1
        val baseDelay = min(initialBackoffMs * 2.0.pow(record.retryCount.toDouble()), maxBackoffMs.toDouble()).toLong()
        val jitteredDelay = retryJitter.nextDelayMillis(baseDelay)
        val retryAfter = now.plusMillis(jitteredDelay)

        // Don't schedule a retry past the OTP expiry or past the max window.
        val maxWindowBoundary = record.createdAt.plusMillis(maxRetryWindowMs)
        val hardStop = if (challenge.expiresAt.isBefore(maxWindowBoundary)) challenge.expiresAt else maxWindowBoundary

        if (!retryAfter.isBefore(hardStop) || !now.isBefore(hardStop)) {
            logger.info(
                "SmsOutboxRelay: UNKNOWN outbox row {} would retry past hard stop {}; marking FAILED (no-retry)",
                record.id, hardStop
            )
            markUnknownTerminal(record, claimToken, "UNKNOWN_RETRY_EXHAUSTED:$reason", now)
            return
        }

        logger.warn(
            "SmsOutboxRelay: UNKNOWN outbox row {} reason={} retryCount={} retryAfter={}",
            record.id, reason, nextRetryCount, retryAfter
        )
        if (!smsOutboxRepository.markUnknownForRetry(record.id!!, claimToken, reason, nextRetryCount, retryAfter, now)) {
            logger.warn("SmsOutboxRelay: lost fenced ownership before UNKNOWN retry transition for outbox row {}", record.id)
        }
    }

    private fun markFailed(record: SmsOutboxRecord, claimToken: String, reason: String, at: Instant) {
        if (!smsOutboxRepository.markFailed(record.id!!, claimToken, reason, at)) {
            logger.warn("SmsOutboxRelay: lost fenced ownership before FAILED transition for outbox row {}", record.id)
        }
    }

    private fun markUnknownTerminal(record: SmsOutboxRecord, claimToken: String, reason: String, at: Instant) {
        if (!smsOutboxRepository.markUnknownTerminal(record.id!!, claimToken, reason, at)) {
            logger.warn("SmsOutboxRelay: lost fenced ownership before terminal UNKNOWN transition for outbox row {}", record.id)
        }
    }
}
