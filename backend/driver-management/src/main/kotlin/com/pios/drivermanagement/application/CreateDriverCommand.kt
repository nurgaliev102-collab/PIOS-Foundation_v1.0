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
 * Carries only [driverId]: [com.pios.drivermanagement.domain.Driver]'s own
 * public constructor accepts no other field, and this sprint's own scope
 * deliberately introduces none (no name, phone, or vehicle information).
 */
data class CreateDriverCommand(val driverId: DriverId)
