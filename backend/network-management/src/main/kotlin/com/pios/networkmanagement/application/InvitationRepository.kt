package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.Invitation

/**
 * The persistence boundary for the Invitation aggregate (Sprint 7A: PIOS
 * Network Foundation). Looked up only by [code] — the value a person
 * actually shares — never by [com.pios.networkmanagement.domain.InvitationId].
 */
interface InvitationRepository {
    fun save(invitation: Invitation)
    fun findByCode(code: String): Invitation?
}
