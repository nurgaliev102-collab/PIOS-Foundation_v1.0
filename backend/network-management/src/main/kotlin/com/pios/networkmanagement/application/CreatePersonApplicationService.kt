package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.Person
import com.pios.networkmanagement.domain.PersonId
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Application-layer coordination for Create Person (Sprint 7A: PIOS
 * Network Foundation). D-12 creation is idempotent by authenticated
 * [CreatePersonCommand.identityId]. The database-enforced unique binding
 * resolves concurrent requests; the losing request returns the winner's row.
 */
@Service
class CreatePersonApplicationService(
    private val personRepository: PersonRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    data class Outcome(val person: Person, val created: Boolean)

    fun handle(command: CreatePersonCommand): Person = createOrGet(command).person

    fun createOrGet(command: CreatePersonCommand): Outcome = transactionRunner.run {
        require(command.identityId.isNotBlank())
        personRepository.findByIdentityId(command.identityId)?.let { return@run Outcome(it, false) }
        val now = Instant.now()
        val person = Person(
            id = PersonId(UUID.randomUUID().toString()),
            name = command.name,
            phone = command.phone,
            createdAt = now,
            identityId = command.identityId,
            identityBoundAt = now
        )
        if (personRepository.insertBoundIfAbsent(person)) Outcome(person, true)
        else Outcome(requireNotNull(personRepository.findByIdentityId(command.identityId)), false)
    }
}
