package com.pios.identity.application

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Periodic cleanup of old outbox rows per the retention policy
 * (C-2 Decision Lock I.2):
 *
 * - SENT rows: delete after 30 days (operational data, no security value
 *   after ciphertext has been nulled and OTP TTL expired).
 * - FAILED / UNKNOWN rows: delete after 90 days (audit data — evidence of
 *   delivery gaps, may support fraud investigation within 60-day window).
 * - Expired PENDING rows (challenge.expires_at < now()): delete after 90 days
 *   from the challenge's own expiry (relay-never-reached, audit evidence).
 *
 * Runs nightly (00:15 UTC). The DELETE queries in [SmsOutboxRepository.deleteByRetentionPolicy]
 * are idempotent — running them concurrently or repeatedly is safe.
 */
@Component
class SmsOutboxRetentionScheduler(
    private val smsOutboxRepository: SmsOutboxRepository
) {
    private val logger = LoggerFactory.getLogger(SmsOutboxRetentionScheduler::class.java)

    @Scheduled(cron = "\${pios.identity.sms.retention.cron:0 15 0 * * *}", zone = "UTC")
    fun runRetentionCleanup() {
        val now = Instant.now()
        val sentOlderThan = now.minus(SENT_RETENTION)
        val otherOlderThan = now.minus(AUDIT_RETENTION)
        logger.info(
            "SmsOutboxRetentionScheduler: deleting SENT older than {}, FAILED/UNKNOWN older than {}",
            sentOlderThan, otherOlderThan
        )
        smsOutboxRepository.deleteByRetentionPolicy(sentOlderThan, otherOlderThan)
    }

    companion object {
        val SENT_RETENTION: Duration = Duration.ofDays(30)
        val AUDIT_RETENTION: Duration = Duration.ofDays(90)
    }
}
