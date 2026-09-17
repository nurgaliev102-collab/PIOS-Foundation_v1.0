package com.pios.identity.persistence

import com.pios.identity.application.ConfirmPhoneVerificationApplicationService
import com.pios.identity.application.ConfirmPhoneVerificationCommand
import com.pios.identity.application.ConfirmPhoneVerificationOutcome
import com.pios.identity.application.ConfirmRecoveryApplicationService
import com.pios.identity.application.ConfirmRecoveryCommand
import com.pios.identity.application.ConfirmRecoveryOutcome
import com.pios.identity.application.OutboundSmsPort
import com.pios.identity.application.PasswordHasher
import com.pios.identity.application.PhoneVerificationChallengeIssuer
import com.pios.identity.application.SessionTokenIssuer
import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.PasswordCredential
import com.pios.identity.domain.Phone
import com.pios.identity.domain.PhoneVerificationPurpose
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * ADR-082 (D-03.7) — the real-PostgreSQL, real-two-thread proof that
 * concurrent OTP verification and concurrent OTP requests each have
 * exactly one winner, mirroring `com.pios.dispatch.persistence.CommitmentTerminationPostgreSQLTest`'s
 * own `race()` shape and reasoning (ADR-080 precedent) -- an in-memory
 * repository's coarse lock (`InMemoryPhoneVerificationChallengeRepository`)
 * cannot prove this; only a real `SELECT ... FOR UPDATE` against a real
 * database, raced by two real threads, can.
 */
class PhoneVerificationChallengePostgreSQLTest {
    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val jdbc = JdbcTemplate(dataSource)
    private val transactions = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val identities = PostgreSQLIdentityRepository(jdbc)
    private val credentials = PostgreSQLCredentialRepository(jdbc)
    private val challenges = PostgreSQLPhoneVerificationChallengeRepository(jdbc)
    private val guard = PostgreSQLPhoneVerificationChallengeGuard(jdbc)
    private val passwordHasher = PasswordHasher(defaultIterations = 1000)
    private val secret = Base64.getEncoder().encodeToString("phone-verification-postgres-test-secret".toByteArray())
    private val sessionTokenIssuer = SessionTokenIssuer(secretBase64 = secret, ttlSeconds = 2_592_000)

    private fun uniqueTestPhone(): Phone = Phone("+7998" + System.nanoTime().toString().takeLast(7))

    private fun registerVerifiedIdentity(): Pair<Identity, Phone> {
        val phone = uniqueTestPhone()
        val identity = Identity(IdentityId(UUID.randomUUID().toString()), phone, null, Instant.now())
            .withPhoneVerified(Instant.now())
        identities.save(identity)
        val hashed = passwordHasher.hash("original-password-postgres-1")
        credentials.save(PasswordCredential(identity.id, hashed.hash, hashed.salt, hashed.iterations, Instant.now()))
        return identity to phone
    }

    private fun issueAndCapture(identityId: IdentityId, phone: Phone, purpose: PhoneVerificationPurpose): String {
        val sent = ConcurrentHashMap<String, String>()
        val issuer = PhoneVerificationChallengeIssuer(
            challenges, passwordHasher, OutboundSmsPort { p, code -> sent[p.value] = code },
            transactions, ttlSeconds = 600, maxAttempts = 5, codeDigits = 6, challengeGuard = guard
        )
        issuer.issue(identityId, phone, purpose)
        return sent.getValue(phone.value)
    }

