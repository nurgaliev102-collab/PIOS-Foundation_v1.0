package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.Connection
import com.pios.networkmanagement.domain.PersonId
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for Retrieve Connections for Person
 * (Sprint 7A: PIOS Network Foundation) — this person's own, inviter-side
 * connections only (see [ConnectionRepository.findByFromPerson]'s own
 * KDoc).
 */
@Service
class RetrievePersonConnectionsHandler(private val connectionRepository: ConnectionRepository) {
    fun handle(personId: PersonId): List<Connection> = connectionRepository.findByFromPerson(personId)
}
