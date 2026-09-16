package com.pios.drivermanagement.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.drivermanagement.domain.DriverAvailabilityChanged
import java.util.UUID

/** One wire contract for both initial availability and later changes. */
internal object DriverAvailabilityOutboxRecordFactory {
    fun create(event: DriverAvailabilityChanged, objectMapper: ObjectMapper): OutboxRecord = OutboxRecord(
        aggregateId = event.driverId.value,
        eventType = "DriverAvailabilityChanged",
        routingKey = "driver.availability.changed",
        payload = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to UUID.randomUUID().toString(),
                "eventType" to "DriverAvailabilityChanged",
                "eventVersion" to 1,
                "occurredAt" to event.occurredAt.toString(),
                "payload" to mapOf(
                    "driverId" to event.driverId.value,
                    "availability" to event.availability.name,
                    "isTest" to event.isTest
                )
            )
        )
    )
}
