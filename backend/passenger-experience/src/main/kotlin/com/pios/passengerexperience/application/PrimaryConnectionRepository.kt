package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.ConnectionId
import com.pios.passengerexperience.domain.PassengerReference
import java.time.Instant

/**
 * The persistence boundary for a passenger's primary-driver designation
 * (ADR-054, Circle of Trust), following the same Domain-Owned Persistence
 * discipline as [ConnectionRepository]. Deliberately separate from
 * [ConnectionRepository] -- see ADR-054 Part 2 for why a primary
 * designation is a distinct, mutable-lifecycle concept kept in its own
 * `primary_connections` table rather than a column on `connections`.
 */
interface PrimaryConnectionRepository {
    fun findByPassenger(passengerReference: PassengerReference): ConnectionId?
    fun setPrimary(passengerReference: PassengerReference, connectionId: ConnectionId, designatedAt: Instant)
}
