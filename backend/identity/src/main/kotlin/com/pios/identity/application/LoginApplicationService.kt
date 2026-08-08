package com.pios.identity.application

import com.pios.identity.domain.Phone
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for Login (ADR-055 Decision 3). Never
 * distinguishes an unknown phone, a malformed phone, a phone with no
 * credential yet, or a correct phone presented with the wrong password —
 * all four collapse to the exact same [LoginOutcome.Failure], recorded
 * against [loginRateLimiter] and delayed the same way in every case. This
 * is the same "never distinguish" discipline
 * [com.pios.identity.api.OwnerCredentialGate] already established
 * (ADR-044 Decision 3), reused here per ADR-055 Decision 3's own
 * instruction.
 */
@Service
class LoginApplicationService(
    private val identityRepository: IdentityRepository,
    private val credentialRepository: CredentialRepository,
    private val passwordHasher: PasswordHasher,
    private val sessionTokenIssuer: SessionTokenIssuer,
    private val loginRateLimiter: LoginRateLimiter,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    fun handle(command: LoginCommand): LoginOutcome = transactionRunner.run {
        if (loginRateLimiter.tooManyRecentFailures(command.phone)) {
            return@run LoginOutcome.Failure
        }

        val phone = phoneOrNull(command.phone) ?: return@run fail(command.phone)
        val identity = identityRepository.findByPhone(phone) ?: return@run fail(command.phone)
        val credential = credentialRepository.findByIdentityId(identity.id) ?: return@run fail(command.phone)
        val matches = passwordHasher.matches(
            password = command.password,
            hash = credential.passwordHash,
            salt = credential.passwordSalt,
            iterations = credential.iterations
        )
        if (!matches) {
            return@run fail(command.phone)
        }

        val issued = sessionTokenIssuer.issue(identity.id.value, identity.driverId)
        LoginOutcome.Success(identity, issued.token, issued.expiresAt)
    }

    private fun fail(phone: String): LoginOutcome.Failure {
        loginRateLimiter.recordFailure(phone)
        return LoginOutcome.Failure
    }

    private fun phoneOrNull(raw: String): Phone? =
        try {
            Phone(raw)
        } catch (ex: IllegalArgumentException) {
            null
        }
}
