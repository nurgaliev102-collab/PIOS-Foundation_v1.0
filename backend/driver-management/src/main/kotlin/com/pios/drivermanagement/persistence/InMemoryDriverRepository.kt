package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.DriverRepository
import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [DriverRepository]. Proves the
 * save/load lifecycle a repository abstraction requires without
 * selecting or depending on any external database, ORM, or shared
 * storage — the specific storage technology remains intentionally
 * undecided until a future Database Design (PERSISTENCE_ARCHITECTURE.md
 * Section 8). Not a production storage mechanism: state is held only in
 * this process's memory and is lost when it ends.
 */
@Repository
class InMemoryDriverRepository : DriverRepository {
    private val store = ConcurrentHashMap<DriverId, Driver>()

    override fun save(driver: Driver) {
        store[driver.id] = driver
    }

    override fun findById(id: DriverId): Driver? = store[id]
}
