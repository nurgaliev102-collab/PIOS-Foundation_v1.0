package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.DriverRideStatedPricesRepository
import com.pios.drivermanagement.domain.DriverId
import java.util.concurrent.ConcurrentHashMap

/**
 * ADR-065 (Driver Earnings from Self-Stated Prices): a fast,
 * dependency-free test double for [DriverRideStatedPricesRepository],
 * mirroring [InMemoryDriverClientsRepository]'s own precedent exactly.
 */
class InMemoryDriverRideStatedPricesRepository : DriverRideStatedPricesRepository {
    data class Record(val driverId: DriverId, val statedPriceRaw: String?, val statedPriceParsed: Long?)

    private val records = ConcurrentHashMap<String, Record>()

    override fun record(eventId: String, driverId: DriverId, statedPriceRaw: String?, statedPriceParsed: Long?) {
        records[eventId] = Record(driverId, statedPriceRaw, statedPriceParsed)
    }

    fun findByEventId(eventId: String): Record? = records[eventId]
}
