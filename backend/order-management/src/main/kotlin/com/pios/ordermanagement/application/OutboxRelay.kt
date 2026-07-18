package com.pios.ordermanagement.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Order Management's own outbox relay (ADR-031, ADR-032; RabbitMQ Event
 * Publishing Foundation v1.0). Polls this domain's own outbox table for
 * unpublished records, in commit order, and publishes each through
 * [eventPublisher] to Order Management's own exchange — marking a record
 * published only once the broker has confirmed receipt
 * ([EventPublisher]'s own contract).
 *
 * If publishing a record fails (the broker does not confirm, or the
 * connection fails), that record is left unpublished and simply retried
 * on the next call to [relay] — it is never marked published without an
 * actual confirmation, and one record's failure does not stop the rest
 * of the batch from being attempted (ADR-032).
 *
 * [eventPublisher] defaults to [NoOpEventPublisher] so that a caller not
 * concerned with actual publication (for example, a test only checking
 * which records are pending) does not need a real broker; a real,
 * Spring-wired instance always receives the real
 * [com.pios.ordermanagement.persistence.RabbitMQEventPublisher] bean
 * instead.
 */
@Component
class OutboxRelay(
    private val outboxRepository: OutboxRepository,
    private val eventPublisher: EventPublisher = NoOpEventPublisher
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
