package com.pios.drivermanagement.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Driver Management's own outbox relay (ADR-031, ADR-032; Driver
 * Management Event Publishing v1.0, applying the pattern already proven in
 * Order Management's RabbitMQ Event Publishing Foundation v1.0). Polls
 * this domain's own outbox table for unpublished records, in commit
 * order, and publishes each through [eventPublisher] to Driver
 * Management's own exchange -- marking a record published only once the
 * broker has confirmed receipt ([EventPublisher]'s own contract).
 *
 * If publishing a record fails (the broker does not confirm, or the
 * connection fails), that record is left unpublished and simply retried
 * on the next call to [relay] -- it is never marked published without an
 * actual confirmation, and one record's failure does not stop the rest
 * of the batch from being attempted (ADR-032).
 *
 * [eventPublisher] has no default. A Kotlin default here would let Spring
 * silently fall back to it whenever no [EventPublisher] bean can be
 * resolved (for example, RabbitMQ autoconfiguration failing to produce a
 * `RabbitTemplate` bean) -- constructing this relay with
 * [NoOpEventPublisher] and discarding every event without any error. This
 * is the same production-safety fix already applied to Order Management's
 * outbox relay (RabbitMQ Event Publishing Foundation v1.0 Hardening
 * Review), applied here from the start rather than discovered later.
 * Requiring the argument means a production context missing a real
 * [EventPublisher] bean fails to start with a clear
 * `NoSuchBeanDefinitionException` instead. A caller that genuinely wants
 * no-op publication (for example, a test only checking which records are
 * pending) passes [NoOpEventPublisher] explicitly.
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
     * confirms it. Returns the records actually relayed -- a subset of
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
