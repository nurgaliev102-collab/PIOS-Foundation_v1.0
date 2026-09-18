package com.pios.dispatch.persistence

import com.pios.dispatch.application.HandoffRepository
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.Handoff
import com.pios.dispatch.domain.HandoffId
import com.pios.dispatch.domain.HandoffStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [HandoffRepository], mirroring
 * [InMemoryTripRepository] exactly. Not a production storage mechanism.
 */
class InMemoryHandoffRepository : HandoffRepository {
    private val store = ConcurrentHashMap<HandoffId, Handoff>()

    override fun save(handoff: Handoff) {
        store[handoff.id] = handoff
    }

    override fun findById(id: HandoffId): Handoff? = store[id]

    override fun findByAssignmentId(assignmentId: AssignmentId): List<Handoff> =
        store.values.filter { it.assignmentId == assignmentId }

    override fun findBySubstituteDriver(driverId: String): List<Handoff> =
        store.values.filter {
            it.substituteDriver.driverId == driverId &&
                (it.status == HandoffStatus.PROPOSED || it.status == HandoffStatus.SUBSTITUTE_ACCEPTED)
        }

    override fun findByOriginalDriver(driverId: String): List<Handoff> =
        store.values.filter { it.originalDriver.driverId == driverId }
}
