package com.pios.identity.application

import com.pios.identity.domain.IdentityId
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for Associate Driver (ADR-039; ADR-055
 * Decision 6 addendum) — `identity`'s first real capability beyond
 * Create/Retrieve. Rejects a blank [AssociateDriverCommand.driverId] the
 * same way [com.pios.identity.domain.IdentityId]'s own constructor already
 * rejects a blank id, for the same reason: an empty reference is not a
 * reference.
 *
 * Issues a fresh session token after persisting the association, mirroring
 * [RegisterIdentityApplicationService]/[LoginApplicationService]'s own
 * `sessionTokenIssuer.issue(identity.id.value, identity.driverId)` pattern
 * exactly. Before this addendum, a token minted at registration (`drv:
 * null`, no driver yet) stayed the only token the caller had for the rest
 * of that session — `associateDriver` changed what `driverId` the caller's
 * Identity carries but never re-minted the token asserting it, so every
 * `driverId`-scoped call the same session made (e.g. Passenger
 * Experience's `GET /v1/connections?driverId=`) kept comparing against a
 * `drv` claim frozen at `null`, permanently 403 until the caller logged
 * out and back in. No server-side revocation of the superseded token is
 * introduced here — ADR-055's stateless model has none; the old token
 * simply stops being the one this device stores and sends.
 */
@Service
class AssociateDriverApplicationService(
    private val identityRepository: IdentityRepository,
    private val sessionTokenIssuer: SessionTokenIssuer,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    fun handle(command: AssociateDriverCommand): AssociateDriverOutcome = transactionRunner.run {
        require(command.driverId.isNotBlank()) { "driverId must not be blank" }
        val identityId = IdentityId(command.identityId)
        val identity = identityRepository.findById(identityId) ?: throw IdentityNotFoundException(identityId)
        if (identity.sessionGeneration != command.presentedGeneration) {
            throw StaleSessionException()
        }
        require(identity.phone != null) { "a guest identity cannot own a driver profile" }
        require(command.driverId == identity.id.value) {
            "a driver profile must use its owning identity id"
        }
        require(identity.driverId == null || identity.driverId == command.driverId) {
            "a driver association cannot be reassigned"
        }
        val updated = identity.withDriverId(command.driverId)
        identityRepository.save(updated)
        val issued = sessionTokenIssuer.issue(updated.id.value, updated.driverId, updated.sessionGeneration)
        AssociateDriverOutcome(updated, issued.token, issued.expiresAt)
    }
}
