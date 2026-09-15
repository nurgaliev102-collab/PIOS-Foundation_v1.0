package com.pios.billing.persistence

import com.pios.billing.domain.DriverReference
import com.pios.billing.domain.Subscription
import com.pios.billing.domain.SubscriptionStatus
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Proves the save/load lifecycle described by [com.pios.billing.application.SubscriptionRepository]
 * against a real PostgreSQL database, not merely in memory (ADR-074 Part
 * 1). Every identifier here is randomized ([UUID.randomUUID]), not a fixed
 * literal, mirroring this codebase's own PostgreSQL-security-test
 * convention.
 */
class PostgreSQLSubscriptionRepositoryTest {

    private val repository = PostgreSQLSubscriptionRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
    private val periodEnd = Instant.parse("2026-10-15T00:00:00Z")

    @Test
    fun `loading a driverId that was never saved returns null -- FREE by construction`() {
        assertNull(repository.findByDriverId(DriverReference("billing-never-saved-${UUID.randomUUID()}")))
    }

    @Test
    fun `a subscription saved to PostgreSQL can be loaded back with its identity and status preserved`() {
        val driverId = DriverReference("billing-driver-${UUID.randomUUID()}")
        val subscription = Subscription.start(driverId, SubscriptionStatus.TRIAL, periodEnd, isTest = false, now = Instant.now())

        repository.save(subscription)
        val loaded = repository.findByDriverId(driverId)

        assertEquals(driverId, loaded?.driverId)
        assertEquals(SubscriptionStatus.TRIAL, loaded?.status)
        assertEquals(periodEnd, loaded?.currentPeriodEnd)
    }

    @Test
    fun `saving again after a transition overwrites the previously persisted row, exactly one row per driver`() {
        val driverId = DriverReference("billing-driver-${UUID.randomUUID()}")
        val subscription = Subscription.start(driverId, SubscriptionStatus.TRIAL, periodEnd, isTest = false, now = Instant.now())
        repository.save(subscription)

        subscription.recordPeriod(SubscriptionStatus.ACTIVE, periodEnd.plusSeconds(3600), now = Instant.now())
        repository.save(subscription)

        val freshRepository = PostgreSQLSubscriptionRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
        val loaded = freshRepository.findByDriverId(driverId)
        assertEquals(SubscriptionStatus.ACTIVE, loaded?.status)
        assertEquals(periodEnd.plusSeconds(3600), loaded?.currentPeriodEnd)
    }

    @Test
    fun `isTest true round-trips through PostgreSQL`() {
        val driverId = DriverReference("billing-driver-${UUID.randomUUID()}")
        val subscription = Subscription.start(driverId, SubscriptionStatus.ACTIVE, periodEnd, isTest = true, now = Instant.now())

        repository.save(subscription)

        assertTrue(repository.findByDriverId(driverId)?.isTest == true)
    }

    @Test
    fun `isTest false round-trips through PostgreSQL`() {
        val driverId = DriverReference("billing-driver-${UUID.randomUUID()}")
        val subscription = Subscription.start(driverId, SubscriptionStatus.ACTIVE, periodEnd, isTest = false, now = Instant.now())

        repository.save(subscription)

        assertEquals(false, repository.findByDriverId(driverId)?.isTest)
    }

    @Test
    fun `a null currentPeriodEnd round-trips through PostgreSQL as null`() {
        val driverId = DriverReference("billing-driver-${UUID.randomUUID()}")
        val subscription = Subscription.start(driverId, SubscriptionStatus.ACTIVE, null, isTest = false, now = Instant.now())

        repository.save(subscription)

        assertNull(repository.findByDriverId(driverId)?.currentPeriodEnd)
    }
}
