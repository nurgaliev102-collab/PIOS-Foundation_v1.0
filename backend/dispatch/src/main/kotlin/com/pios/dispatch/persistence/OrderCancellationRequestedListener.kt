package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.CommitmentTerminationApplicationService
import com.pios.dispatch.application.TerminateCommitmentCommand
import com.pios.dispatch.domain.TerminationInitiator
import com.pios.dispatch.domain.TerminationReasonCode
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class OrderCancellationRequestedListener(
    private val service: CommitmentTerminationApplicationService,
    private val mapper: ObjectMapper
) {
    @RabbitListener(
        queues = [RabbitMQOrderManagementTopologyConfiguration.CANCELLATION_REQUESTED_QUEUE_NAME],
        containerFactory = "rabbitListenerContainerFactory"
    )
    fun onMessage(message: String) {
        val envelope = mapper.readTree(message)
        require(envelope.path("eventType").asText() == "OrderCancellationRequested")
        require(envelope.path("eventVersion").asInt() == 1)
        require(envelope.path("eventId").asText().isNotBlank())
        val payload = envelope.path("payload")
        val requestId = payload.path("requestId").asText()
        val orderId = payload.path("orderId").asText()
        require(requestId.isNotBlank() && orderId.isNotBlank())
        service.terminate(
            TerminateCommitmentCommand(
                requestId = requestId,
                orderId = orderId,
                initiator = TerminationInitiator.PASSENGER,
                reasonCode = payload.get("reasonCode")?.takeUnless { it.isNull }?.asText()
                    ?.let(TerminationReasonCode::valueOf),
                note = payload.get("note")?.takeUnless { it.isNull }?.asText()
            )
        )
    }
}
