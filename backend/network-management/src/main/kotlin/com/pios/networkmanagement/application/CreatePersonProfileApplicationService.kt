package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.PersonProfile
import com.pios.networkmanagement.domain.PersonProfileId
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Application-layer coordination for Create Profile (Sprint 7A: PIOS
 * Network Foundation). Verifies the referenced [PersonRepository] entry
 * exists before saving — unlike a cross-*module* reference (Dispatch's
 * `DriverReference`/`OrderReference`, deliberately unverified), this is a
 * same-module, same-database reference, so checking it costs nothing and
 * catches a real data-integrity mistake early.
 */
@Service
class CreatePersonProfileApplicationService(
    private val personRepository: PersonRepository,
    private val personProfileRepository: PersonProfileRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    fun handle(command: CreatePersonProfileCommand): PersonProfile = transactionRunner.run {
        if (personRepository.findById(command.personId) == null) {
            throw PersonNotFoundException(command.personId)
        }
        val profile = PersonProfile(
            id = PersonProfileId(UUID.randomUUID().toString()),
            personId = command.personId,
            type = command.type,
            createdAt = Instant.now()
        )
        personProfileRepository.save(profile)
        profile
    }
}
