package com.pios.passengerexperience.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Passenger Experience's own outbox relay (Task 14, First Refusal
 * Foundation), mirroring Dispatch's, Order Management's, and Driver
 * Management's own already-proven relay exactly (ADR-031, ADR-032). Polls
 * this domain's own outbox table for unpublished records, in commit
 * order, and publishes each through [eventPublisher] to this module's own
 * exchange — marking a record published only once the broker has
 * confirmed receipt ([EventPublisher]'s own contract).
 *
 * If publishing a record fails (the broker does not confirm, or the
 * connection fails), that record is left unpublished and simply retried
 * on the next call to [relay] — it is never marked published without an
 * actual confirmation, and one record's failure does not stop the rest of
 * the batch from being attempted.
 *
 * [eventPublisher] has no default, for the identical reason Dispatch's
 * own `OutboxRelay` requires it explicitly: a Kotlin default here would
 * let Spring silently fall back to it whenever no [EventPublisher] bean
 * can be resolved, discarding every event without error. A production
 * context missing a real [EventPublisher] bean must fail to start
 * instead.
 */
@Component
class OutboxRelay(
    private val outboxRepository: OutboxRepository,
    private val eventPublisher: EventPublisher
) {
    private val logger = LoggerFactory.getLogger(OutboxRelay::class.java)

    /**
     * Attempts to publish every currently-unpublished outbox record, in
     * commit order, marking each one published only after [eventPublisher]
     * confirms it. Returns the records actually relayed — a subset of
     * what was pending if any attempt failed.
     */
    fun relay(): List<OutboxRecord> {
        val pending = outboxRepository.findUnpublished()
        val relayed = mutableListOf<OutboxRecord>()
        for (record in pending) {
            try {
                eventPublisher.publish(record.routingKey, record.payload)
                outboxRepository.markPublished(record.id!!)
                relayed.add(record)
                logger.info(
                    "Relayed {} (routing key '{}') for aggregate {}",
                    record.eventType,
                    record.routingKey,
                    record.aggregateId
                )
            } catch (ex: Exception) {
                logger.warn(
                    "Failed to relay {} (routing key '{}') for aggregate {}; will retry on next poll",
                    record.eventType,
                    record.routingKey,
                    record.aggregateId,
                    ex
                )
            }
        }
        return relayed
    }
}
