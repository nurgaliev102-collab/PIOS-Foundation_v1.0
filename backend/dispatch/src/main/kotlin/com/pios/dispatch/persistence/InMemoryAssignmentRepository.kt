package com.pios.dispatch.persistence

import com.pios.dispatch.application.AssignmentRepository
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentId
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [AssignmentRepository]. Proves the
 * save/load lifecycle a repository abstraction requires without
 * selecting or depending on any external database, ORM, or shared
 * storage — the specific storage technology remains intentionally
 * undecided until a future Database Design (PERSISTENCE_ARCHITECTURE.md
 * Section 8). Not a production storage mechanism: state is held only in
 * this process's memory and is lost when it ends.
 */
@Repository
class InMemoryAssignmentRepository : AssignmentRepository {
    private val store = ConcurrentHashMap<AssignmentId, Assignment>()

    override fun save(assignment: Assignment) {
        store[assignment.id] = assignment
    }

    override fun findById(id: AssignmentId): Assignment? = store[id]
}
