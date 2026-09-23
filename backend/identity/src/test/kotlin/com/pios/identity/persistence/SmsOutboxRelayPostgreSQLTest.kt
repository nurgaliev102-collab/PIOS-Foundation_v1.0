package com.pios.identity.persistence

import com.pios.identity.application.OutboundSmsPort
import com.pios.identity.application.PasswordHasher
import com.pios.identity.application.ConfirmRecoveryApplicationService
import com.pios.identity.application.ConfirmRecoveryCommand
import com.pios.identity.application.ConfirmRecoveryOutcome
import com.pios.identity.application.CredentialRepository
import com.pios.identity.application.IdentityRepository
import com.pios.identity.application.PhoneVerificationChallengeRepository
import com.pios.identity.application.PhoneVerificationChallengeIssuer
import com.pios.identity.application.SessionTokenIssuer
import com.pios.identity.application.SmsOutboxRelay
import com.pios.identity.application.SmsOutboxRepository
import com.pios.identity.application.SmsOutboxStatus
import com.pios.identity.application.SmsRetryJitter
import com.pios.identity.application.testOtpCipher
import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.PasswordCredential
import com.pios.identity.domain.Phone
import com.pios.identity.domain.PhoneVerificationChallenge
import com.pios.identity.domain.PhoneVerificationPurpose
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real-PostgreSQL proof of C-2 dispatch authorization and lease fencing. */
class SmsOutboxRelayPostgreSQLTest {
    private val jdbc = JdbcTemplate(PostgreSQLTestDatabase.dataSource)
    private val transactionTemplate = TransactionTemplate(DataSourceTransactionManager(PostgreSQLTestDatabase.dataSource))
    private val transactions = SpringTransactionRunner(transactionTemplate)
    private val identities = PostgreSQLIdentityRepository(jdbc)
    private val challenges = PostgreSQLPhoneVerificationChallengeRepository(jdbc)
    private val outbox = PostgreSQLSmsOutboxRepository(jdbc)
    private val guard = PostgreSQLPhoneVerificationChallengeGuard(jdbc)
    private val hasher = PasswordHasher(defaultIterations = 1_000)

    private fun issue(maxAttempts: Int = 2): Issued {
        val phone = uniquePhone()
        val identity = Identity(IdentityId(UUID.randomUUID().toString()), phone, null, Instant.now()).withPhoneVerified(Instant.now())
        identities.save(identity)
        val issuer = PhoneVerificationChallengeIssuer(
            challenges, hasher, testOtpCipher(), outbox, transactions,
            ttlSeconds = 600, maxAttempts = maxAttempts, codeDigits = 6, challengeGuard = guard
        )
        issuer.issue(identity.id, phone, PhoneVerificationPurpose.RECOVERY)
        val challengeId = jdbc.queryForObject(
            "SELECT id FROM phone_verification_challenges WHERE identity_id = ? AND purpose = 'RECOVERY' ORDER BY created_at DESC LIMIT 1",
            String::class.java, identity.id.value
        )!!
        // This shared Flyway test database retains rows from other test
        // classes. Make this fixture due and first in FIFO before using the
        // production claim query, rather than manufacturing PROCESSING.
        jdbc.update(
            "UPDATE identity_sms_outbox SET next_attempt_at = now(), created_at = TIMESTAMPTZ '1970-01-01 00:00:00+00' WHERE challenge_id = ?",
            challengeId
        )
        // Each race now starts from a real FIFO/SKIP LOCKED batch-50 claim
        // with a current claim token and 60-second lease.
        val claimed = outbox.claimBatch(50, Duration.ofSeconds(60))
            .firstOrNull { it.challengeId == challengeId }
        assertNotNull(claimed, "relay did not claim the newly issued outbox row")
        return Issued(identity, phone, challengeId, claimed, issuer)
    }

    private fun relay(port: RecordingPort) = SmsOutboxRelay(
        outbox, port, testOtpCipher(), Clock.systemUTC(), SmsRetryJitter { 0L },
        batchSize = 50, leaseSeconds = 60, initialBackoffMs = 30_000,
        maxBackoffMs = 120_000, maxRetryWindowMs = 300_000
    )

