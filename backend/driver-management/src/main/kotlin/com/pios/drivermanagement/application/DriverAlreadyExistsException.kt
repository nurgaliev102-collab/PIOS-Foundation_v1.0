package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.DriverId

/**
 * Thrown when Create Driver is attempted for a [driverId] that already has
 * a saved Driver record. This is an application-layer error, not a domain
 * one: [com.pios.drivermanagement.domain.Driver] itself has no notion of
 * "already exists" -- that only exists at the point creation is attempted
 * against an already-populated store, which is an application concern
 * (APPLICATION_ARCHITECTURE.md Section 2), mirroring
 * [DriverNotFoundException]'s own reasoning for the opposite case.
 *
 * [com.pios.drivermanagement.persistence.PostgreSQLDriverRepository.save]'s
 * own SQL is an upsert (`INSERT ... ON CONFLICT DO UPDATE`) -- it would
 * silently overwrite an existing driver's availability rather than reject
 * the request, which is exactly why [CreateDriverApplicationService]
 * checks [DriverRepository.findById] itself before calling
 * [DriverRepository.save], instead of relying on the repository to reject
 * a duplicate.
 */
class DriverAlreadyExistsException(val driverId: DriverId) :
    RuntimeException("Driver ${driverId.value} already exists")
