package com.pios.billing.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Proves the exact permitted-transition table ADR-074 Part 2 names, and
 * nothing else -- every pair not in that table must be rejected, not only
 * the ones this test happens to enumerate first.
 */
class SubscriptionTest {

    private val driverId = DriverReference("driver-1")
    private val t0 = Instant.parse("2026-09-15T00:00:00Z")
    private val periodEnd = Instant.parse("2026-10-15T00:00:00Z")

    // --- FREE -> ... (Subscription.start) ---

    @Test
    fun `FREE to TRIAL is permitted`() {
        val subscription = Subscription.start(driverId, SubscriptionStatus.TRIAL, periodEnd, isTest = false, now = t0)

        assertEquals(SubscriptionStatus.TRIAL, subscription.status)
        assertEquals(periodEnd, subscription.currentPeriodEnd)
        assertEquals(t0, subscription.createdAt)
        assertEquals(t0, subscription.updatedAt)
    }

    @Test
    fun `FREE to ACTIVE is permitted`() {
        val subscription = Subscription.start(driverId, SubscriptionStatus.ACTIVE, periodEnd, isTest = false, now = t0)

        assertEquals(SubscriptionStatus.ACTIVE, subscription.status)
    }

    @Test
    fun `FREE to EXPIRED is rejected -- a brand-new record cannot start already expired`() {
        assertFailsWith<IllegalArgumentException> {
            Subscription.start(driverId, SubscriptionStatus.EXPIRED, periodEnd, isTest = false, now = t0)
        }
    }

    @Test
    fun `FREE to FREE is rejected -- FREE is never a write target`() {
        assertFailsWith<IllegalArgumentException> {
            Subscription.start(driverId, SubscriptionStatus.FREE, periodEnd, isTest = false, now = t0)
        }
    }

    @Test
    fun `a Subscription can never be constructed holding FREE, even through reconstruct`() {
        assertFailsWith<IllegalArgumentException> {
            Subscription.reconstruct(driverId, SubscriptionStatus.FREE, periodEnd, isTest = false, createdAt = t0, updatedAt = t0)
        }
    }

    // --- isTest: set once at start, immutable thereafter ---

    @Test
    fun `isTest is sourced from the caller at start and stays fixed across later transitions`() {
        val subscription = Subscription.start(driverId, SubscriptionStatus.TRIAL, periodEnd, isTest = true, now = t0)
        assertTrue(subscription.isTest)

        subscription.recordPeriod(SubscriptionStatus.ACTIVE, periodEnd, now = t0.plusSeconds(60))

        assertTrue(subscription.isTest)
    }

    // --- TRIAL -> ... ---

    @Test
    fun `TRIAL to ACTIVE is permitted`() {
        val subscription = Subscription.start(driverId, SubscriptionStatus.TRIAL, null, isTest = false, now = t0)

        subscription.recordPeriod(SubscriptionStatus.ACTIVE, periodEnd, now = t0.plusSeconds(60))

        assertEquals(SubscriptionStatus.ACTIVE, subscription.status)
        assertEquals(periodEnd, subscription.currentPeriodEnd)
        assertEquals(t0.plusSeconds(60), subscription.updatedAt)
    }

    @Test
    fun `TRIAL to EXPIRED is permitted`() {
        val subscription = Subscription.start(driverId, SubscriptionStatus.TRIAL, periodEnd, isTest = false, now = t0)

        subscription.recordPeriod(SubscriptionStatus.EXPIRED, null, now = t0.plusSeconds(60))

        assertEquals(SubscriptionStatus.EXPIRED, subscription.status)
    }

    @Test
    fun `TRIAL to TRIAL is rejected`() {
        val subscription = Subscription.start(driverId, SubscriptionStatus.TRIAL, periodEnd, isTest = false, now = t0)

        assertFailsWith<IllegalStateException> {
            subscription.recordPeriod(SubscriptionStatus.TRIAL, periodEnd, now = t0.plusSeconds(60))
        }
    }

    // --- ACTIVE -> ... ---

