package com.pios.networkmanagement.api

/**
 * The shape `POST /v1/invitations` returns, per the founder's own
 * specification example (`{"code":"ABC123","link":"pios/invite/ABC123"}`).
 * [link] is built here, not stored on [com.pios.networkmanagement.domain.Invitation]
 * itself, since it is a presentation detail derived entirely from [code].
 */
data class InvitationResponse(val code: String, val link: String)
