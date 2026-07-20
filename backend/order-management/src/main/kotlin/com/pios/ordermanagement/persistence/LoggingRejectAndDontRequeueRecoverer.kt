package com.pios.ordermanagement.persistence

import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.retry.MessageRecoverer
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException

/**
 * Wraps Spring Retry's [RejectAndDontRequeueRecoverer] purely to log that
 * a message's bounded retry policy (ADR-031) has been exhausted, before
 * delegating to it for the actual reject-without-requeue behavior that
 * routes the message to Order Management's own dead-letter queue
 * ([RabbitMQConsumerTopologyConfiguration.DEAD_LETTER_QUEUE_NAME]). Keeps
 * processing failure operationally visible (ADR-031's own requirement)
 * without building any retry mechanism of its own -- Spring Retry's
 * recoverer still does the actual work. Mirrors Dispatch's own
 * already-proven recoverer exactly (Tranche 1: Dispatch Event Publishing
 * Completion).
 */
class LoggingRejectAndDontRequeueRecoverer : MessageRecoverer {
    private val logger = LoggerFactory.getLogger(LoggingRejectAndDontRequeueRecoverer::class.java)
    private val delegate = RejectAndDontRequeueRecoverer()

    override fun recover(message: Message, cause: Throwable) {
        val rootCause = if (cause is ListenerExecutionFailedException) cause.cause ?: cause else cause
        logger.error(
            "Exhausted retry attempts for a message on {}; routing to dead-letter queue {}",
            RabbitMQConsumerTopologyConfiguration.QUEUE_NAME,
            RabbitMQConsumerTopologyConfiguration.DEAD_LETTER_QUEUE_NAME,
            rootCause
        )
        delegate.recover(message, cause)
    }
}
