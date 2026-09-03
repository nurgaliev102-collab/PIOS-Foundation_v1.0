package com.pios.dispatch.persistence

import com.pios.dispatch.application.PrimaryDriverRecord
import com.pios.dispatch.application.PrimaryDriverRepository
import com.pios.dispatch.domain.PassengerReference
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [PrimaryDriverRepository] -- a fast,
 * dependency-free test double, mirroring this codebase's own established
 * `InMemoryXxxRepository` convention (e.g. [InMemoryAssignmentRepository],
 * [InMemoryTripRepository]). Not a production storage mechanism.
 */
class InMemoryPrimaryDriverRepository : PrimaryDriverRepository {
    private val records = ConcurrentHashMap<String, PrimaryDriverRecord>()
    private val processedEventIds = ConcurrentHashMap.newKeySet<String>()

    override fun markProcessed(eventId: String): Boolean = processedEventIds.add(eventId)

    override fun upsert(record: PrimaryDriverRecord) {
        records[record.passengerReference.passengerId] = record
    }

    override fun clear(passengerReference: PassengerReference) {
        records.remove(passengerReference.passengerId)
    }

    override fun findByPassenger(passengerReference: PassengerReference): PrimaryDriverRecord? =
        records[passengerReference.passengerId]
}
