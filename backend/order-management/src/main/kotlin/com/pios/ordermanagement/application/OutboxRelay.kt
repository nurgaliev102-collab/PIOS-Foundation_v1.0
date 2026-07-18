package com.pios.ordermanagement.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * The skeleton of Order Management's own outbox relay (ADR-031, ADR-032).
 * A real relay polls this domain's own outbox table for unpublished
 * records, in commit order, and publishes each to Order Management's own
 * exchange using RabbitMQ's publisher-confirm mechanism (ADR-029),
 * marking a record published only once the broker confirms receipt.
 *
 * RabbitMQ publishing is deliberately not implemented here -- Outbox
 * Foundation v1.0's explicit scope stops at proving the outbox itself is
 * transactionally correct. [relay] therefore only reads and reports
 * pending records; it never calls [OutboxRepository.markPublished],
 * since nothing has actually been published yet. This is the seam a
 * future task wires an actual broker client into, replacing the
 * placeholder log line with a real publish-then-confirm-then-mark
 * sequence.
 */
@Component
class OutboxRelay(
    private val outboxRepository: OutboxRepository
) {
    private val logger = LoggerFactory.getLogger(OutboxRelay::class.java)

    /**
     * Reads every currently-unpublished outbox record, in commit order,
     * and returns them. Does not publish anything and does not mark any
     * record published.
     */
    fun relay(): List<OutboxRecord> {
        val pending = outboxRepository.findUnpublished()
        pending.forEach { record ->
            logger.info(
                "Outbox relay skeleton: would publish {} (routing key '{}') for aggregate {} " +
                    "-- RabbitMQ publishing not yet implemented (ADR-032)",
                record.eventType,
                record.routingKey,
                record.aggregateId
            )
        }
        return pending
    }
}
