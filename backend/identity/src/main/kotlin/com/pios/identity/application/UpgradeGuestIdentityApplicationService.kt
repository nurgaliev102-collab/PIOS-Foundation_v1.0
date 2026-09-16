package com.pios.identity.application

import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.PasswordCredential
import com.pios.identity.domain.Phone
import org.springframework.stereotype.Service
import java.time.Instant

data class UpgradeGuestIdentityCommand(
    val identityId: String,
    val phone: String,
    val password: String
)

/** Converts a guest in place so its orders and relationships keep the same identity reference. */
@Service
class UpgradeGuestIdentityApplicationService(
    private val identityRepository: IdentityRepository,
    private val credentialRepository: CredentialRepository,
    private val passwordHasher: PasswordHasher,
    private val sessionTokenIssuer: SessionTokenIssuer,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    fun handle(command: UpgradeGuestIdentityCommand): RegisterIdentityOutcome = transactionRunner.run {
        require(command.password.length in RegisterIdentityApplicationService.MIN_PASSWORD_LENGTH..
            RegisterIdentityApplicationService.MAX_PASSWORD_LENGTH) {
            "password must contain between ${RegisterIdentityApplicationService.MIN_PASSWORD_LENGTH} and " +
                "${RegisterIdentityApplicationService.MAX_PASSWORD_LENGTH} characters"
        }
        val identityId = IdentityId(command.identityId)
        val identity = identityRepository.findById(identityId) ?: throw IdentityNotFoundException(identityId)
        check(identity.phone == null && credentialRepository.findByIdentityId(identityId) == null) {
            "only a guest identity can be upgraded"
        }
        val phone = Phone(command.phone)
        if (identityRepository.findByPhone(phone) != null) {
            throw PhoneAlreadyRegisteredException(phone)
        }

        val upgraded = identity.withPhone(phone)
        identityRepository.save(upgraded)
        val hashed = passwordHasher.hash(command.password)
        credentialRepository.save(
            PasswordCredential(
                identityId = identityId,
                passwordHash = hashed.hash,
                passwordSalt = hashed.salt,
                iterations = hashed.iterations,
                createdAt = Instant.now()
            )
        )
        val issued = sessionTokenIssuer.issue(upgraded.id.value, upgraded.driverId)
        RegisterIdentityOutcome(upgraded, issued.token, issued.expiresAt)
    }
}
