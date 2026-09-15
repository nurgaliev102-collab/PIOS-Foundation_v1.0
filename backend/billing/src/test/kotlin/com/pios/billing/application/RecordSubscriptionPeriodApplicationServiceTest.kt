package com.pios.billing.application

import com.pios.billing.domain.DriverReference
import com.pios.billing.domain.SubscriptionStatus
import com.pios.billing.persistence.InMemorySubscriptionRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RecordSubscriptionPeriodApplicationServiceTest {

    private val repository = InMemorySubscriptionRepository()
    private val service = RecordSubscriptionPeriodApplicationService(repository)
    private val driverId = DriverReference("driver-app-1")
    private val periodEnd = Instant.parse("2026-10-15T00:00:00Z")

    @Test
    fun `a driver with no record starts a brand-new record on FREE to TRIAL`() {
        val subscription = service.handle(RecordSubscriptionPeriodCommand(driverId, SubscriptionStatus.TRIAL, periodEnd, isTest = false))

        assertEquals(SubscriptionStatus.TRIAL, subscription.status)
        assertEquals(subscription, repository.findByDriverId(driverId))
    }

    @Test
    fun `a driver with no record starts a brand-new record on FREE to ACTIVE, isTest carried through`() {
        val subscription = service.handle(RecordSubscriptionPeriodCommand(driverId, SubscriptionStatus.ACTIVE, periodEnd, isTest = true))

        assertEquals(SubscriptionStatus.ACTIVE, subscription.status)
        assertTrue(subscription.isTest)
    }

    @Test
    fun `an already-existing record is transitioned in place, not replaced`() {
        service.handle(RecordSubscriptionPeriodCommand(driverId, SubscriptionStatus.TRIAL, periodEnd, isTest = false))

        val transitioned = service.handle(RecordSubscriptionPeriodCommand(driverId, SubscriptionStatus.ACTIVE, periodEnd.plusSeconds(3600), isTest = false))

        assertEquals(SubscriptionStatus.ACTIVE, transitioned.status)
        assertEquals(periodEnd.plusSeconds(3600), transitioned.currentPeriodEnd)
        assertEquals(transitioned, repository.findByDriverId(driverId))
    }

    @Test
    fun `isTest on a later command is ignored once the record already exists`() {
        service.handle(RecordSubscriptionPeriodCommand(driverId, SubscriptionStatus.TRIAL, periodEnd, isTest = true))

        val transitioned = service.handle(RecordSubscriptionPeriodCommand(driverId, SubscriptionStatus.ACTIVE, periodEnd, isTest = false))

        assertTrue(transitioned.isTest, "isTest must stay whatever it was set to at creation, never re-supplied")
    }

    @Test
    fun `an illegal transition against an existing record throws and does not mutate the persisted record`() {
        service.handle(RecordSubscriptionPeriodCommand(driverId, SubscriptionStatus.ACTIVE, periodEnd, isTest = false))

        assertFailsWith<IllegalStateException> {
            service.handle(RecordSubscriptionPeriodCommand(driverId, SubscriptionStatus.TRIAL, periodEnd, isTest = false))
        }

        assertEquals(SubscriptionStatus.ACTIVE, repository.findByDriverId(driverId)?.status)
    }

    @Test
    fun `starting a brand-new record already EXPIRED is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            service.handle(RecordSubscriptionPeriodCommand(driverId, SubscriptionStatus.EXPIRED, periodEnd, isTest = false))
        }
        assertEquals(null, repository.findByDriverId(driverId))
    }

    @Test
    fun `a driver never interacting with this module has no record, i_e_ is FREE by construction`() {
        assertEquals(null, repository.findByDriverId(DriverReference("driver-never-touched")))
    }
}