    @Test
    fun `supersession committed while authorization waits prevents provider submission`() {
        val issued = issue()
        runInvalidationRace(issued) { allowCommit, challengeLockHeld ->
            val blockingOutbox = object : SmsOutboxRepository by outbox {
                override fun insert(record: com.pios.identity.application.SmsOutboxRecord) {
                    // supersedeLive has updated the old challenge row before
                    // insertion of the replacement outbox row reaches here.
                    challengeLockHeld()
                    check(allowCommit.await(10, TimeUnit.SECONDS)) { "supersession transaction was not released" }
                    outbox.insert(record)
                }
            }
            PhoneVerificationChallengeIssuer(
                challenges, hasher, testOtpCipher(), blockingOutbox, transactions,
                ttlSeconds = 600, maxAttempts = 2, codeDigits = 6, challengeGuard = guard
            ).issue(issued.identity.id, issued.phone, PhoneVerificationPurpose.RECOVERY)
        }
    }

    @Test
    fun `consumption committed while authorization waits prevents provider submission`() {
        val issued = issue()
        val credentials = PostgreSQLCredentialRepository(jdbc)
        val originalPassword = hasher.hash("original-password-for-race")
        credentials.save(PasswordCredential(issued.identity.id, originalPassword.hash, originalPassword.salt, originalPassword.iterations, Instant.now()))
        val code = testOtpCipher().decrypt(
            assertNotNull(challenges.findById(issued.challengeId)).otpCiphertext!!,
            assertNotNull(challenges.findById(issued.challengeId)).otpNonce!!
        )
        runInvalidationRace(issued) { allowCommit, challengeLockHeld ->
            val blockingIdentities = BlockingIdentityRepository(identities, allowCommit, challengeLockHeld)
            val service = ConfirmRecoveryApplicationService(
                blockingIdentities, credentials, challenges, hasher,
                SessionTokenIssuer(java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 7 }), 600), transactions
            )
            assertIs<ConfirmRecoveryOutcome.Success>(
                service.handle(ConfirmRecoveryCommand(issued.phone.value, code, "replacement-password-for-race"))
            )
        }
    }

    @Test
    fun `expiry committed while authorization waits prevents provider submission`() {
        val issued = issue()
        runInvalidationRace(issued) { allowCommit, challengeLockHeld ->
            transactions.run {
                jdbc.update("UPDATE phone_verification_challenges SET expires_at = now() - INTERVAL '1 second' WHERE id = ?", issued.challengeId)
                challengeLockHeld()
                check(allowCommit.await(10, TimeUnit.SECONDS)) { "expiry transaction was not released" }
            }
        }
    }

    @Test
    fun `attempt exhaustion committed while authorization waits prevents provider submission`() {
        val issued = issue(maxAttempts = 1)
        val code = testOtpCipher().decrypt(
            assertNotNull(challenges.findById(issued.challengeId)).otpCiphertext!!,
            assertNotNull(challenges.findById(issued.challengeId)).otpNonce!!
        )
        val wrongCode = if (code == "000000") "999999" else "000000"
        runInvalidationRace(issued) { allowCommit, challengeLockHeld ->
            val blockingChallenges = BlockingChallengeRepository(challenges, allowCommit, challengeLockHeld)
            val service = ConfirmRecoveryApplicationService(
                identities, PostgreSQLCredentialRepository(jdbc), blockingChallenges, hasher,
                SessionTokenIssuer(java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 8 }), 600), transactions
            )
            assertEquals(
                ConfirmRecoveryOutcome.Failure,
                service.handle(ConfirmRecoveryCommand(issued.phone.value, wrongCode, "replacement-password-for-race"))
            )
        }
    }

    @Test
    fun `only one concurrent execution obtains the one-time dispatch authorization`() {
        val issued = issue()
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit(Callable {
                gate.await()
                outbox.authorizeDispatch(issued.claimed.id!!, issued.claimed.claimToken!!, Duration.ofSeconds(60))
            })
            val second = pool.submit(Callable {
                gate.await()
                outbox.authorizeDispatch(issued.claimed.id!!, issued.claimed.claimToken!!, Duration.ofSeconds(60))
            })
            gate.countDown()
            val authorizations = listOf(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))
            assertEquals(1, authorizations.count { it != null })
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `expired lease is reclaimed and stale terminal transition cannot overwrite new owner`() {
        val issued = issue()
        val authorizationA = assertNotNull(
            outbox.authorizeDispatch(issued.claimed.id!!, issued.claimed.claimToken!!, Duration.ofSeconds(60))
        )
        jdbc.update(
            "UPDATE identity_sms_outbox SET lease_until = now() - INTERVAL '1 second', created_at = now() - INTERVAL '1 day' WHERE id = ?",
            issued.claimed.id
        )
        val reclaimed = outbox.claimBatch(50, Duration.ofSeconds(60)).first { it.challengeId == issued.challengeId }
        val authorizationB = assertNotNull(
            outbox.authorizeDispatch(reclaimed.id!!, reclaimed.claimToken!!, Duration.ofSeconds(60))
        )

        // Simulates a slow provider return after A's renewed lease expired:
        // A's external result is fenced out and cannot overwrite B.
        assertFalse(outbox.markSent(issued.claimed.id!!, authorizationA.claimToken, 1001L, Instant.now()))
        assertTrue(outbox.markSent(reclaimed.id!!, authorizationB.claimToken, 1002L, Instant.now()))
        val stored = assertNotNull(outbox.findByChallengeId(issued.challengeId))
        assertEquals(SmsOutboxStatus.SENT, stored.status)
        assertEquals(1002L, stored.providerMessageId)
    }

    @Test
    fun `issuer transaction rolls back challenge when outbox insertion fails`() {
        val phone = uniquePhone()
        val identity = Identity(IdentityId(UUID.randomUUID().toString()), phone, null, Instant.now()).withPhoneVerified(Instant.now())
        identities.save(identity)
        val failingOutbox = object : SmsOutboxRepository by outbox {
            override fun insert(record: com.pios.identity.application.SmsOutboxRecord) {
                error("forced outbox failure")
            }
        }
        val issuer = PhoneVerificationChallengeIssuer(
            challenges, hasher, testOtpCipher(), failingOutbox, transactions,
            ttlSeconds = 600, maxAttempts = 2, codeDigits = 6, challengeGuard = guard
        )

        assertTrue(runCatching { issuer.issue(identity.id, phone, PhoneVerificationPurpose.RECOVERY) }.isFailure)
        val count = jdbc.queryForObject(
            "SELECT count(*) FROM phone_verification_challenges WHERE identity_id = ? AND purpose = 'RECOVERY'",
            Long::class.java, identity.id.value
        )!!
        assertEquals(0L, count)
        assertNull(outbox.findByChallengeId("no-row-for-${identity.id.value}"))
    }

    /**
     * Holds a real invalidating transaction after it has locked the challenge.
     * Before allowing that transaction to commit, the monitor observes the
     * relay's real `authorizeDispatch` CTE in pg_stat_activity, waiting on a
     * PostgreSQL lock held by this transaction's backend PID. This is a
     * database-state rendezvous, not a scheduler-delay inference.
     */
    private fun runInvalidationRace(
        issued: Issued,
        invalidateInsideTransaction: (allowCommit: CountDownLatch, challengeLockHeld: () -> Unit) -> Unit
    ) {
        val invalidationEntered = CountDownLatch(1)
        val allowInvalidationCommit = CountDownLatch(1)
        val invalidatorBackendPid = AtomicInteger()
        val port = RecordingPort()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val invalidator = pool.submit(Callable {
                invalidateInsideTransaction(allowInvalidationCommit) {
                    val backendPid = assertNotNull(jdbc.queryForObject("SELECT pg_backend_pid()", Int::class.java))
                    invalidatorBackendPid.set(backendPid)
                    invalidationEntered.countDown()
                }
            })
            assertTrue(invalidationEntered.await(10, TimeUnit.SECONDS), "invalidation did not acquire its PostgreSQL lock")
            assertTrue(invalidatorBackendPid.get() > 0, "invalidation backend PID was not recorded")
            val relayFuture = pool.submit(Callable {
                relay(port).relayClaimed(issued.claimed)
            })

            // This only returns after PostgreSQL reports the actual
            // authorizeDispatch CTE waiting on the invalidator's row lock.
            // The invalidator cannot commit until the next line releases it.
            assertTrue(
                awaitRelayAuthorizationBlockedBy(invalidatorBackendPid.get()) > 0,
                "relay did not wait on the challenge-row lock before invalidation commit"
            )
            allowInvalidationCommit.countDown()
            invalidator.get(20, TimeUnit.SECONDS)
            relayFuture.get(20, TimeUnit.SECONDS)
            assertEquals(0, port.calls.get(), "an invalid challenge committed before authorization must not reach the provider")
            assertEquals(SmsOutboxStatus.FAILED, assertNotNull(outbox.findByChallengeId(issued.challengeId)).status)
        } finally {
            allowInvalidationCommit.countDown()
            pool.shutdownNow()
        }
    }

    /**
     * Observes the real authorization CTE, not merely a started thread. The
     * only completion condition is PostgreSQL reporting both Lock wait state
     * and the invalidating backend as the blocker. The Future timeout is
     * solely a deadlock/test-hang guard and fails if the condition is absent.
     */
    private fun awaitRelayAuthorizationBlockedBy(invalidatorBackendPid: Int): Int {
        val monitor = Executors.newSingleThreadExecutor()
        try {
            val observeBlockedAuthorization: Callable<Int> = Callable<Int> {
                while (true) {
                    val waitingBackendPid = jdbc.query(
                        """
                        SELECT activity.pid
                        FROM pg_stat_activity activity
                        WHERE activity.datname = current_database()
                          AND activity.state = 'active'
                          AND activity.wait_event_type = 'Lock'
                          AND ? = ANY(pg_blocking_pids(activity.pid))
                          AND activity.query LIKE 'WITH owned AS MATERIALIZED%'
                        LIMIT 1
                        """.trimIndent(),
                        { rs, _ -> rs.getInt("pid") },
                        invalidatorBackendPid
                    ).firstOrNull()
                    if (waitingBackendPid != null) {
                        return@Callable waitingBackendPid
                    }
                    Thread.yield()
                }
                error("unreachable: the monitor loop only returns after observing the blocked authorization")
            }
            return monitor.submit(observeBlockedAuthorization).get(10, TimeUnit.SECONDS)
        } catch (ex: TimeoutException) {
            throw AssertionError(
                "relay authorization never reached a PostgreSQL Lock wait blocked by backend $invalidatorBackendPid",
                ex
            )
        } finally {
            monitor.shutdownNow()
        }
    }

    private data class Issued(
        val identity: Identity,
        val phone: Phone,
        val challengeId: String,
        val claimed: com.pios.identity.application.SmsOutboxRecord,
        val issuer: PhoneVerificationChallengeIssuer
    )

    private fun uniquePhone(): Phone {
        val suffix = UUID.randomUUID().toString().filter { it.isDigit() }.take(7).padEnd(7, '0')
        return Phone("+7998$suffix")
    }

    private class RecordingPort : OutboundSmsPort {
        val calls = AtomicInteger()
        override fun sendVerificationCode(phone: Phone, code: String): Long {
            calls.incrementAndGet()
            return 99L
        }
    }

    private class BlockingIdentityRepository(
        private val delegate: IdentityRepository,
        private val allowCommit: CountDownLatch,
        private val challengeLockHeld: () -> Unit
    ) : IdentityRepository by delegate {
        override fun save(identity: Identity) {
            delegate.save(identity)
            // ConfirmRecovery has already consumed the locked challenge.
            challengeLockHeld()
            check(allowCommit.await(10, TimeUnit.SECONDS)) { "consumption transaction was not released" }
        }
    }

    private class BlockingChallengeRepository(
        private val delegate: PhoneVerificationChallengeRepository,
        private val allowCommit: CountDownLatch,
        private val challengeLockHeld: () -> Unit
    ) : PhoneVerificationChallengeRepository by delegate {
        override fun update(challenge: PhoneVerificationChallenge) {
            delegate.update(challenge)
            // The failed confirmation has persisted the final allowed attempt.
            challengeLockHeld()
            check(allowCommit.await(10, TimeUnit.SECONDS)) { "attempt-exhaustion transaction was not released" }
        }
    }
}
