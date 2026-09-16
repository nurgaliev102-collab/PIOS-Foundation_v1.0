package com.pios.drivermanagement.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverAvailabilityChanged
import com.pios.drivermanagement.domain.DriverId
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Application-layer coordination for the Create Driver command (Sprint 3A:
 * MVR Pilot Enablement). This service sequences the command into a new
 * [Driver] instance; it decides nothing about the driver's own business
 * meaning beyond existence, mirroring
 * [DriverAvailabilityApplicationService]'s own "Domain Decides Business
 * Meaning" discipline -- there is barely any decision to make here, since
 * [Driver]'s own constructor is public and unconditional (see that class's
 * own KDoc).
 *
 * Explicitly checks [DriverRepository.findById] before saving, and throws
 * [DriverAlreadyExistsException] if a Driver with this id already exists --
 * required because [DriverRepository.save]'s real, PostgreSQL-backed
 * implementation is an upsert
 * ([com.pios.drivermanagement.persistence.PostgreSQLDriverRepository]'s own
 * `INSERT ... ON CONFLICT DO UPDATE`) and would otherwise silently
 * overwrite an existing driver's availability instead of rejecting the
 * request.
 *
 * Registration writes an initial `DriverAvailabilityChanged` projection
 * fact (`UNAVAILABLE`) into the same transactional outbox as later
 * availability changes. This does not claim the driver is on line; it lets
 * Dispatch know the new driver's existence and test classification even
 * before their first availability toggle, which advance direct requests
 * need. No separate `DriverRegistered` public contract is introduced.
 *
 * [transactionRunner] defaults to [NoOpTransactionRunner], mirroring every
 * other application service in this module, so tests exercising this
 * service without a real database continue to work unchanged; a real,
 * Spring-wired instance always receives
 * [com.pios.drivermanagement.persistence.SpringTransactionRunner] instead.
 * This does not close every concurrent-creation race (two simultaneous
 * requests for the same new [com.pios.drivermanagement.domain.DriverId]
 * could both pass the existence check before either saves) -- accepted for
 * this sprint's own scope (a small, manually-coordinated pilot), not a
 * general guarantee.
 *
 * ## ADR-073: Driver-to-Driver Referral -- Single-Hop Origin Fact
 *
 * [command]'s own [CreateDriverCommand.invitedByDriverId] is resolved to
 * the actual value stored on the new [Driver] here, per ADR-073 Part 2 --
 * never in the domain constructor, never at the transport boundary:
 *
 * - Self-reference (`invitedByDriverId == driverId`) degrades to `null`.
 * - An `invitedByDriverId` that identifies no existing, already-saved
 *   Driver degrades to `null` -- a stale or mistyped inviter code must
 *   never fail the *registering* driver's own request.
 * - Otherwise, the value is stored unchanged.
 *
 * This is the only place this field is ever set; it is never updated
 * afterward (no method on [Driver] changes it), the same "set once, at
 * creation" immutability [isTest] and [Driver.createdAt] already have.
 */
@Service
class CreateDriverApplicationService(
    private val driverRepository: DriverRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner,
    private val outboxRepository: OutboxRepository = NoOpOutboxRepository,
    private val objectMapper: ObjectMapper = ObjectMapper()
) {
    fun handle(command: CreateDriverCommand): Driver = transactionRunner.run {
        if (driverRepository.findById(command.driverId) != null) {
            throw DriverAlreadyExistsException(command.driverId)
        }
        val invitedByDriverId = resolveInvitedByDriverId(command.driverId, command.invitedByDriverId)
        val registeredAt = Instant.now()
        val driver = Driver(
            command.driverId,
            displayName = command.displayName,
            createdAt = registeredAt,
            isTest = command.isTest,
            invitedByDriverId = invitedByDriverId
        )
        driverRepository.save(driver)
        // The first UNAVAILABLE state is a real projection fact. Without it,
        // a newly registered driver has no Dispatch record until toggling
        // availability and cannot even consider a future direct request.
        outboxRepository.save(
            DriverAvailabilityOutboxRecordFactory.create(
                DriverAvailabilityChanged(driver.id, driver.availability, driver.isTest, registeredAt),
                objectMapper
            )
        )
        driver
    }

    /**
     * ADR-073 Part 2's own validation: a self-referencing or
     * unrecognized [invitedByDriverId] must never fail registration --
     * only ever degrade to `null`.
     */
    private fun resolveInvitedByDriverId(driverId: DriverId, invitedByDriverId: String?): String? {
        if (invitedByDriverId.isNullOrBlank()) {
            return null
        }
        if (invitedByDriverId == driverId.value) {
            return null
        }
        return driverRepository.findById(DriverId(invitedByDriverId))?.id?.value
    }
}
