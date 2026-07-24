package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId

/**
 * The persistence boundary for the Driver aggregate
 * (PERSISTENCE_ARCHITECTURE.md Section 3, "Driver Management": "Persists
 * the Driver logical entity, including its availability information").
 * This is the repository abstraction Domain-Owned Persistence
 * (PERSISTENCE_ARCHITECTURE.md Section 2) requires: it names only what
 * Driver Management's own domain needs — saving and loading a Driver by
 * its identity — and says nothing about how or where a Driver is
 * actually stored. No storage technology, framework, or persistence
 * implementation detail is named here or anywhere in the domain layer.
 *
 * Only Driver Management persists a driver's availability state
 * (PERSISTENCE_ARCHITECTURE.md Section 3); no other module implements or
 * depends on this interface.
 *
 * [findAll] added by Sprint FR-002 (Driver Availability), for the
 * coordinator's own list-of-drivers view — no filtering or ordering is
 * named here, consistent with that sprint's own scope.
 */
interface DriverRepository {
    fun save(driver: Driver)
    fun findById(id: DriverId): Driver?
    fun findAll(): List<Driver>
}
