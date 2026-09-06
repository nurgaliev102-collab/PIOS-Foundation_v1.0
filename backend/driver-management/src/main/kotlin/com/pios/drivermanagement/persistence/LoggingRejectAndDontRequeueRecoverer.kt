package com.pios.drivermanagement.persistence

import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.retry.MessageRecoverer
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
 * mirrors Order Management's own `LoggingRejectAndDontRequeueRecoverer`
 * exactly -- logs that this queue's bounded retry policy has been
 * exhausted before delegating to Spring Retry's own recoverer, which
 * routes the message to this module's own dead-letter queue.
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
