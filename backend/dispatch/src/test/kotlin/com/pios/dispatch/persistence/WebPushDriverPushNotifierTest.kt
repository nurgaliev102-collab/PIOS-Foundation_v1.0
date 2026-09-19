package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.DriverPushSubscription
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.ProposalId
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves [WebPushDriverPushNotifier]'s own send-path behavior (ADR-083,
 * D-10) -- entirely in memory, with a fake [DriverPushSender] standing in
 * for the real, network-calling `nl.martijndwars:web-push` send (this
 * class's own KDoc explains why that seam exists). No real Spring
 * transaction is opened here, so [TransactionSynchronizationManager.isSynchronizationActive]
 * is `false` throughout -- every send happens immediately, on the calling
 * thread, exactly the branch this class's own KDoc documents for a unit
 * test using no real transaction.
 */
class WebPushDriverPushNotifierTest {

    /** A same-thread [Executor] -- immaterial here since no real transaction is ever open, but keeps every notifier construction below identical. */
    private val immediateExecutor = Executor { it.run() }

    private val proposalId = ProposalId("push-notifier-proposal-1")
    private val driver = DriverReference("push-notifier-driver-1")

    private fun subscription(endpoint: String) =
        DriverPushSubscription(endpoint = endpoint, driverReference = driver, p256dh = "p256dh-value", auth = "auth-value")

    @Test
    fun `both driver notifications link to the DriverHome route`() {
        val repository = InMemoryDriverPushSubscriptionRepository()
        repository.upsert(subscription("endpoint-driver-home"))
        val payloads = mutableListOf<String>()
        val notifier = WebPushDriverPushNotifier(
            repository, immediateExecutor, "pub", "priv", "mailto:test@example.com",
            sender = { _, payload -> payloads += payload; 201 }
        )

        notifier.offerCreated(proposalId, driver)
        notifier.priceConfirmed(proposalId, driver)

        assertEquals(listOf("N1", "N2"), payloads.map { ObjectMapper().readTree(it)["kind"].asText() })
        assertTrue(payloads.all { ObjectMapper().readTree(it)["url"].asText() == "/" })
    }

    @Test
    fun `a driver with zero subscriptions is a silent no-op`() {
        val repository = InMemoryDriverPushSubscriptionRepository()
        var sendCount = 0
        val notifier = WebPushDriverPushNotifier(
            repository, immediateExecutor, "pub", "priv", "mailto:test@example.com",
            sender = { _, _ -> sendCount += 1; 201 }
        )

        notifier.offerCreated(proposalId, driver)

        assertEquals(0, sendCount)
    }

    @Test
    fun `404 from the push send deletes exactly that subscription row`() {
        val repository = InMemoryDriverPushSubscriptionRepository()
        repository.upsert(subscription("endpoint-404"))
        val notifier = WebPushDriverPushNotifier(
            repository, immediateExecutor, "pub", "priv", "mailto:test@example.com",
            sender = { _, _ -> 404 }
        )

        notifier.offerCreated(proposalId, driver)

        assertTrue(repository.findByDriver(driver).isEmpty())
    }

    @Test
    fun `410 from the push send deletes exactly that subscription row`() {
        val repository = InMemoryDriverPushSubscriptionRepository()
        repository.upsert(subscription("endpoint-410"))
        val notifier = WebPushDriverPushNotifier(
            repository, immediateExecutor, "pub", "priv", "mailto:test@example.com",
            sender = { _, _ -> 410 }
        )

        notifier.priceConfirmed(proposalId, driver)

        assertTrue(repository.findByDriver(driver).isEmpty())
    }

    @Test
    fun `other failures (429, 500) keep the subscription row, with no retry`() {
        val repository = InMemoryDriverPushSubscriptionRepository()
        repository.upsert(subscription("endpoint-429"))
        repository.upsert(subscription("endpoint-500"))
        var sendCount = 0
        val notifier = WebPushDriverPushNotifier(
            repository, immediateExecutor, "pub", "priv", "mailto:test@example.com",
            sender = { subscription, _ ->
                sendCount += 1
                if (subscription.endpoint == "endpoint-429") 429 else 500
            }
        )

        notifier.offerCreated(proposalId, driver)

        assertEquals(2, sendCount)
        assertEquals(2, repository.findByDriver(driver).size)
    }

    @Test
    fun `a 2xx response keeps the subscription row and deletes nothing`() {
        val repository = InMemoryDriverPushSubscriptionRepository()
        repository.upsert(subscription("endpoint-ok"))
        val notifier = WebPushDriverPushNotifier(
            repository, immediateExecutor, "pub", "priv", "mailto:test@example.com",
            sender = { _, _ -> 201 }
        )

        notifier.offerCreated(proposalId, driver)

        assertEquals(1, repository.findByDriver(driver).size)
    }

    @Test
    fun `a sender that throws never propagates to the caller`() {
        val repository = InMemoryDriverPushSubscriptionRepository()
        repository.upsert(subscription("endpoint-throws"))
        val notifier = WebPushDriverPushNotifier(
            repository, immediateExecutor, "pub", "priv", "mailto:test@example.com",
            sender = { _, _ -> throw RuntimeException("simulated push service outage") }
        )

        // Does not throw -- proven by reaching this line at all.
        notifier.offerCreated(proposalId, driver)
        notifier.priceConfirmed(proposalId, driver)

        // The row survives -- a thrown exception is treated like any other
        // non-404/410 failure, not as a deletion signal.
        assertEquals(1, repository.findByDriver(driver).size)
    }

    @Test
    fun `a blank VAPID private key disables sending -- fail-closed, and never throws`() {
        val repository = InMemoryDriverPushSubscriptionRepository()
        repository.upsert(subscription("endpoint-disabled"))
        val notifier = WebPushDriverPushNotifier(
            repository, immediateExecutor,
            vapidPublicKey = "pub",
            vapidPrivateKey = "",
            vapidSubject = "mailto:test@example.com"
        )

        notifier.offerCreated(proposalId, driver)

        // Nothing was deleted (no send was even attempted) and, most
        // importantly, no exception escaped -- even though no explicit
        // sender override was supplied and the configured "pub" string is
        // not a real VAPID key.
        assertEquals(1, repository.findByDriver(driver).size)
    }

    @Test
    fun `a blank VAPID public key also disables sending -- fail-closed`() {
        val repository = InMemoryDriverPushSubscriptionRepository()
        repository.upsert(subscription("endpoint-disabled-2"))
        val notifier = WebPushDriverPushNotifier(
            repository, immediateExecutor,
            vapidPublicKey = "",
            vapidPrivateKey = "priv",
            vapidSubject = "mailto:test@example.com"
        )

        notifier.offerCreated(proposalId, driver)

        assertEquals(1, repository.findByDriver(driver).size)
    }
}
