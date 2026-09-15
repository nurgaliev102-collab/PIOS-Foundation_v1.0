package com.pios.billing.application

import com.pios.billing.domain.DriverReference
import com.pios.billing.domain.SubscriptionStatus
import com.pios.billing.persistence.InMemorySubscriptionRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RetrieveSubscriptionHandlerTest {

    private val repository = InMemorySubscriptionRepository()
    private val recordService = RecordSubscriptionPeriodApplicationService(repository)
    private val handler = RetrieveSubscriptionHandler(repository)
    private val periodEnd = Instant.parse("2026-10-15T00:00:00Z")

    @Test
    fun `a driver with no record reads as FREE with a null currentPeriodEnd`() {
        val view = handler.handle(DriverReference("driver-never-touched"))

        assertEquals(SubscriptionStatus.FREE, view.status)
        assertNull(view.currentPeriodEnd)
    }

    @Test
    fun `a driver with a persisted record reads its real status and currentPeriodEnd`() {
        val driverId = DriverReference("driver-read-1")
        recordService.handle(RecordSubscriptionPeriodCommand(driverId, SubscriptionStatus.ACTIVE, periodEnd, isTest = false))

        val view = handler.handle(driverId)

        assertEquals(SubscriptionStatus.ACTIVE, view.status)
        assertEquals(periodEnd, view.currentPeriodEnd)
    }
}
