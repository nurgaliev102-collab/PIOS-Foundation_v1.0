package com.pios.dispatch.persistence

import com.pios.dispatch.application.TrustedDriverRecord
import com.pios.dispatch.application.TrustedDriverRepository
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.PassengerReference
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [TrustedDriverRepository] -- a fast,
 * dependency-free test double, mirroring [InMemoryPrimaryDriverRepository]'s
 * own established convention. Not a production storage mechanism.
 *
 * [findLongestIdleTrustedAvailable] has no notion of "idle time" of its
 * own -- unlike the real PostgreSQL adapter, this fake has no
 * `driver_availability.updated_at` to order by, so it is not used by
 * [FallbackDispatchApplicationServiceTest]'s own in-memory availability
 * fake. It exists so this repository can be constructed and exercised for
 * its set-membership behavior without a database.
 */
class InMemoryTrustedDriverRepository : TrustedDriverRepository {
    private val trustedPairs = ConcurrentHashMap.newKeySet<Pair<String, String>>()
    private val processedEventIds = ConcurrentHashMap.newKeySet<String>()

    override fun markProcessed(eventId: String): Boolean = processedEventIds.add(eventId)

    override fun add(record: TrustedDriverRecord) {
        trustedPairs.add(record.passengerReference.passengerId to record.driverId.driverId)
    }

    override fun remove(passengerReference: PassengerReference, driverId: DriverReference) {
        trustedPairs.remove(passengerReference.passengerId to driverId.driverId)
    }

    override fun findLongestIdleTrustedAvailable(
        passengerReference: PassengerReference,
        orderIsTest: Boolean,
        excluding: Set<DriverReference>
    ): DriverReference? = null

    fun isTrusted(passengerReference: PassengerReference, driverId: DriverReference): Boolean =
        (passengerReference.passengerId to driverId.driverId) in trustedPairs
}