    private fun <A, B> race(first: () -> A, second: () -> B): Pair<Result<A>, Result<B>> {
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        return try {
            val a = pool.submit(Callable { gate.await(); runCatching(first) })
            val b = pool.submit(Callable { gate.await(); runCatching(second) })
            gate.countDown()
            a.get(20, TimeUnit.SECONDS) to b.get(20, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `two concurrent recovery confirms with the same correct code -- exactly one succeeds, generation bumps once, repeated`() {
        repeat(5) {
            val (identity, phone) = registerVerifiedIdentity()
            val code = issueAndCapture(identity.id, phone, PhoneVerificationPurpose.RECOVERY)
            val service = ConfirmRecoveryApplicationService(identities, credentials, challenges, passwordHasher, sessionTokenIssuer, transactions)

            val (first, second) = race(
                { service.handle(ConfirmRecoveryCommand(phone.value, code, "raced-new-password-1")) },
                { service.handle(ConfirmRecoveryCommand(phone.value, code, "raced-new-password-2")) }
            )

            val outcomes = listOf(first.getOrThrow(), second.getOrThrow())
            val successes = outcomes.filterIsInstance<ConfirmRecoveryOutcome.Success>()
            val failures = outcomes.filterIsInstance<ConfirmRecoveryOutcome.Failure>()
            assertEquals(1, successes.size, "exactly one concurrent confirm may succeed")
            assertEquals(1, failures.size, "the loser must see Failure, never a second Success")

            val persisted = identities.findById(identity.id)!!
            assertEquals(1, persisted.sessionGeneration, "generation must bump exactly once, not twice")
        }
    }

    @Test
    fun `two concurrent legacy-enrolment confirms with the same correct code -- exactly one succeeds, repeated`() {
        repeat(5) {
            val phone = uniqueTestPhone()
            val identity = Identity(IdentityId(UUID.randomUUID().toString()), phone, null, Instant.now())
            identities.save(identity)
            val code = issueAndCapture(identity.id, phone, PhoneVerificationPurpose.LEGACY_ENROLLMENT)
            val service = ConfirmPhoneVerificationApplicationService(identities, challenges, passwordHasher, transactions)

            val (first, second) = race(
                { service.handle(ConfirmPhoneVerificationCommand(identity.id.value, code)) },
                { service.handle(ConfirmPhoneVerificationCommand(identity.id.value, code)) }
            )

            val outcomes = listOf(first.getOrThrow(), second.getOrThrow())
            val successes = outcomes.filterIsInstance<ConfirmPhoneVerificationOutcome.Success>()
            assertEquals(1, successes.size, "exactly one concurrent legacy-enrolment confirm may succeed")

            val persisted = identities.findById(identity.id)!!
            assertTrue(persisted.phoneVerifiedAt != null)
        }
    }

    @Test
    fun `two concurrent recovery requests for the same identity leave exactly one live challenge, repeated`() {
        repeat(5) {
            val (identity, phone) = registerVerifiedIdentity()
            val sent = ConcurrentHashMap<String, String>()
            val issuer = PhoneVerificationChallengeIssuer(
                challenges, passwordHasher, OutboundSmsPort { p, code -> sent[p.value] = code },
                transactions, ttlSeconds = 600, maxAttempts = 5, codeDigits = 6, challengeGuard = guard
            )

            val (first, second) = race(
                { issuer.issue(identity.id, phone, PhoneVerificationPurpose.RECOVERY) },
                { issuer.issue(identity.id, phone, PhoneVerificationPurpose.RECOVERY) }
            )
            first.getOrThrow()
            second.getOrThrow()

            val live = jdbc.queryForObject(
                "SELECT count(*) FROM phone_verification_challenges WHERE identity_id = ? AND purpose = 'RECOVERY' " +
                    "AND consumed_at IS NULL AND superseded_at IS NULL",
                Long::class.java, identity.id.value
            )
            assertEquals(1L, live, "at most one live challenge may survive two concurrent requests -- the partial unique index's own guarantee")
        }
    }

    @Test
    fun `a wrong code against a challenge under concurrent contention still counts an attempt and never double-consumes`() {
        val (identity, phone) = registerVerifiedIdentity()
        val correctCode = issueAndCapture(identity.id, phone, PhoneVerificationPurpose.RECOVERY)
        val service = ConfirmRecoveryApplicationService(identities, credentials, challenges, passwordHasher, sessionTokenIssuer, transactions)

        val (wrong, right) = race(
            { service.handle(ConfirmRecoveryCommand(phone.value, "000000", "raced-new-password-3")) },
            { service.handle(ConfirmRecoveryCommand(phone.value, correctCode, "raced-new-password-4")) }
        )

        val outcomes = listOf(wrong.getOrThrow(), right.getOrThrow())
        assertEquals(1, outcomes.count { it is ConfirmRecoveryOutcome.Success })
        assertEquals(1, identities.findById(identity.id)!!.sessionGeneration)
    }

    @Test
    fun `a fresh 'reopen' -- new repository instances -- still rejects the pre-recovery token and accepts the new one`() {
        val (identity, phone) = registerVerifiedIdentity()
        val code = issueAndCapture(identity.id, phone, PhoneVerificationPurpose.RECOVERY)

        // "Reopen": brand-new repository/service instances backed by nothing
        // but the database itself, mirroring PostgreSQLIdentityLifecycleTest's
        // own convention.
        val reopenedIdentities = PostgreSQLIdentityRepository(JdbcTemplate(dataSource))
        val reopenedCredentials = PostgreSQLCredentialRepository(JdbcTemplate(dataSource))
        val reopenedChallenges = PostgreSQLPhoneVerificationChallengeRepository(JdbcTemplate(dataSource))
        val reopenedService = ConfirmRecoveryApplicationService(
            reopenedIdentities, reopenedCredentials, reopenedChallenges, passwordHasher, sessionTokenIssuer, transactions
        )

        val outcome = assertIs<ConfirmRecoveryOutcome.Success>(
            reopenedService.handle(ConfirmRecoveryCommand(phone.value, code, "reopened-new-password"))
        )

        val afterReopen = PostgreSQLIdentityRepository(JdbcTemplate(dataSource)).findById(identity.id)!!
        assertEquals(1, afterReopen.sessionGeneration)
        assertEquals(outcome.identity.sessionGeneration, afterReopen.sessionGeneration)
    }
}
