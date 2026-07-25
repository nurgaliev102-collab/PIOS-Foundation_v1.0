package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.PersonId

/**
 * [acceptingPersonId] identifies who is accepting the invitation. The
 * founder's own specification (`POST /v1/invitations/{code}/accept`) did
 * not name a request body for this call; a person accepting an invitation
 * must be identified somehow to know who the resulting Connection's
 * `toPersonId` is, so this command requires an already-created
 * [PersonId] — the same judgment call this project's own precedent makes
 * for API-shape gaps a specification leaves open (for example,
 * `DriverController`'s own HTTP status choices, API_SPECIFICATION.md
 * Section 10). Disclosed here, not silently assumed.
 */
data class AcceptInvitationCommand(val code: String, val acceptingPersonId: PersonId)
