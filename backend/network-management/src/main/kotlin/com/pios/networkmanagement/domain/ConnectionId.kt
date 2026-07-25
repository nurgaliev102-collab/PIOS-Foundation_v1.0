package com.pios.networkmanagement.domain

/**
 * Identity of a Connection (Sprint 7A: PIOS Network Foundation), generated
 * on creation, never caller-supplied — same reasoning as [PersonId].
 */
@JvmInline
value class ConnectionId(val value: String) {
    init {
        require(value.isNotBlank()) { "ConnectionId must not be blank" }
    }
}
