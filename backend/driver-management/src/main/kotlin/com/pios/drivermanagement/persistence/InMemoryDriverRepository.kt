package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.DriverRepository
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [DriverRepository]. Originally proved
 * the save/load lifecycle a repository abstraction requires ahead of any
 * database technology decision (PERSISTENCE_ARCHITECTURE.md Section 8);
 * superseded as Driver Management's production adapter by
 * [PostgreSQLDriverRepository] (PostgreSQL Persistence Driver Management
 * v1.0, per ADR-025). Retained, deliberately not `@Repository`-annotated,
 * as a fast, dependency-free test double — several existing unit tests
 * across this module and its consumers construct it directly and would
 * otherwise require a live PostgreSQL connection for basic checks. Not a
 * production storage mechanism: state is held only in this process's
 * memory and is lost when it ends.
 */
class InMemoryDriverRepository : DriverRepository {
    private val store = ConcurrentHashMap<DriverId, Driver>()

    override fun save(driver: Driver) {
        store[driver.id] = driver
    }

    override fun findById(id: DriverId): Driver? = store[id]

    override fun findAll(): List<Driver> = store.values.toList()
}