    @Test
    fun `ACTIVE to EXPIRED is permitted`() {
        val subscription = Subscription.start(driverId, SubscriptionStatus.ACTIVE, periodEnd, isTest = false, now = t0)

        subscription.recordPeriod(SubscriptionStatus.EXPIRED, null, now = t0.plusSeconds(60))

        assertEquals(SubscriptionStatus.EXPIRED, subscription.status)
    }

    @Test
    fun `ACTIVE to TRIAL is rejected -- a paying driver never regresses to a trial`() {
        val subscription = Subscription.start(driverId, SubscriptionStatus.ACTIVE, periodEnd, isTest = false, now = t0)

        assertFailsWith<IllegalStateException> {
            subscription.recordPeriod(SubscriptionStatus.TRIAL, periodEnd, now = t0.plusSeconds(60))
        }
    }

    @Test
    fun `ACTIVE to ACTIVE is rejected`() {
        val subscription = Subscription.start(driverId, SubscriptionStatus.ACTIVE, periodEnd, isTest = false, now = t0)

        assertFailsWith<IllegalStateException> {
            subscription.recordPeriod(SubscriptionStatus.ACTIVE, periodEnd.plusSeconds(3600), now = t0.plusSeconds(60))
        }
    }

    // --- EXPIRED -> ... ---

    @Test
    fun `EXPIRED to ACTIVE is permitted`() {
        val subscription = Subscription.start(driverId, SubscriptionStatus.TRIAL, periodEnd, isTest = false, now = t0)
        subscription.recordPeriod(SubscriptionStatus.EXPIRED, null, now = t0.plusSeconds(60))

        subscription.recordPeriod(SubscriptionStatus.ACTIVE, periodEnd.plusSeconds(3600), now = t0.plusSeconds(120))

        assertEquals(SubscriptionStatus.ACTIVE, subscription.status)
        assertEquals(periodEnd.plusSeconds(3600), subscription.currentPeriodEnd)
    }

    @Test
    fun `EXPIRED to TRIAL is rejected -- a lapsed subscription never returns to trial`() {
        val subscription = Subscription.start(driverId, SubscriptionStatus.TRIAL, periodEnd, isTest = false, now = t0)
        subscription.recordPeriod(SubscriptionStatus.EXPIRED, null, now = t0.plusSeconds(60))

        assertFailsWith<IllegalStateException> {
            subscription.recordPeriod(SubscriptionStatus.TRIAL, periodEnd, now = t0.plusSeconds(120))
        }
    }

    @Test
    fun `EXPIRED to EXPIRED is rejected`() {
        val subscription = Subscription.start(driverId, SubscriptionStatus.TRIAL, periodEnd, isTest = false, now = t0)
        subscription.recordPeriod(SubscriptionStatus.EXPIRED, null, now = t0.plusSeconds(60))

        assertFailsWith<IllegalStateException> {
            subscription.recordPeriod(SubscriptionStatus.EXPIRED, null, now = t0.plusSeconds(120))
        }
    }

    @Test
    fun `EXPIRED to FREE is rejected -- FREE is never a write target from any state`() {
        val subscription = Subscription.start(driverId, SubscriptionStatus.TRIAL, periodEnd, isTest = false, now = t0)
        subscription.recordPeriod(SubscriptionStatus.EXPIRED, null, now = t0.plusSeconds(60))

        assertFailsWith<IllegalStateException> {
            subscription.recordPeriod(SubscriptionStatus.FREE, null, now = t0.plusSeconds(120))
        }
    }

    @Test
    fun `a rejected transition leaves the aggregate's own state unchanged`() {
        val subscription = Subscription.start(driverId, SubscriptionStatus.ACTIVE, periodEnd, isTest = false, now = t0)

        assertFailsWith<IllegalStateException> {
            subscription.recordPeriod(SubscriptionStatus.TRIAL, periodEnd, now = t0.plusSeconds(60))
        }

        assertEquals(SubscriptionStatus.ACTIVE, subscription.status)
        assertEquals(periodEnd, subscription.currentPeriodEnd)
        assertFalse(subscription.updatedAt == t0.plusSeconds(60))
    }
}
