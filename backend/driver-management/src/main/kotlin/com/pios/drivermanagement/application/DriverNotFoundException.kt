package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.DriverId

/**
 * Thrown when an application-layer operation needs the Driver identified
 * by [driverId], but [DriverRepository.findById] could not locate one
 * (PERSISTENCE_ARCHITECTURE.md Section 3, "Driver Management").
 *
 * This is an application-layer error, not a domain error: the Driver
 * aggregate itself has no notion of "not found" — that only exists at
 * the point something tries to look one up, which is an application
 * concern (APPLICATION_ARCHITECTURE.md Section 2). It is also not a
 * persistence error: the repository itself never throws for a miss
 * ([com.pios.drivermanagement.persistence.InMemoryDriverRepository.findById]
 * simply returns null); this exception is raised exactly once, here, at
 * the point the application decides it cannot proceed without the
 * aggregate.
 */
class DriverNotFoundException(val driverId: DriverId) :
    RuntimeException("Driver ${driverId.value} was not found")
