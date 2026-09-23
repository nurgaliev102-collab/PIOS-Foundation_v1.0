package com.pios.dispatch.application

import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.Trip
import com.pios.dispatch.domain.TripId

/**
 * A no-op [TripRepository]. Used only as
 * [DispatchAssignmentApplicationService]'s default when no real Trip
 * persistence is wired in — for example, existing tests exercising
 * Assignment lifecycle logic unrelated to Trip, constructed before this
 * task added Trip creation to that service, and left unmodified per
 * Task 11's own "do not weaken existing assertions" requirement. Mirrors
 * [NoOpOutboxRepository] exactly. Never used in a running application:
 * Spring always finds and injects the real
 * [com.pios.dispatch.persistence.PostgreSQLTripRepository] bean there
 * instead.
 */
object NoOpTripRepository : TripRepository {
    override fun save(trip: Trip) = Unit
    override fun findById(id: TripId): Trip? = null
    override fun findByAssignmentId(assignmentId: AssignmentId): Trip? = null
    override fun findByExecutingDriver(driver: DriverReference): List<Trip> = emptyList()
}
