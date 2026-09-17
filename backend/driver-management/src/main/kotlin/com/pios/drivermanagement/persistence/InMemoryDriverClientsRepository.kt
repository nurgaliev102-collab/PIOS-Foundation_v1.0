package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.DriverClientsRepository
import com.pios.drivermanagement.domain.DriverClientRecord
import com.pios.drivermanagement.domain.DriverId
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
 * Section 2.1's own disclosed gap, now closed): a fast, dependency-free
 * test double for [DriverClientsRepository], mirroring
 * [InMemoryDriverRepository]'s own precedent exactly.
 */
class InMemoryDriverClientsRepository : DriverClientsRepository {
    private val records = ConcurrentHashMap<Pair<DriverId, String>, DriverClientRecord>()

    override fun recordRideForClient(driverId: DriverId, passengerReference: String, occurredAt: Instant): Boolean {
        val key = driverId to passengerReference
        val existing = records[key]
        val updated = DriverClientRecord(
            driverId = driverId,
            passengerReference = passengerReference,
            rideCount = (existing?.rideCount ?: 0) + 1,
            lastRideAt = occurredAt
        )
        records[key] = updated
        return updated.rideCount == REPEAT_CLIENT_THRESHOLD
    }

    // Mirrors the Postgres adapter's own `ORDER BY last_ride_at DESC NULLS
    // LAST`: `Instant.MIN` is never a real ride timestamp, so treating a
    // missing [DriverClientRecord.lastRideAt] as that sentinel sorts it to
    // the end of a descending list, exactly like SQL's own NULLS LAST.
    override fun findAllForDriver(driverId: DriverId): List<DriverClientRecord> =
        records.values
            .filter { it.driverId == driverId }
            .sortedByDescending { it.lastRideAt ?: Instant.MIN }

    companion object {
        private const val REPEAT_CLIENT_THRESHOLD = 2
    }
}
