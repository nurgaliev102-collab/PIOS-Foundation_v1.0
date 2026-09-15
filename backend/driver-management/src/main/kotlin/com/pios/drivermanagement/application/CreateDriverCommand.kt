package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.DriverId

/**
 * The Create Driver command (Sprint 3A: MVR Pilot Enablement). Represents
 * an operator's intention to bring a new Driver record into existence for
 * a real pilot driver -- a capability [DriverAvailabilityApplicationService]'s
 * own `declareAvailability` assumes already exists (it requires an
 * already-saved [com.pios.drivermanagement.domain.Driver], confirmed by
 * [DriverNotFoundException]) but that, until this command, nothing in this
 * module actually provided.
 *
 * Carries [driverId] and, since Sprint 7B (Personal Network Flow MVP),
 * [displayName] -- the only other field
 * [com.pios.drivermanagement.domain.Driver]'s own public constructor
 * accepts.
 *
 * [isTest] (Owner Control Center test/production data separation,
 * 2026-08-17) defaults to `false`, mirroring [displayName]'s own
 * backward-compatible defaulting.
 *
 * [invitedByDriverId] (ADR-073, Driver-to-Driver Referral -- Single-Hop
 * Origin Fact) carries [com.pios.drivermanagement.api.CreateDriverRequest]'s
 * own field unchanged -- validation (self-reference and existence checks,
 * both degrading to `null` rather than failing the command) happens in
 * [CreateDriverApplicationService.handle], not here; this command is a
 * plain carrier, mirroring [isTest]'s own division of responsibility.
 */
data class CreateDriverCommand(
    val driverId: DriverId,
    val displayName: String? = null,
    val isTest: Boolean = false,
    val invitedByDriverId: String? = null
)
