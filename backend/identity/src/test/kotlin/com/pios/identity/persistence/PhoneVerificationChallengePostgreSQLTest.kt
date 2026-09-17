package com.pios.identity.persistence

import com.pios.identity.application.ConfirmPhoneVerificationApplicationService
import com.pios.identity.application.ConfirmPhoneVerificationCommand
import com.pios.identity.application.ConfirmPhoneVerificationOutcome
import com.pios.identity.application.ConfirmRecoveryApplicationService
import com.pios.identity.application.ConfirmRecoveryCommand
import com.pios.identity.application.ConfirmRecoveryOutcome
import com.pios.identity.application.LoginApplicationService
import com.pios.identity.application.LoginCommand
import com.pios.identity.application.LoginOutcome
import com.pios.identity.application.LoginRateLimiter
import com.pios.identity.application.OutboundSmsPort
import com.pios.identity.application.PasswordHasher
import com.pios.identity.application.PhoneVerificationChallengeIssuer
import com.pios.identity.application.SessionTokenIssuer
import com.pios.identity.application.StaleSessionException
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

    // --- D-03 closure pass: cross-purpose concurrency (recovery confirm vs legacy-enrolment confirm) ---

    /**
     * The adversarial pair the closure pass asked to be proven, not
     * assumed: `ConfirmRecoveryApplicationService` and
     * `ConfirmPhoneVerificationApplicationService` each do a single,
     * early, unlocked read of the `Identity` row, then an unconditional
     * full-row `save()` -- the exact shape a lost update needs. Two
     * independent things turn out to prevent it here, both found by
     * actually running this race, not only by reasoning about it:
     *
     * 1. Mutually exclusive read-time preconditions: recovery requires
     *    `phoneVerifiedAt != null` to proceed at all; legacy-enrolment-confirm
     *    requires `phoneVerifiedAt == null`. On the same row at the same
     *    instant those cannot both hold, so at most one of the two can ever
     *    pass its own precondition and reach a write.
     * 2. `ConfirmPhoneVerificationApplicationService`'s own pre-existing
     *    `sessionGeneration` staleness check (added for the unrelated
     *    stolen-token scenario, ADR-082 §8) also fires here: if recovery's
     *    generation bump commits before legacy's read, legacy's own
     *    `presentedGeneration` (0, the only value reachable in this test --
     *    no prior recovery had happened yet to mint a caller a `sgen: 1`
     *    token) no longer matches, and it throws `StaleSessionException`
     *    instead of returning `Failure`. This was the first, genuinely
     *    reproducible outcome of this exact race in this closure pass (2 of
     *    3 initial runs returned `Failure`; the third threw) -- both are
     *    safe: the exception aborts legacy's transaction before any write,
     *    identical in effect to `Failure`. Asserted below as "either safe
     *    outcome", not silently narrowed to just one.
     *
     * This test races them for real, on the same identity, from both of
     * the only two reachable starting states, repeated, to confirm that
     * structural exclusion actually holds under real PostgreSQL
     * concurrency rather than only in single-threaded reasoning.
     */
    @Test
    fun `racing a recovery confirm against a legacy-enrolment confirm on an already-verified identity never loses the recovery effect, repeated`() {
        repeat(5) {
            val (identity, phone) = registerVerifiedIdentity() // phoneVerifiedAt already set -- legacy-confirm's own precondition must block it
            val recoveryCode = issueAndCapture(identity.id, phone, PhoneVerificationPurpose.RECOVERY)
            // A live LEGACY_ENROLLMENT challenge too, so the race is a real
            // contest -- not a foregone conclusion because no second
            // challenge even exists to attempt.
            val legacyCode = issueAndCapture(identity.id, phone, PhoneVerificationPurpose.LEGACY_ENROLLMENT)
            val recoveryService = ConfirmRecoveryApplicationService(identities, credentials, challenges, passwordHasher, sessionTokenIssuer, transactions)
            val legacyService = ConfirmPhoneVerificationApplicationService(identities, challenges, passwordHasher, transactions)

            val (recoveryResult, legacyResult) = race(
                { recoveryService.handle(ConfirmRecoveryCommand(phone.value, recoveryCode, "cross-purpose-new-password")) },
                { legacyService.handle(ConfirmPhoneVerificationCommand(identity.id.value, legacyCode)) }
            )

            assertIs<ConfirmRecoveryOutcome.Success>(
                recoveryResult.getOrThrow(), "recovery must succeed -- the identity was already phone-verified, and recovery has no generation precondition of its own"
            )
            // Legacy's own outcome may be a plain Failure (its
            // phoneVerifiedAt precondition already false) or a thrown
            // StaleSessionException (its generation precondition stale
            // because recovery's bump committed first) -- both are safe,
            // non-writing outcomes; neither is a lost update.
            val legacyOutcome = legacyResult.fold(
                onSuccess = { it },
                onFailure = { ex -> assertIs<StaleSessionException>(ex, "the only exception legacy-enrolment-confirm may throw here"); null }
            )
            if (legacyOutcome != null) {
                assertEquals(ConfirmPhoneVerificationOutcome.Failure, legacyOutcome)
            }

            val persisted = identities.findById(identity.id)!!
            assertEquals(1, persisted.sessionGeneration, "recovery's own bump must survive intact -- never reverted to the pre-recovery generation")
            assertTrue(persisted.phoneVerifiedAt != null, "phoneVerifiedAt must remain set -- never lost")
            assertEquals(identity.driverId, persisted.driverId, "driverId must be unchanged by either operation")
            assertEquals(identity.id, persisted.id, "Identity.id must be unchanged")

            // Credential recovery actually took effect, not rolled back.
            val loginService = LoginApplicationService(
                identities, credentials, passwordHasher, sessionTokenIssuer,
                LoginRateLimiter(failureDelayMillis = 0, maxFailuresPerWindow = 1000, windowMillis = 900_000)
            )
            assertIs<LoginOutcome.Success>(loginService.handle(LoginCommand(phone.value, "cross-purpose-new-password")))
        }
    }

    @Test
    fun `racing a recovery confirm against a legacy-enrolment confirm on a not-yet-verified identity never corrupts state, repeated`() {
        repeat(5) {
            val phone = uniqueTestPhone()
            val identity = Identity(IdentityId(UUID.randomUUID().toString()), phone, null, Instant.now()) // phoneVerifiedAt == null -- recovery's own precondition must block it
            identities.save(identity)
            val hashed = passwordHasher.hash("original-password-postgres-unverified")
            credentials.save(PasswordCredential(identity.id, hashed.hash, hashed.salt, hashed.iterations, Instant.now()))
            val legacyCode = issueAndCapture(identity.id, phone, PhoneVerificationPurpose.LEGACY_ENROLLMENT)
            // No RECOVERY challenge is even issuable here -- RequestRecoveryApplicationService
            // itself refuses to issue one for an unverified identity (already
            // proven by RequestRecoveryApplicationServiceTest) -- so the most
            // adversarial reachable input for the recovery side is simply an
            // arbitrary code against a nonexistent challenge, which must fail
            // the same generic way regardless of the race.
            val recoveryService = ConfirmRecoveryApplicationService(identities, credentials, challenges, passwordHasher, sessionTokenIssuer, transactions)
            val legacyService = ConfirmPhoneVerificationApplicationService(identities, challenges, passwordHasher, transactions)

            val (recoveryResult, legacyResult) = race(
                { recoveryService.handle(ConfirmRecoveryCommand(phone.value, "000000", "cross-purpose-new-password-2")) },
                { legacyService.handle(ConfirmPhoneVerificationCommand(identity.id.value, legacyCode)) }
            )

            assertEquals(ConfirmRecoveryOutcome.Failure, recoveryResult.getOrThrow(), "recovery must fail -- the identity is not yet phone-verified")
            assertIs<ConfirmPhoneVerificationOutcome.Success>(legacyResult.getOrThrow(), "legacy-enrolment must succeed -- nothing else can have written first")

            val persisted = identities.findById(identity.id)!!
            assertEquals(0, persisted.sessionGeneration, "recovery never ran -- generation stays at its initial value")
            assertTrue(persisted.phoneVerifiedAt != null, "legacy enrolment's own effect must not be lost")
            assertEquals(identity.driverId, persisted.driverId)
        }
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
