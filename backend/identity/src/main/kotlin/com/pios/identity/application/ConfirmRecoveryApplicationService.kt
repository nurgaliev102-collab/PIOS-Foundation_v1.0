package com.pios.identity.application

import com.pios.identity.domain.PasswordCredential
import com.pios.identity.domain.Phone
import com.pios.identity.domain.PhoneVerificationPurpose
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * ADR-082 Part 6/§5 (D-03.1, D-03.3, D-03.6, D-03.7) — application-layer
 * coordination for `POST /v1/identities/recovery/confirm`. One atomic
 * transaction: verify OTP, replace credential, bump `sessionGeneration`,
 * mint a fresh token — no partial success (the ratified contract's own
 * words). Never distinguishes *why* it failed (mirrors
 * [LoginApplicationService]'s own discipline) — a malformed/unknown phone,
 * no live challenge, an expired one, one whose attempts are exhausted, and
 * a wrong code all collapse to the same [ConfirmRecoveryOutcome.Failure].
 *
 * D-03.6 invariants, enforced structurally rather than merely checked:
 * [ConfirmRecoveryCommand] carries no `driverId` (items 3/4) — this class
 * never reads or writes `driverId` anywhere; it only reads the
 * already-loaded [com.pios.identity.domain.Identity]'s own value and
 * carries it through unchanged into the response (items 5/6). The same
 * [com.pios.identity.domain.IdentityId] the phone already resolved to is
 * always the one mutated and returned (items 1/2) — no new `Identity` is
 * ever created here. A guest (`phone == null`) can never reach this path,
 * since [IdentityRepository.findByPhone] cannot match one (item 7).
 */
@Service
class ConfirmRecoveryApplicationService(
    private val identityRepository: IdentityRepository,
    private val credentialRepository: CredentialRepository,
    private val challengeRepository: PhoneVerificationChallengeRepository,
    private val passwordHasher: PasswordHasher,
    private val sessionTokenIssuer: SessionTokenIssuer,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    fun handle(command: ConfirmRecoveryCommand): ConfirmRecoveryOutcome {
        // Validated before touching the challenge/identity at all -- this
        // is input validation on a field the caller themselves supplied,
        // not a brute-forceable secret, so a distinct 400 here (mapped by
        // the controller) leaks nothing about the target identity. Mirrors
        // RegisterIdentityApplicationService's own identical guard.
        require(command.newPassword.length in RegisterIdentityApplicationService.MIN_PASSWORD_LENGTH..
            RegisterIdentityApplicationService.MAX_PASSWORD_LENGTH) {
            "password must contain between ${RegisterIdentityApplicationService.MIN_PASSWORD_LENGTH} and " +
                "${RegisterIdentityApplicationService.MAX_PASSWORD_LENGTH} characters"
        }

        return transactionRunner.run {
            val phone = phoneOrNull(command.phone) ?: return@run ConfirmRecoveryOutcome.Failure
            val identity = identityRepository.findByPhone(phone) ?: return@run ConfirmRecoveryOutcome.Failure
            if (identity.phoneVerifiedAt == null) {
                // Unverified/legacy -- D-03.2: knowing the phone is never
                // sufficient by itself, and this must fail exactly like
                // every other reason, not distinguishably.
                return@run ConfirmRecoveryOutcome.Failure
            }

            val challenge = challengeRepository.findLiveForUpdate(identity.id, PhoneVerificationPurpose.RECOVERY)
                ?: return@run ConfirmRecoveryOutcome.Failure
            val now = Instant.now()
            if (challenge.isExpired(now) || challenge.attemptsExhausted) {
                return@run ConfirmRecoveryOutcome.Failure
            }

            // Attempt is recorded before the compare, and persisted even on
            // a wrong-code return below -- this method returning normally
            // (not throwing) still commits the transaction, so a failed
            // guess's cost is never lost to a rollback. D-03.7 "bounded
            // attempts", "atomic consume".
            val attempted = challenge.copy(attemptCount = challenge.attemptCount + 1)
            challengeRepository.update(attempted)

            val codeMatches = passwordHasher.matches(
                password = command.code,
                hash = attempted.codeHash,
                salt = attempted.codeSalt,
                iterations = attempted.iterations
            )
            if (!codeMatches) {
                return@run ConfirmRecoveryOutcome.Failure
            }

            challengeRepository.update(attempted.copy(consumedAt = now))

            val hashed = passwordHasher.hash(command.newPassword)
            credentialRepository.replace(
                PasswordCredential(
                    identityId = identity.id,
                    passwordHash = hashed.hash,
                    passwordSalt = hashed.salt,
                    iterations = hashed.iterations,
                    createdAt = now
                )
            )

            val bumped = identity.withSessionGenerationBumped()
            identityRepository.save(bumped)

            val issued = sessionTokenIssuer.issue(bumped.id.value, bumped.driverId, bumped.sessionGeneration)
            ConfirmRecoveryOutcome.Success(bumped, issued.token, issued.expiresAt)
        }
    }

    private fun phoneOrNull(raw: String): Phone? =
        try {
            Phone(raw)
        } catch (ex: IllegalArgumentException) {
            null
        }
}
