package com.pios.dispatch.persistence

import com.pios.dispatch.application.TripRepository
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.Trip
import com.pios.dispatch.domain.TripId
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [TripRepository], mirroring
 * [InMemoryAssignmentRepository] exactly. Not a production storage
 * mechanism: state is held only in this process's memory and is lost
 * when it ends — used as a fast, dependency-free test double.
 */
class InMemoryTripRepository : TripRepository {
    private val store = ConcurrentHashMap<TripId, Trip>()

    override fun save(trip: Trip) {
        store[trip.id] = trip
    }

    override fun findById(id: TripId): Trip? = store[id]

    override fun findByAssignmentId(assignmentId: AssignmentId): Trip? =
        store.values.firstOrNull { it.assignmentId == assignmentId }

    override fun findByExecutingDriver(driver: DriverReference): List<Trip> =
        store.values.filter { it.executingDriver == driver }
}
