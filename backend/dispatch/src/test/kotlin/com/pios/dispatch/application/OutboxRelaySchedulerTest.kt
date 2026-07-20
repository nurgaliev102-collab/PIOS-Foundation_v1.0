package com.pios.dispatch.application

import com.pios.dispatch.persistence.PostgreSQLOutboxRepository
import com.pios.dispatch.persistence.PostgreSQLTestDatabase
import com.pios.dispatch.persistence.RabbitMQEventPublisher
import com.pios.dispatch.persistence.RabbitMQTestConnection
import com.pios.dispatch.persistence.RabbitMQTestObserverQueue
import com.pios.dispatch.persistence.awaitUntilNotNull
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Test-only, scheduling-only Spring context registering the real
 * [OutboxRepository], [EventPublisher], [OutboxRelay], and
 * [OutboxRelayScheduler] beans -- deliberately not the full
 * `DispatchApplication`. Mirrors Order Management's own
 * `TestOutboxRelaySchedulerConfiguration` exactly.
 */
@Configuration
@EnableScheduling
class TestOutboxRelaySchedulerConfiguration {

    @Bean
    fun outboxRepository(): OutboxRepository =
        PostgreSQLOutboxRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))

    @Bean
    fun eventPublisher(): EventPublisher =
        RabbitMQEventPublisher(RabbitTemplate(RabbitMQTestConnection.connectionFactory))

    @Bean
    fun outboxRelay(outboxRepository: OutboxRepository, eventPublisher: EventPublisher): OutboxRelay =
        OutboxRelay(outboxRepository, eventPublisher)

    @Bean
    fun outboxRelayScheduler(outboxRelay: OutboxRelay): OutboxRelayScheduler =
        OutboxRelayScheduler(outboxRelay)
}

/**
 * Proves [OutboxRelayScheduler] actually triggers [OutboxRelay.relay]
 * automatically, on its own configured schedule (Implementation Plan:
 * Production Outbox Relay Trigger v1.0). This test never calls `.relay()`
 * itself. Mirrors Order Management's own `OutboxRelaySchedulerTest` exactly.
 */
class OutboxRelaySchedulerTest {

    @Test
    fun `a pending outbox record is automatically relayed by the scheduled trigger, without the test calling relay itself`() {
        val context = AnnotationConfigApplicationContext(TestOutboxRelaySchedulerConfiguration::class.java)

        try {
            val outboxRepository = context.getBean(OutboxRepository::class.java)
            val observer = RabbitMQTestObserverQueue(RabbitMQTestConnection.connectionFactory)
            val marker = UUID.randomUUID().toString()

            val saved = outboxRepository.save(
                OutboxRecord(
                    aggregateId = marker,
                    eventType = "OrderAssigned",
                    routingKey = "order.assigned",
                    payload = """{"orderId":"$marker"}"""
                )
            )

            val received = observer.receiveMessageContaining(marker)
            assertTrue(received != null)

            val markedPublished = awaitUntilNotNull(maxAttempts = 20, perAttemptDelayMillis = 100) {
                if (outboxRepository.findUnpublished().none { it.id == saved.id }) true else null
            }
            assertTrue(markedPublished == true)
        } finally {
            context.close()
        }
    }
}
