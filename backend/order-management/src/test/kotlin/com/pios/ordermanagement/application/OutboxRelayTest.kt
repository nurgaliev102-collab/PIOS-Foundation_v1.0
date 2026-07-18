package com.pios.ordermanagement.application

import com.pios.ordermanagement.persistence.PostgreSQLOutboxRepository
import com.pios.ordermanagement.persistence.PostgreSQLTestDatabase
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves the outbox relay skeleton (Outbox Foundation v1.0; ADR-032)
 * reads pending records without publishing anything or marking them
 * published — the seam a future task wires a real RabbitMQ client into.
 */
class OutboxRelayTest {

    private val outboxRepository = PostgreSQLOutboxRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
    private val relay = OutboxRelay(outboxRepository)

    @Test
    fun `relay returns pending records without marking them published`() {
        val saved = outboxRepository.save(
            OutboxRecord(
                aggregateId = "relay-test-order-1",
                eventType = "OrderSubmitted",
                routingKey = "order.submitted",
                payload = "{}"
            )
        )

        val relayed = relay.relay()

        assertTrue(relayed.any { it.id == saved.id })
        assertTrue(outboxRepository.findUnpublished().any { it.id == saved.id })
    }
}
