package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.DriverPushNotifier
import com.pios.dispatch.application.DriverPushSubscription
import com.pios.dispatch.application.DriverPushSubscriptionRepository
import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.ProposalId
import nl.martijndwars.webpush.Notification
import nl.martijndwars.webpush.PushService
import nl.martijndwars.webpush.Subscription
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.security.Security
import java.util.concurrent.Executor

/**
 * The single outbound Web Push HTTP call for one subscription, returning
 * the push service's own HTTP status code -- extracted from
 * [WebPushDriverPushNotifier] as its own narrow interface purely for test
 * substitutability, mirroring this codebase's own established seam pattern
 * ([com.pios.dispatch.application.TransactionRunner],
 * [com.pios.dispatch.application.OrderGuard],
 * [com.pios.dispatch.application.EventPublisher]: a real, framework/library-
 * backed production implementation, replaceable by a test-only fake with no
 * change to the caller). [RealDriverPushSender] (this file, below) is the
 * real, `nl.martijndwars:web-push`-backed implementation; a test injects a
 * fake instead of exercising a real network call, to deterministically
 * prove the 404/410 deletion rule, the "keep the row on any other failure"
 * rule, and "a sender that throws never propagates to the caller".
 */
fun interface DriverPushSender {
    fun send(subscription: DriverPushSubscription, payload: String): Int
}

/** The real, `nl.martijndwars:web-push`-backed [DriverPushSender] (RFC 8291/8292 -- ADR-011: no hand-rolled cryptography). */
class RealDriverPushSender(private val pushService: PushService) : DriverPushSender {
    override fun send(subscription: DriverPushSubscription, payload: String): Int {
        val notification = Notification(
            Subscription(subscription.endpoint, Subscription.Keys(subscription.p256dh, subscription.auth)),
            payload
        )
        return pushService.send(notification).statusLine.statusCode
    }
}

/**
 * The real, Web-Push-backed [DriverPushNotifier] (ADR-083, D-10).
 *
 * ## Transaction boundary (ADR-083 Part 8)
 *
 * [offerCreated]/[priceConfirmed] never send anything themselves. Each
 * registers a [TransactionSynchronization.afterCommit] callback via
 * [TransactionSynchronizationManager] -- since [com.pios.dispatch.application.ProposalApplicationService.handle]
 * is frequently invoked from inside
 * [com.pios.dispatch.application.DispatchRequestApplicationService.routeSubmitted]'s
 * own outer transaction, registering the callback this way makes it
 * naturally attach to whichever transaction is outermost, regardless of
 * nesting depth -- the callback fires once, strictly after that outermost
 * transaction commits, and never at all if it rolls back.
 *
 * If [TransactionSynchronizationManager.isSynchronizationActive] is
 * `false` (no real Spring transaction is open -- e.g. a unit test using
 * [com.pios.dispatch.application.NoOpTransactionRunner]), the send happens
 * immediately instead. This branch is never exercised by real production
 * traffic, where a real Spring transaction is always active.
 *
 * ## Failure handling (ADR-083 Part 8)
 *
 * Every exception anywhere in the send path is caught here and logged,
 * never rethrown -- a push failure must never surface as an error on an
 * already-succeeded request, and must never alter Proposal/Assignment/
 * Dispatch business state. `404`/`410` from the push service deletes that
 * one subscription row (the standard stale-subscription signal); any other
 * failure (`429`, `5xx`, timeout) is logged and the row is kept, with no
 * retry -- a push is only useful inside the routing window; redelivering
 * one later would describe an order that may no longer exist.
 *
 * ## Fail-closed VAPID configuration (ADR-083 Part 8)
 *
 * A blank [vapidPrivateKey] or [vapidPublicKey] disables the whole push
 * path when [sender] is not itself explicitly supplied: [effectiveSender]
 * is then `null`, logged once, and every send becomes a silent no-op --
 * never a thrown error.
 *
 * [sender] defaults to `null` in production -- Spring wires no bean of
 * this functional-interface type, so [effectiveSender] always falls
 * through to the real, VAPID-backed [RealDriverPushSender] built from the
 * three `pios.push.vapid.*` properties. A test supplies [sender] directly.
 */
@Component
class WebPushDriverPushNotifier(
    private val subscriptionRepository: DriverPushSubscriptionRepository,
    private val driverPushExecutor: Executor,
    @Value("\${pios.push.vapid.public-key:}") private val vapidPublicKey: String,
    @Value("\${pios.push.vapid.private-key:}") private val vapidPrivateKey: String,
    @Value("\${pios.push.vapid.subject:}") private val vapidSubject: String,
    private val sender: DriverPushSender? = null
) : DriverPushNotifier {

    private val logger = LoggerFactory.getLogger(WebPushDriverPushNotifier::class.java)
    private val objectMapper = ObjectMapper()

    init {
        // Idempotent: safe to call even if another module in this same JVM
        // already registered it. Required by the underlying library's own
        // key-loading code (KeyFactory.getInstance(..., "BC")).
        Security.addProvider(BouncyCastleProvider())
    }

    private val effectiveSender: DriverPushSender? by lazy { sender ?: buildRealSenderOrNull() }

    override fun offerCreated(proposalId: ProposalId, driver: DriverReference) {
        registerAfterCommit(driver, "N1", "${proposalId.value}:OPEN")
    }

    override fun priceConfirmed(proposalId: ProposalId, driver: DriverReference) {
        registerAfterCommit(driver, "N2", "${proposalId.value}:ACCEPTED")
    }

    private fun registerAfterCommit(driver: DriverReference, kind: String, tag: String) {
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                    override fun afterCommit() {
                        driverPushExecutor.execute { sendSafely(driver, kind, tag) }
                    }
                })
            } else {
                sendSafely(driver, kind, tag)
            }
        } catch (t: Throwable) {
            logger.warn("Failed to register driver push after-commit hook for kind {}", kind, t)
        }
    }

    private fun sendSafely(driver: DriverReference, kind: String, tag: String) {
        try {
            send(driver, kind, tag)
        } catch (t: Throwable) {
            logger.warn("Driver push send failed for kind {}", kind, t)
        }
    }

    private fun send(driver: DriverReference, kind: String, tag: String) {
        val activeSender = effectiveSender ?: return
        val subscriptions = subscriptionRepository.findByDriver(driver)
        if (subscriptions.isEmpty()) {
            return
        }
        val payload = objectMapper.writeValueAsString(mapOf("kind" to kind, "tag" to tag, "url" to "/driver"))
        subscriptions.forEach { subscription ->
            try {
                val statusCode = activeSender.send(subscription, payload)
                if (statusCode == 404 || statusCode == 410) {
                    subscriptionRepository.deleteByEndpoint(subscription.endpoint)
                }
            } catch (t: Throwable) {
                logger.warn("Driver push send failed for one subscription", t)
            }
        }
    }

    private fun buildRealSenderOrNull(): DriverPushSender? {
        if (vapidPrivateKey.isBlank() || vapidPublicKey.isBlank()) {
            logger.info("Driver push disabled: VAPID keys are not configured (pios.push.vapid.*)")
            return null
        }
        return try {
            RealDriverPushSender(PushService(vapidPublicKey, vapidPrivateKey, vapidSubject))
        } catch (t: Throwable) {
            logger.warn("Driver push disabled: failed to initialize the VAPID-backed PushService", t)
            null
        }
    }
}
