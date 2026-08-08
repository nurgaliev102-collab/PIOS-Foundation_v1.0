package com.pios.identity.application

import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.PasswordCredential
import com.pios.identity.domain.Phone
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Application-layer coordination for Register Identity (ADR-055 Decision
 * 3) — creates the [Identity] and its first-ever [PasswordCredential] in
 * one call, then issues a session token immediately so a freshly
 * registered caller does not need a separate login round-trip.
 * Supersedes [CreateIdentityApplicationService] as the only way a new
 * Identity gains a phone from this point forward; that class is left in
 * place only because it is still exercised directly (bypassing HTTP) by
 * tests that predate credentials — [com.pios.identity.api.IdentityController]
 * no longer calls it, and `POST /v1/identities` (the credential-less
 * create) no longer exists.
 *
 * Rejects a blank [RegisterIdentityCommand.password] the same way
 * [AssociateDriverApplicationService] rejects a blank `driverId` — an
 * empty credential is not a credential. A phone already registered throws
 * [PhoneAlreadyRegisteredException] (ADR-055 Decision 3: 409); the
 * application-level [IdentityRepository.findByPhone] check here is the
 * ordinary path, and the database's own `ux_identities_phone` unique
 * index (`V3__create_identity_credentials.sql`) is the actual guarantee
 * against a race between the check and the insert, mirroring
 * `com.pios.passengerexperience.application.CreateConnectionApplicationService`'s
 * own reasoning for why a schema-level uniqueness constraint, not an
 * application-level check alone, is the real guarantee.
 */
@Service
class RegisterIdentityApplicationService(
    private val identityRepository: IdentityRepository,
    private val credentialRepository: CredentialRepository,
    private val passwordHasher: PasswordHasher,
    private val sessionTokenIssuer: SessionTokenIssuer,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    fun handle(command: RegisterIdentityCommand): RegisterIdentityOutcome = transactionRunner.run {
        require(command.password.isNotBlank()) { "password must not be blank" }
        val phone = Phone(command.phone)
        if (identityRepository.findByPhone(phone) != null) {
            throw PhoneAlreadyRegisteredException(phone)
        }

        val identity = Identity(
            id = IdentityId(UUID.randomUUID().toString()),
            phone = phone,
            driverId = null,
            createdAt = Instant.now()
        )
        identityRepository.save(identity)

        val hashed = passwordHasher.hash(command.password)
        credentialRepository.save(
            PasswordCredential(
                identityId = identity.id,
                passwordHash = hashed.hash,
                passwordSalt = hashed.salt,
                iterations = hashed.iterations,
                createdAt = Instant.now()
            )
        )

        val issued = sessionTokenIssuer.issue(identity.id.value, identity.driverId)
        RegisterIdentityOutcome(identity, issued.token, issued.expiresAt)
    }
}
