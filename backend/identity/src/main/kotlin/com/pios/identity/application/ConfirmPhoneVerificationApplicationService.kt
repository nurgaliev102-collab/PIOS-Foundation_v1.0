package com.pios.identity.application

import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.PhoneVerificationPurpose
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * ADR-082 Part 2/§5 (D-03.2, D-03.6 item 8) — application-layer
 * coordination for `POST /v1/identities/me/phone/verify/confirm`. Verifies
 * the OTP for purpose [PhoneVerificationPurpose.LEGACY_ENROLLMENT] and, on
 * success, sets `Identity.phoneVerifiedAt` — nothing else. No credential
 * change, no `sessionGeneration` bump (this is a proof event on an
 * already-trusted session, not a recovery event), and no driver
 * association of any kind is created as a side effect (D-03.6 item 8) —
 * this method never touches `driverId`.
 */
@Service
class ConfirmPhoneVerificationApplicationService(
    private val identityRepository: IdentityRepository,
    private val challengeRepository: PhoneVerificationChallengeRepository,
    private val passwordHasher: PasswordHasher,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    fun handle(command: ConfirmPhoneVerificationCommand): ConfirmPhoneVerificationOutcome = transactionRunner.run {
        val identityId = IdentityId(command.identityId)
        val identity = identityRepository.findById(identityId) ?: return@run ConfirmPhoneVerificationOutcome.Failure
        if (identity.sessionGeneration != command.presentedGeneration) {
            throw StaleSessionException()
        }
        if (identity.phone == null || identity.phoneVerifiedAt != null) {
            return@run ConfirmPhoneVerificationOutcome.Failure
        }

        val challenge = challengeRepository.findLiveForUpdate(identity.id, PhoneVerificationPurpose.LEGACY_ENROLLMENT)
            ?: return@run ConfirmPhoneVerificationOutcome.Failure
        val now = Instant.now()
        if (challenge.isExpired(now) || challenge.attemptsExhausted) {
            return@run ConfirmPhoneVerificationOutcome.Failure
        }

        val attempted = challenge.copy(attemptCount = challenge.attemptCount + 1)
        challengeRepository.update(attempted)

        val codeMatches = passwordHasher.matches(
            password = command.code,
            hash = attempted.codeHash,
            salt = attempted.codeSalt,
            iterations = attempted.iterations
        )
        if (!codeMatches) {
            return@run ConfirmPhoneVerificationOutcome.Failure
        }

        challengeRepository.update(attempted.copy(consumedAt = now))

        val verified = identity.withPhoneVerified(now)
        identityRepository.save(verified)
        ConfirmPhoneVerificationOutcome.Success(verified)
    }
}
