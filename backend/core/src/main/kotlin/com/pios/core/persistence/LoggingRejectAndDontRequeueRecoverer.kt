package com.pios.core.persistence

import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.retry.MessageRecoverer
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException

/**
 * Wraps Spring Retry's [RejectAndDontRequeueRecoverer] purely to log that
 * a message's bounded retry policy (ADR-031 / ADR-067) has been
 * exhausted, before delegating to it for the actual reject-without-requeue
 * behaviour that routes the message to the consuming queue's own
 * dead-letter queue (via the `x-dead-letter-*` arguments each topology
 * configuration sets). Keeps processing failure operationally visible
 * without building any retry mechanism of its own — Spring Retry's
 * recoverer still does the actual work.
 *
 * Mirrors dispatch's own `LoggingRejectAndDontRequeueRecoverer`; the only
 * difference is that Core has three consumer queues, so the failed
 * message's own consumer-queue / routing-key (read from its
 * [org.springframework.amqp.core.MessageProperties]) is logged rather
 * than a single hard-coded queue name.
 */
class LoggingRejectAndDontRequeueRecoverer : MessageRecoverer {
    private val logger = LoggerFactory.getLogger(LoggingRejectAndDontRequeueRecoverer::class.java)
    private val delegate = RejectAndDontRequeueRecoverer()

    override fun recover(message: Message, cause: Throwable) {
        val rootCause = if (cause is ListenerExecutionFailedException) cause.cause ?: cause else cause
        val props = message.messageProperties
        logger.error(
            "Exhausted retry attempts for a message on queue '{}' (exchange '{}', routingKey '{}'); routing to its dead-letter queue",
            props.consumerQueue ?: "?",
            props.receivedExchange ?: "?",
            props.receivedRoutingKey ?: "?",
            rootCause
        )
        delegate.recover(message, cause)
    }
}
