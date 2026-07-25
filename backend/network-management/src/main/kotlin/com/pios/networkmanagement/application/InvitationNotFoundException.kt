package com.pios.networkmanagement.application

/**
 * Thrown when an operation references an invitation [code] that identifies
 * no saved [com.pios.networkmanagement.domain.Invitation] — mapped to HTTP
 * 404.
 */
class InvitationNotFoundException(val code: String) : RuntimeException("Invitation not found: $code")
