package com.pios.ordermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.ordermanagement.application.OrderCancellationCoordinationService
import com.pios.ordermanagement.application.OrderTerminationRecord
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class CommitmentTerminatedListener(
    private val service: OrderCancellationCoordinationService,
    private val mapper: ObjectMapper
) {
    @RabbitListener(
        queues = [RabbitMQConsumerTopologyConfiguration.COMMITMENT_TERMINATED_QUEUE_NAME],
        containerFactory = "rabbitListenerContainerFactory"
    )
    fun onMessage(message: String) {
        val envelope = mapper.readTree(message)
        require(envelope.path("eventType").asText() == "CommitmentTerminated")
        require(envelope.path("eventVersion").asInt() == 1)
        require(envelope.path("eventId").asText().isNotBlank())
        val data = envelope.path("payload")
        service.resolveTermination(
            OrderTerminationRecord(
                orderId = data.path("orderId").asText(),
                requestId = data.path("requestId").asText(),
                assignmentId = data.path("assignmentId").asText(),
                driverId = data.path("driverId").asText(),
                initiator = data.path("initiator").asText(),
                reasonCode = data.path("reasonCode").asText(),
                note = data.get("note")?.takeUnless { it.isNull }?.asText(),
                terminatedAt = Instant.parse(data.path("terminatedAt").asText())
            )
        )
    }
}
