package com.pios.ordermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.ordermanagement.application.DispatchExhaustedApplicationService
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class DispatchExhaustedListener(
    private val applicationService: DispatchExhaustedApplicationService,
    private val objectMapper: ObjectMapper
) {
    @RabbitListener(
        queues = [RabbitMQConsumerTopologyConfiguration.DISPATCH_EXHAUSTED_QUEUE_NAME],
        containerFactory = "rabbitListenerContainerFactory"
    )
    fun onMessage(payload: String) {
        val envelope = objectMapper.readTree(payload)
        require(envelope.get("eventType")?.asText() == "DispatchExhausted") { "Unsupported event type" }
        require(envelope.get("eventVersion")?.asInt() == 1) { "Unsupported event version" }
        require(!envelope.get("eventId")?.asText().isNullOrBlank()) { "Missing eventId" }
        val data = requireNotNull(envelope.get("payload")) { "Missing payload" }
        require(data.get("reason")?.asText() == "NO_OFFER_WITHIN_WINDOW") { "Unsupported exhaustion reason" }
        val orderId = data.get("orderId")?.asText()
        require(!orderId.isNullOrBlank()) { "Missing payload.orderId" }
        applicationService.handle(orderId)
    }
}
