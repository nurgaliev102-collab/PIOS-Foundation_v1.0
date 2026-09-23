package com.pios.networkmanagement.api

/**
 * The founder's own specification did not name a request body for
 * `POST /v1/invitations/{code}/accept` — see
 * [com.pios.networkmanagement.application.AcceptInvitationCommand]'s own
 * KDoc for why [personId], identifying who is accepting, is required here.
 */
data class AcceptInvitationRequest(val personId: String? = null)
