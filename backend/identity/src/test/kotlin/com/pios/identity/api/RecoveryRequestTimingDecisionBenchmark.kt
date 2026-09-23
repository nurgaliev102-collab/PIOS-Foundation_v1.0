package com.pios.identity.api

import com.pios.identity.application.AssociateDriverApplicationService
import com.pios.identity.application.ConfirmPhoneVerificationApplicationService
import com.pios.identity.application.ConfirmRecoveryApplicationService
import com.pios.identity.application.CreateGuestIdentityApplicationService
import com.pios.identity.application.GuestIdentityRateLimiter
import com.pios.identity.application.LoginApplicationService
import com.pios.identity.application.LoginRateLimiter
import com.pios.identity.application.OtpClientKeyRateLimiter
import com.pios.identity.application.PasswordHasher
import com.pios.identity.application.PhoneOtpRequestRateLimiter
import com.pios.identity.application.PhoneVerificationChallengeIssuer
import com.pios.identity.application.RequestPhoneVerificationApplicationService
import com.pios.identity.application.RequestRecoveryApplicationService
import com.pios.identity.application.RetrieveIdentityHandler
import com.pios.identity.application.SessionTokenIssuer
import com.pios.identity.application.UpgradeGuestIdentityApplicationService
import com.pios.identity.application.testOtpCipher
import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.Phone
import com.pios.identity.persistence.PostgreSQLCredentialRepository
import com.pios.identity.persistence.PostgreSQLIdentityRepository
import com.pios.identity.persistence.PostgreSQLPhoneVerificationChallengeGuard
import com.pios.identity.persistence.PostgreSQLPhoneVerificationChallengeRepository
import com.pios.identity.persistence.PostgreSQLSmsOutboxRepository
import com.pios.identity.persistence.PostgreSQLTestDatabase
import com.pios.identity.persistence.SpringTransactionRunner
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.Base64
import kotlin.math.ceil
import kotlin.test.assertEquals

/**
 * Manual C-2 timing-decision benchmark. It uses only the hard-coded
 * `pios_identity_test` database, never sends SMS, and exercises the controller
 * through transaction commit. Keep disabled in ordinary test runs; enable only
 * for an explicit local timing-decision run.
 */
@Disabled("Manual C-2 timing-decision benchmark; enable only for an explicit local measurement")
class RecoveryRequestTimingDecisionBenchmark {
    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val jdbc = JdbcTemplate(dataSource)
    private val identities = PostgreSQLIdentityRepository(jdbc)
    private val credentials = PostgreSQLCredentialRepository(jdbc)
    private val challenges = PostgreSQLPhoneVerificationChallengeRepository(jdbc)
    private val outbox = PostgreSQLSmsOutboxRepository(jdbc)
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val passwordHasher = PasswordHasher(defaultIterations = 210_000)
    private val sessionSecret = Base64.getEncoder().encodeToString(ByteArray(32) { 9 })
    private val sessionIssuer = SessionTokenIssuer(sessionSecret, 2_592_000)
    private val sessionVerifier = SessionTokenVerifier(sessionSecret)
    private val phoneLimiter = PhoneOtpRequestRateLimiter(10_000, 3_600_000)
    private val clientLimiter = OtpClientKeyRateLimiter(10_000, 3_600_000)
    private val issuer = PhoneVerificationChallengeIssuer(
        challenges, passwordHasher, testOtpCipher(), outbox, transactionRunner,
        ttlSeconds = 600, maxAttempts = 10, codeDigits = 6,
        challengeGuard = PostgreSQLPhoneVerificationChallengeGuard(jdbc)
    )
    private val controller = IdentityController(
        registerIdentityApplicationService = com.pios.identity.application.RegisterIdentityApplicationService(
            identities, credentials, passwordHasher, sessionIssuer
        ),
        loginApplicationService = LoginApplicationService(
            identities, credentials, passwordHasher, sessionIssuer,
            LoginRateLimiter(0, 10_000, 3_600_000)
        ),
        retrieveIdentityHandler = RetrieveIdentityHandler(identities),
        associateDriverApplicationService = AssociateDriverApplicationService(identities, sessionIssuer),
        sessionTokenVerifier = sessionVerifier,
        createGuestIdentityApplicationService = CreateGuestIdentityApplicationService(identities, sessionIssuer),
        guestIdentityRateLimiter = GuestIdentityRateLimiter(10_000, 3_600_000),
        upgradeGuestIdentityApplicationService = UpgradeGuestIdentityApplicationService(
            identities, credentials, passwordHasher, sessionIssuer
        ),
        requestRecoveryApplicationService = RequestRecoveryApplicationService(identities, issuer, phoneLimiter, clientLimiter),
        confirmRecoveryApplicationService = ConfirmRecoveryApplicationService(
            identities, credentials, challenges, passwordHasher, sessionIssuer, transactionRunner
        ),
        requestPhoneVerificationApplicationService = RequestPhoneVerificationApplicationService(identities, issuer, phoneLimiter),
        confirmPhoneVerificationApplicationService = ConfirmPhoneVerificationApplicationService(
            identities, challenges, passwordHasher, transactionRunner
        ),
        otpClientKeyRateLimiter = clientLimiter
    )

    @Test
    fun `manual controller to commit timing benchmark`() {
        val sampleCount = 50
        repeat(10) { index -> warmUp(Case.UNKNOWN, index) }
        repeat(10) { index -> warmUp(Case.UNVERIFIED, index) }
        repeat(10) { index -> warmUp(Case.ELIGIBLE, index) }

        Case.entries.forEach { case ->
            val samples = List(sampleCount) { index ->
                val phone = "+7994${uniqueDigits()}"
                prepare(phone, case)
                val started = System.nanoTime()
                val response = controller.requestRecoveryFromAddress(phone, "198.51.100.${(index % 200) + 1}", null).statusCode.value()
                assertEquals(202, response)
                (System.nanoTime() - started) / 1_000_000.0
            }
            println("C2_TIMING ${case.name} ${summary(samples)}")
        }
    }

    private fun warmUp(case: Case, index: Int) {
        val phone = "+7994${uniqueDigits()}"
        prepare(phone, case)
        assertEquals(202, controller.requestRecoveryFromAddress(phone, "198.51.100.${(index % 200) + 1}", null).statusCode.value())
    }

    /** Setup is intentionally outside the measured controller request. */
    private fun prepare(phoneValue: String, case: Case) {
        val phone = Phone(phoneValue)
        when (case) {
            Case.UNKNOWN -> Unit
            Case.UNVERIFIED -> identities.save(Identity(IdentityId("timing-u-${uniqueDigits()}"), phone, null, Instant.now()))
            Case.ELIGIBLE -> identities.save(
                Identity(IdentityId("timing-e-${uniqueDigits()}"), phone, null, Instant.now()).withPhoneVerified(Instant.now())
            )
        }
    }

    private fun uniqueDigits(): String = System.nanoTime().toString().takeLast(7)

    private fun summary(values: List<Double>): String {
        val sorted = values.sorted()
        fun percentile(q: Double): Double = sorted[(ceil(q * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)]
        return "n=${sorted.size} min=%.3fms p50=%.3fms p95=%.3fms p99=%.3fms max=%.3fms".format(
            sorted.first(), percentile(0.50), percentile(0.95), percentile(0.99), sorted.last()
        )
    }

    private enum class Case { UNKNOWN, UNVERIFIED, ELIGIBLE }
}
