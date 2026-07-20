package com.pios.ordermanagement.application

import com.pios.ordermanagement.persistence.PostgreSQLOutboxRepository
import com.pios.ordermanagement.persistence.PostgreSQLTestDatabase
import com.pios.ordermanagement.persistence.RabbitMQEventPublisher
import com.pios.ordermanagement.persistence.RabbitMQTestConnection
import com.pios.ordermanagement.persistence.RabbitMQTestObserverQueue
import com.pios.ordermanagement.persistence.awaitUntilNotNull
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
 * `OrderManagementApplication` (which would also start the real
 * `AssignmentAcceptedListener`, a competing consumer against the same
 * real queue `AssignmentAcceptedConsumerIntegrationTest` and its sibling
 * tests already manipulate directly in this same test JVM). [EnableScheduling]
 * is required here specifically: unlike every other class in this test
 * suite, whether an `@Scheduled` method actually fires can only be proven
 * inside a real [org.springframework.context.ApplicationContext], never by
 * direct construction (Implementation Plan: Production Outbox Relay
 * Trigger v1.0, Part 7).
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
 * automatically, on its own configured schedule -- the one behavior no
 * other test in this suite exercises, and the entire point of
 * Implementation Plan: Production Outbox Relay Trigger v1.0. This test
 * never calls `.relay()` itself.
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
                    eventType = "OrderSubmitted",
                    routingKey = "order.submitted",
                    payload = """{"orderId":"$marker"}"""
                )
            )

            // receiveMessageContaining already polls the broker (up to 10s);
            // the scheduler -- never called directly by this test -- is what
            // must have relayed the record for this to ever arrive.
            val received = observer.receiveMessageContaining(marker)
            assertTrue(received != null)

            // A short additional poll: publisher-confirm and the subsequent
            // markPublished both happen on the scheduler's own thread, so the
            // database write may lag the broker delivery this test just
            // observed by a few milliseconds.
            val markedPublished = awaitUntilNotNull(maxAttempts = 20, perAttemptDelayMillis = 100) {
                if (outboxRepository.findUnpublished().none { it.id == saved.id }) true else null
            }
            assertTrue(markedPublished == true)
        } finally {
            context.close()
        }
    }
}
