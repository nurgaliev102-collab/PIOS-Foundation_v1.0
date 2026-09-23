package com.pios.identity.persistence

import com.pios.identity.application.SmsOutboxRecord
import com.pios.identity.application.SmsDispatchAuthorization
import com.pios.identity.application.SmsOutboxRepository
import com.pios.identity.application.SmsOutboxStatus
import com.pios.identity.domain.PhoneVerificationChallenge
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Test-only outbox with the same lease fencing semantics as PostgreSQL. */
class InMemorySmsOutboxRepository(
    private val challenges: InMemoryPhoneVerificationChallengeRepository
) : SmsOutboxRepository {
    private val lock = Any()
    private val ids = AtomicLong(0)
    private val records = linkedMapOf<Long, SmsOutboxRecord>()

    override fun insert(record: SmsOutboxRecord) = synchronized(lock) {
        val id = ids.incrementAndGet()
        records[id] = record.copy(id = id)
    }

    override fun claimBatch(batchSize: Int, leaseDuration: Duration): List<SmsOutboxRecord> = synchronized(lock) {
        val now = Instant.now()
        val token = UUID.randomUUID().toString()
        records.values
            .filter { (it.status == SmsOutboxStatus.PENDING && !it.nextAttemptAt.isAfter(now)) ||
                (it.status == SmsOutboxStatus.PROCESSING && it.leaseUntil?.isBefore(now) == true) }
            .sortedBy { it.createdAt }
            .take(batchSize)
            .map { record ->
                record.copy(status = SmsOutboxStatus.PROCESSING, claimToken = token,
                    leaseUntil = now.plus(leaseDuration), updatedAt = now).also { records[record.id!!] = it }
            }
    }

    override fun authorizeDispatch(recordId: Long, claimToken: String, leaseDuration: Duration): SmsDispatchAuthorization? = synchronized(lock) {
        val now = Instant.now()
        val record = records[recordId] ?: return@synchronized null
        if (record.status != SmsOutboxStatus.PROCESSING || record.claimToken != claimToken ||
            record.leaseUntil?.isAfter(now) != true) return@synchronized null
        val challenge = challenges.findById(record.challengeId) ?: run {
            records[recordId] = record.copy(status = SmsOutboxStatus.FAILED, failureReason = "CHALLENGE_NOT_FOUND",
                leaseUntil = null, claimToken = null, updatedAt = now)
            return@synchronized null
        }
        if (!challenge.isLive || challenge.isExpired(now) || challenge.attemptsExhausted ||
            challenge.otpCiphertext == null || challenge.otpNonce == null) {
            records[recordId] = record.copy(status = SmsOutboxStatus.FAILED, failureReason = "CHALLENGE_NOT_SENDABLE",
                leaseUntil = null, claimToken = null, updatedAt = now)
            challenges.nullCiphertext(challenge.id)
            return@synchronized null
        }
        val renewed = record.copy(leaseUntil = now.plus(leaseDuration), claimToken = UUID.randomUUID().toString(), updatedAt = now)
        records[recordId] = renewed
        SmsDispatchAuthorization(challenge, renewed.leaseUntil!!, renewed.claimToken!!)
    }

    override fun markSent(recordId: Long, claimToken: String, providerMessageId: Long, at: Instant): Boolean = synchronized(lock) {
        transition(recordId, claimToken) { it.copy(status = SmsOutboxStatus.SENT, providerMessageId = providerMessageId,
            sentAt = at, leaseUntil = null, claimToken = null, updatedAt = at) }
            ?.also { challenges.nullCiphertext(it.challengeId) } != null
    }

    override fun markFailed(recordId: Long, claimToken: String, failureReason: String, at: Instant): Boolean = synchronized(lock) {
        transition(recordId, claimToken) { it.copy(status = SmsOutboxStatus.FAILED, failureReason = failureReason,
            leaseUntil = null, claimToken = null, updatedAt = at) }
            ?.also { challenges.nullCiphertext(it.challengeId) } != null
    }

    override fun markUnknownForRetry(recordId: Long, claimToken: String, failureReason: String, retryCount: Int,
        retryAfter: Instant, at: Instant): Boolean = synchronized(lock) {
        transition(recordId, claimToken) { it.copy(status = SmsOutboxStatus.PENDING, failureReason = failureReason,
            retryCount = retryCount, nextAttemptAt = retryAfter, leaseUntil = null, claimToken = null, updatedAt = at) } != null
    }

    override fun markUnknownTerminal(recordId: Long, claimToken: String, failureReason: String, at: Instant): Boolean = synchronized(lock) {
        transition(recordId, claimToken) { it.copy(status = SmsOutboxStatus.UNKNOWN, failureReason = failureReason,
            leaseUntil = null, claimToken = null, updatedAt = at) } != null
    }

    override fun cancelPendingForSuperseded(identityId: String, purpose: String, at: Instant) = synchronized(lock) {
        val superseded = challenges.supersededChallengeIds(identityId, purpose)
        records.replaceAll { _, record ->
            if (record.status == SmsOutboxStatus.PENDING && record.challengeId in superseded)
                record.copy(status = SmsOutboxStatus.FAILED, failureReason = "SUPERSEDED", updatedAt = at)
            else record
        }
    }

    override fun deleteByRetentionPolicy(sentOlderThan: Instant, otherOlderThan: Instant) {
        synchronized(lock) {
            records.entries.removeIf { (_, record) ->
                (record.status == SmsOutboxStatus.SENT && record.sentAt?.isBefore(sentOlderThan) == true) ||
                    (record.status in setOf(SmsOutboxStatus.FAILED, SmsOutboxStatus.UNKNOWN) && record.updatedAt.isBefore(otherOlderThan)) ||
                    (record.status == SmsOutboxStatus.PENDING && challenges.findById(record.challengeId)?.expiresAt?.isBefore(otherOlderThan) == true)
            }
        }
    }

    override fun findByChallengeId(challengeId: String): SmsOutboxRecord? = synchronized(lock) {
        records.values.filter { it.challengeId == challengeId }.maxByOrNull { it.id!! }
    }

    override fun findChallengeById(challengeId: String): PhoneVerificationChallenge? = challenges.findById(challengeId)

    /** Test-only visibility for assertions that an ineligible request enqueues nothing. */
    fun allRecords(): List<SmsOutboxRecord> = synchronized(lock) { records.values.toList() }

    private fun transition(recordId: Long, claimToken: String, change: (SmsOutboxRecord) -> SmsOutboxRecord): SmsOutboxRecord? {
        val current = records[recordId] ?: return null
        if (current.status != SmsOutboxStatus.PROCESSING || current.claimToken != claimToken ||
            current.leaseUntil?.isAfter(Instant.now()) != true) return null
        return change(current).also { records[recordId] = it }
    }
}
