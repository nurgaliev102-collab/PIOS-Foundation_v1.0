package com.pios.dispatch.application

import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentId

/**
 * The persistence boundary for the Assignment aggregate
 * (PERSISTENCE_ARCHITECTURE.md Section 3, "Dispatch": "Persists the
 * Assignment logical entity"). This is the repository abstraction
 * Domain-Owned Persistence (PERSISTENCE_ARCHITECTURE.md Section 2)
 * requires: it names only what Dispatch's own domain needs — saving and
 * loading an Assignment by its identity — and says nothing about how or
 * where an Assignment is actually stored. No storage technology,
 * framework, or persistence implementation detail is named here or
 * anywhere in the domain layer.
 *
 * Only Dispatch persists or changes Assignment information
 * (PERSISTENCE_ARCHITECTURE.md Section 3); no other module implements or
 * depends on this interface.
 */
interface AssignmentRepository {
    fun save(assignment: Assignment)
    fun findById(id: AssignmentId): Assignment?
}
