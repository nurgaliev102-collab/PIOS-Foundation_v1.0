package com.pios.identity.application

import java.time.Instant

/**
 * A single row in [identity_sms_outbox] (C-2 Decision Lock I.5).
 * Written atomically with the [com.pios.identity.domain.PhoneVerificationChallenge]
 * INSERT in [PhoneVerificationChallengeIssuer] — the relay reads this row
 * to discover what to send, then transitions it through the PENDING →
 * PROCESSING → SENT | FAILED | UNKNOWN state machine.
 *
 * [phone] is NOT stored here (Decision Lock item 10): the relay obtains it
 * from the [com.pios.identity.domain.PhoneVerificationChallenge] referenced
 * by [challengeId].
 *
 * [providerMessageId]: semantics per Decision Lock I.3 — "SMS Aero accepted
 * for routing", NOT "delivered". Set on SENT transition only.
 */
data class SmsOutboxRecord(
    val id: Long?,           // null before INSERT (BIGSERIAL)
    val challengeId: String,
    val status: SmsOutboxStatus,
    val leaseUntil: Instant?,
    val claimToken: String?,
    val nextAttemptAt: Instant,
    val providerMessageId: Long?,
    val sentAt: Instant?,
    val failureReason: String?,
    val retryCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * A challenge snapshot returned only after the relay has atomically proved
 * that its fenced PROCESSING lease is current and that the challenge is
 * still sendable.  The database statement which creates this value locks the
 * challenge row, so challenge consumption and supersession are serialized
 * before the provider call without holding that lock during the call itself.
 */
data class SmsDispatchAuthorization(
    val challenge: com.pios.identity.domain.PhoneVerificationChallenge,
    val leaseUntil: Instant,
    /** Fresh, one-time dispatch fence replacing the batch claim token. */
    val claimToken: String
)

/**
 * Outbox state machine (C-2 architecture item 7, Decision Lock I.5):
 *
 * PENDING    — written by [PhoneVerificationChallengeIssuer]; not yet claimed.
 * PROCESSING — claimed by the relay; [SmsOutboxRecord.leaseUntil] is set.
 *              An expired PROCESSING row (lease_until < now()) is reclaimable.
 * SENT       — SMS Aero accepted the message; [SmsOutboxRecord.providerMessageId]
 *              is set; OTP ciphertext on the challenge row is nulled.
 * FAILED     — SMS Aero definitively rejected (HTTP 4xx or provider JSON
 *              false); no automatic retry (Decision Lock item: "no automatic
 *              retry after explicit provider rejection").
 * UNKNOWN    — server error / timeout / malformed response; the SMS may or
 *              may not have been sent. Subject to exponential backoff retry
 *              within the OTP TTL window (Decision Lock I.4).
 */
enum class SmsOutboxStatus { PENDING, PROCESSING, SENT, FAILED, UNKNOWN }
