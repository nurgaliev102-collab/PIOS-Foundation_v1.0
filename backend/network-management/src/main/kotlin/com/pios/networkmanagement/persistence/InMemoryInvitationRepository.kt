package com.pios.networkmanagement.persistence

import com.pios.networkmanagement.application.InvitationRepository
import com.pios.networkmanagement.domain.Invitation
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [InvitationRepository] — a test double
 * only, mirroring [InMemoryPersonRepository]'s own role.
 */
class InMemoryInvitationRepository : InvitationRepository {
    private val store = ConcurrentHashMap<String, Invitation>()

    override fun save(invitation: Invitation) {
        store[invitation.code] = invitation
    }

    override fun findByCode(code: String): Invitation? = store[code]
}
