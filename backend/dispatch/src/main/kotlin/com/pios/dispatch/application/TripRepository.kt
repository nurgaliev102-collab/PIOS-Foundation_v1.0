package com.pios.dispatch.application

import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.Trip
import com.pios.dispatch.domain.TripId

/**
 * The persistence boundary for the Trip aggregate (ADR-063: Trip as a
 * New Dispatch-Owned Aggregate, Distinct from Assignment). Mirrors
 * [AssignmentRepository]'s own shape and conventions exactly: names only
 * what Dispatch's own domain needs, says nothing about storage
 * technology.
 *
 * [findByAssignmentId] exists specifically to support idempotent Trip
 * creation — the caller checks for an already-existing Trip before
 * calling [com.pios.dispatch.domain.Trip.create], mirroring
 * [AssignmentRepository.findByOrder]'s own role in
 * [com.pios.dispatch.domain.Assignment.create]'s duplicate-prevention
 * check.
 *
 * Only Dispatch persists or changes Trip information; no other module
 * implements or depends on this interface.
 */
interface TripRepository {
    fun save(trip: Trip)
    fun findById(id: TripId): Trip?
    fun findByAssignmentId(assignmentId: AssignmentId): Trip?
    fun findByExecutingDriver(driver: DriverReference): List<Trip>
}
