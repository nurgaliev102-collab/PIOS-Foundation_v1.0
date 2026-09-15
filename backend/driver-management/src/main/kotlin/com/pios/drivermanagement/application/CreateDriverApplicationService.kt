package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.Driver
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
 * No `DriverRegistered` (or equivalent) domain event is raised or
 * published: Sprint 3A's own scope explicitly excludes it (nothing in this
 * module consumes one today; introducing an unconsumed event is exactly
 * the premature abstraction this project's engineering discipline rejects)
 * -- unlike [DriverAvailabilityApplicationService], this service has no
 * [OutboxRepository] dependency at all, not merely a defaulted one.
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
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    fun handle(command: CreateDriverCommand): Driver = transactionRunner.run {
        if (driverRepository.findById(command.driverId) != null) {
            throw DriverAlreadyExistsException(command.driverId)
        }
        val invitedByDriverId = resolveInvitedByDriverId(command.driverId, command.invitedByDriverId)
        val driver = Driver(
            command.driverId,
            displayName = command.displayName,
            createdAt = Instant.now(),
            isTest = command.isTest,
            invitedByDriverId = invitedByDriverId
        )
        driverRepository.save(driver)
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
