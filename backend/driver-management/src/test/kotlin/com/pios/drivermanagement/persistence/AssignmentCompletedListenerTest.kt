package com.pios.drivermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.drivermanagement.application.AssignmentCompletedApplicationService
import com.pios.drivermanagement.domain.DriverId
import kotlin.test.Test
import kotlin.test.assertEquals

class AssignmentCompletedListenerTest {
    private val milestones = InMemoryDriverMilestonesRepository()
    private val clients = InMemoryDriverClientsRepository()
    private val service = AssignmentCompletedApplicationService(
        InMemoryAssignmentCompletedRepository(),
        milestones,
        InMemoryOrderPassengerRepository(),
        clients,
        InMemoryDriverRideStatedPricesRepository()
    )
    private val listener = AssignmentCompletedListener(service, ObjectMapper())

    @Test
    fun `additive executingDriverId receives execution credit`() {
        listener.onMessage(envelope(driverId = "committer", executingDriverId = "executor"))

        assertEquals(1L, milestones.findByDriverId(DriverId("executor"))?.completedRidesCount)
        assertEquals(null, milestones.findByDriverId(DriverId("committer")))
    }

    @Test
    fun `legacy envelope without executingDriverId remains compatible`() {
        listener.onMessage(envelope(driverId = "legacy-driver", executingDriverId = null))

        assertEquals(1L, milestones.findByDriverId(DriverId("legacy-driver"))?.completedRidesCount)
    }

    private fun envelope(driverId: String, executingDriverId: String?): String {
        val payload = linkedMapOf<String, Any?>(
            "orderId" to "order-listener",
            "driverId" to driverId
        )
        if (executingDriverId != null) payload["executingDriverId"] = executingDriverId
        return ObjectMapper().writeValueAsString(
            mapOf(
                "eventId" to "event-$driverId-${executingDriverId ?: "legacy"}",
                "eventType" to "AssignmentCompleted",
                "eventVersion" to 1,
                "occurredAt" to "2026-08-03T09:00:00Z",
                "payload" to payload
            )
        )
    }
}
