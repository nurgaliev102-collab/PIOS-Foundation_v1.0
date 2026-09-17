package com.pios.identity.application

import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.Phone
import com.pios.identity.domain.PhoneVerificationChallenge
import com.pios.identity.domain.PhoneVerificationPurpose
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * ADR-082 (D-03) — the shared OTP-issuance step behind both
 * `POST /v1/identities/recovery/request` and
 * `POST /v1/identities/me/phone/verify/request`: supersede any existing
 * live challenge for `(identityId, purpose)`, generate a fresh code, hash
 * it (never store plaintext — D-03.7), persist, then send it.
 *
 * The database write (lock + supersede + insert) runs inside one
 * [TransactionRunner] boundary of its own; the [outboundSmsPort] call
 * happens *after* that transaction commits, deliberately outside it — an
 * external network call held open inside a database transaction is
 * exactly the anti-pattern `ADR-080`'s own `OrderGuard` discipline (lock,
 * mutate, release quickly) argues against, and nothing here needs the SMS
 * send and the DB write to be one atomic unit: a code that was persisted
 * but never delivered is functionally identical to one the caller never
 * requested, not a correctness problem.
 *
 * [challengeGuard] is taken first, before [PhoneVerificationChallengeRepository.supersedeLive]/
 * [PhoneVerificationChallengeRepository.save] — found necessary, not
 * assumed: two concurrent first-ever requests for the same
 * `(identity, purpose)` can both find nothing to supersede and both
 * attempt to insert, without it (see [PhoneVerificationChallengeGuard]'s
 * own KDoc; a real two-thread PostgreSQL test caught this).
 */
@Component
class PhoneVerificationChallengeIssuer(
    private val challengeRepository: PhoneVerificationChallengeRepository,
    private val passwordHasher: PasswordHasher,
    private val outboundSmsPort: OutboundSmsPort,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner,
    @Value("\${pios.identity.otp.ttl-seconds:600}") private val ttlSeconds: Long,
    @Value("\${pios.identity.otp.max-attempts:5}") private val maxAttempts: Int,
    @Value("\${pios.identity.otp.code-digits:6}") private val codeDigits: Int,
    private val challengeGuard: PhoneVerificationChallengeGuard = NoOpPhoneVerificationChallengeGuard
) {
    private val secureRandom = SecureRandom()

    fun issue(identityId: IdentityId, phone: Phone, purpose: PhoneVerificationPurpose) {
        val code = generateCode()
        transactionRunner.run {
            challengeGuard.lock(identityId, purpose)
            val now = Instant.now()
            challengeRepository.supersedeLive(identityId, purpose, now)
            val hashed = passwordHasher.hash(code)
            challengeRepository.save(
                PhoneVerificationChallenge(
                    id = UUID.randomUUID().toString(),
                    identityId = identityId,
                    phone = phone,
                    purpose = purpose,
                    codeHash = hashed.hash,
                    codeSalt = hashed.salt,
                    iterations = hashed.iterations,
                    attemptCount = 0,
                    maxAttempts = maxAttempts,
                    createdAt = now,
                    expiresAt = now.plus(Duration.ofSeconds(ttlSeconds)),
                    consumedAt = null,
                    supersededAt = null
                )
            )
        }
        // Never logged (D-03.7); code exists in memory only as long as this
        // call, and only as a plain numeric string handed to the port below.
        outboundSmsPort.sendVerificationCode(phone, code)
    }

    private fun generateCode(): String {
        val bound = Math.pow(10.0, codeDigits.toDouble()).toInt()
        val value = secureRandom.nextInt(bound)
        return value.toString().padStart(codeDigits, '0')
    }
}
