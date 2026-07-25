package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.Person
import com.pios.networkmanagement.domain.PersonId
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Application-layer coordination for Create Person (Sprint 7A: PIOS
 * Network Foundation). Unlike Driver Management's `CreateDriverApplicationService`,
 * no existence check is needed before saving: [PersonId] is generated
 * here, fresh, on every call (see [PersonId]'s own KDoc for why it is
 * never caller-supplied), so no id collision is possible in practice.
 */
@Service
class CreatePersonApplicationService(
    private val personRepository: PersonRepository,
    private val transactionRunner: TransactionRunner = NoOpTransactionRunner
) {
    fun handle(command: CreatePersonCommand): Person = transactionRunner.run {
        val person = Person(
            id = PersonId(UUID.randomUUID().toString()),
            name = command.name,
            phone = command.phone,
            createdAt = Instant.now()
        )
        personRepository.save(person)
        person
    }
}
