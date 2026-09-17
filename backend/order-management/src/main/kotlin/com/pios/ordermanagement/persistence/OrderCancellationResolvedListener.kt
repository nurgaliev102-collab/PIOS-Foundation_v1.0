package com.pios.ordermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.ordermanagement.application.OrderCancellationCoordinationService
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class OrderCancellationResolvedListener(
    private val service: OrderCancellationCoordinationService,
    private val mapper: ObjectMapper
) {
    @RabbitListener(
        queues = [RabbitMQConsumerTopologyConfiguration.CANCELLATION_RESOLVED_QUEUE_NAME],
        containerFactory = "rabbitListenerContainerFactory"
    )
    fun onMessage(message: String) {
        val envelope = mapper.readTree(message)
        require(envelope.path("eventType").asText() == "OrderCancellationResolved")
        require(envelope.path("eventVersion").asInt() == 1)
        require(envelope.path("eventId").asText().isNotBlank())
        val data = envelope.path("payload")
        service.resolveNoCommitment(
            data.path("requestId").asText(),
            data.path("orderId").asText(),
            data.path("outcome").asText()
        )
    }
}
