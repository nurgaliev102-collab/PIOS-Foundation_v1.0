package com.pios.passengerexperience.domain

/**
 * Identity of a Connection (Sprint 7B: Personal Network Flow MVP),
 * generated on creation, never caller-supplied -- no external system
 * assigns a connection an identity before PIOS does.
 */
@JvmInline
value class ConnectionId(val value: String) {
    init {
        require(value.isNotBlank()) { "ConnectionId must not be blank" }
    }
}
