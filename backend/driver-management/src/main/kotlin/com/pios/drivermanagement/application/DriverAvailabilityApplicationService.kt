package com.pios.drivermanagement.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverAvailabilityChanged
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for the Declare Availability command
 * (APPLICATION_ARCHITECTURE.md Section 6). This service sequences the
 * command into the Driver aggregate's own behavior; it does not decide a
 * driver's availability state itself (APPLICATION_ARCHITECTURE.md
 * Section 2, "Domain Decides Business Meaning"). Once the aggregate has
 * actually changed, the service persists it *and* a corresponding
 * [OutboxRecord] together, inside one [transactionRunner] boundary
 * (ADR-032, applying the pattern already proven in Order Management's
 * Outbox Foundation v1.0) — so the two either both commit or both roll
 * back. The [Driver] aggregate itself remains entirely unaware that a
 * repository or an outbox exists.
 *
 * [outboxRepository] and [transactionRunner] default to no-ops so that
 * existing tests exercising only availability behavior (not the outbox)
 * continue to work unchanged; a real, Spring-wired instance of this
 * service always receives the real
 * [com.pios.drivermanagement.persistence.PostgreSQLOutboxRepository] and
 * [com.pios.drivermanagement.persistence.SpringTransactionRunner] beans
 * instead, since Spring's constructor injection always supplies an
 * argument for a parameter it can resolve a bean for, regardless of a
 * Kotlin default being present.
 */
@Service
class DriverAvailabilityApplicationService(
    private val driverRepository: DriverRepository,
    private val outboxRepository: OutboxRepository = NoOpOutboxRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner,
    private val objectMapper: ObjectMapper = ObjectMapper()
) {

    /**
     * Coordinates a [DeclareAvailabilityCommand] against the given [driver],
     * returning the resulting [DriverAvailabilityChanged] event, or null if
     * the declaration did not change the driver's availability. Persists
     * [driver] and its outbox record together only when its availability
     * actually changed — there is nothing new to persist, and no event to
     * report, otherwise.
     */
    fun handle(driver: Driver, command: DeclareAvailabilityCommand): DriverAvailabilityChanged? = transactionRunner.run {
        require(driver.id == command.driverId) {
            "Command targets driver ${command.driverId.value} but was handled against driver ${driver.id.value}"
        }
        val event = driver.declareAvailability(command.requestedAvailability)
        if (event != null) {
            driverRepository.save(driver)
            outboxRepository.save(DriverAvailabilityOutboxRecordFactory.create(event, objectMapper))
        }
        event
    }

    /**
     * Coordinates a [DeclareAvailabilityCommand] by first restoring the
     * targeted Driver through [driverRepository], proving the aggregate
     * can be saved, loaded back, and continue its own domain operation
     * exactly as it would if it had never left memory. Throws
     * [DriverNotFoundException] — an application-layer error, never a
     * persistence or domain one (see that class's own KDoc) — if no
     * Driver identified by [DeclareAvailabilityCommand.driverId] has been
     * saved.
     */
    fun handle(command: DeclareAvailabilityCommand): DriverAvailabilityChanged? = transactionRunner.run {
        val driver = driverRepository.findById(command.driverId) ?: throw DriverNotFoundException(command.driverId)
        handle(driver, command)
    }

}
