package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.DeclareAvailabilityCommand
import com.pios.drivermanagement.application.DriverAvailabilityApplicationService
import com.pios.drivermanagement.application.OutboxRelay
import com.pios.drivermanagement.domain.Availability
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves Driver Management Event Publishing v1.0's end-to-end goal:
 * declaring an availability change eventually produces a real, observable
 * DriverAvailabilityChanged message on Driver Management's own RabbitMQ
 * exchange -- not merely that the outbox, relay, and publisher each work
 * in isolation.
 */
class DriverAvailabilityChangedPublicationTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val driverRepository = PostgreSQLDriverRepository(JdbcTemplate(dataSource))
    private val outboxRepository = PostgreSQLOutboxRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val eventPublisher = RabbitMQEventPublisher(RabbitTemplate(RabbitMQTestConnection.connectionFactory))
    private val service = DriverAvailabilityApplicationService(driverRepository, outboxRepository, transactionRunner)
    private val relay = OutboxRelay(outboxRepository, eventPublisher)

    @Test
    fun `declaring availability eventually publishes DriverAvailabilityChanged to Driver Management's own exchange`() {
        val observer = RabbitMQTestObserverQueue(RabbitMQTestConnection.connectionFactory)
        val driverId = DriverId("publication-driver-${java.util.UUID.randomUUID()}")
        val driver = Driver(id = driverId, availability = Availability.UNAVAILABLE)

        service.handle(driver, DeclareAvailabilityCommand(driverId, Availability.AVAILABLE))
        relay.relay()

        val received = observer.receiveMessageContaining(driverId.value)

        assertTrue(received != null)
        assertTrue(outboxRepository.findUnpublished().none { it.aggregateId == driverId.value })
    }
}
