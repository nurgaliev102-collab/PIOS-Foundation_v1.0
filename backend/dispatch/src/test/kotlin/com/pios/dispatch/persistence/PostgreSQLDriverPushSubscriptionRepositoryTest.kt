package com.pios.dispatch.persistence

import com.pios.dispatch.application.DriverPushSubscription
import com.pios.dispatch.domain.DriverReference
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves [DriverPushSubscriptionRepository]'s own upsert/find/delete
 * semantics against a real PostgreSQL database (ADR-083, D-10) -- also the
 * proof that `V25__driver_push_subscriptions.sql` applies cleanly, since
 * [PostgreSQLTestDatabase.dataSource] migrates on first access. Mirrors
 * [PostgreSQLDriverAvailabilityRepositoryTest]'s own style.
 */
class PostgreSQLDriverPushSubscriptionRepositoryTest {

    private val repository = PostgreSQLDriverPushSubscriptionRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))

    private fun driver() = DriverReference("push-repo-driver-${UUID.randomUUID()}")
    private fun endpoint() = "https://push.example.com/${UUID.randomUUID()}"

    @Test
    fun `a driver with no subscriptions has an empty result`() {
        assertTrue(repository.findByDriver(driver()).isEmpty())
    }

    @Test
    fun `upserting a subscription makes it findable by driver`() {
        val driver = driver()
        val endpoint = endpoint()

        repository.upsert(DriverPushSubscription(endpoint, driver, "p256dh-1", "auth-1"))

        val found = repository.findByDriver(driver)
        assertEquals(1, found.size)
        assertEquals(endpoint, found.single().endpoint)
        assertEquals("p256dh-1", found.single().p256dh)
        assertEquals("auth-1", found.single().auth)
    }

    @Test
    fun `upserting the same endpoint again replaces the keys and driver -- re-registration under a different driver reassigns the row`() {
        val firstDriver = driver()
        val secondDriver = driver()
        val endpoint = endpoint()

        repository.upsert(DriverPushSubscription(endpoint, firstDriver, "p256dh-old", "auth-old"))
        repository.upsert(DriverPushSubscription(endpoint, secondDriver, "p256dh-new", "auth-new"))

        assertTrue(repository.findByDriver(firstDriver).isEmpty())
        val found = repository.findByDriver(secondDriver)
        assertEquals(1, found.size)
        assertEquals("p256dh-new", found.single().p256dh)
        assertEquals("auth-new", found.single().auth)
    }

    @Test
    fun `deleteByDriverAndEndpoint removes only the row matching both`() {
        val driverA = driver()
        val driverB = driver()
        val endpointA = endpoint()
        repository.upsert(DriverPushSubscription(endpointA, driverA, "p256dh", "auth"))

        // Driver B deleting driver A's endpoint removes nothing.
        repository.deleteByDriverAndEndpoint(driverB, endpointA)
        assertEquals(1, repository.findByDriver(driverA).size)

        // Driver A deleting their own endpoint removes it.
        repository.deleteByDriverAndEndpoint(driverA, endpointA)
        assertTrue(repository.findByDriver(driverA).isEmpty())
    }

    @Test
    fun `deleteByEndpoint removes the row regardless of which driver it currently belongs to`() {
        val driver = driver()
        val endpoint = endpoint()
        repository.upsert(DriverPushSubscription(endpoint, driver, "p256dh", "auth"))

        repository.deleteByEndpoint(endpoint)

        assertTrue(repository.findByDriver(driver).isEmpty())
    }

    @Test
    fun `deleting a non-existent row is a harmless no-op`() {
        val driver = driver()

        repository.deleteByDriverAndEndpoint(driver, endpoint())
        repository.deleteByEndpoint(endpoint())

        assertTrue(repository.findByDriver(driver).isEmpty())
    }
}
