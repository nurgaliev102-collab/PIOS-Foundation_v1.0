package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.DriverClientRecord
import com.pios.drivermanagement.domain.DriverId
import org.springframework.stereotype.Service

/**
 * Server-side "Мой бизнес" read model (`docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md`
 * Part 5's read-model-only authorization for the Relationship stage):
 * application-layer coordination for the Retrieve Driver Clients query --
 * a read-only lookup, mirroring [RetrieveDriverMilestonesHandler]'s own
 * shape exactly. A driver with no completed ride recorded for any
 * passenger yet is not an error: [handle] returns an empty list rather than
 * throwing (this handler does not check driver existence at all, the same
 * "no milestones yet" precedent [RetrieveDriverMilestonesHandler] already
 * establishes).
 */
@Service
class RetrieveDriverClientsHandler(
    private val driverClientsRepository: DriverClientsRepository
) {
    fun handle(driverId: DriverId): List<DriverClientRecord> =
        driverClientsRepository.findAllForDriver(driverId)
}
