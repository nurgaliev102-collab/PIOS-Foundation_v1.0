package com.pios.dispatch.application

import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.Handoff
import com.pios.dispatch.domain.HandoffId
import com.pios.dispatch.domain.HandoffStatus

/**
 * The persistence boundary for the Handoff aggregate (D-07, Handoff
 * Protocol). Mirrors [TripRepository]/[AssignmentRepository]'s own shape
 * and conventions exactly: names only what Dispatch's own domain needs,
 * says nothing about storage technology.
 *
 * [findActiveByAssignmentId] exists specifically to support
 * [com.pios.dispatch.domain.Handoff.propose]'s own single-active-per-Assignment
 * invariant, mirroring [AssignmentRepository.findByOrder]'s own role in
 * [com.pios.dispatch.domain.Assignment.create]'s duplicate-prevention
 * check.
 *
 * Only Dispatch persists or changes Handoff information; no other module
 * implements or depends on this interface.
 */
interface HandoffRepository {
    fun save(handoff: Handoff)
    fun findById(id: HandoffId): Handoff?
    fun findByAssignmentId(assignmentId: AssignmentId): List<Handoff>
    fun findBySubstituteDriver(driverId: String): List<Handoff>

    /** The one non-terminal ([HandoffStatus.PROPOSED]/[HandoffStatus.SUBSTITUTE_ACCEPTED]) Handoff for [assignmentId], if any. */
    fun findActiveByAssignmentId(assignmentId: AssignmentId): Handoff? =
        findByAssignmentId(assignmentId).firstOrNull {
            it.status == HandoffStatus.PROPOSED || it.status == HandoffStatus.SUBSTITUTE_ACCEPTED
        }
}

/**
 * A no-op [HandoffRepository]. Used only as a default for callers with no
 * interest in Handoff persistence, mirroring [NoOpTripRepository]
 * exactly. Never used in a running application: Spring always finds and
 * injects the real [com.pios.dispatch.persistence.PostgreSQLHandoffRepository]
 * bean there instead.
 */
object NoOpHandoffRepository : HandoffRepository {
    override fun save(handoff: Handoff) = Unit
    override fun findById(id: HandoffId): Handoff? = null
    override fun findByAssignmentId(assignmentId: AssignmentId): List<Handoff> = emptyList()
    override fun findBySubstituteDriver(driverId: String): List<Handoff> = emptyList()
}
