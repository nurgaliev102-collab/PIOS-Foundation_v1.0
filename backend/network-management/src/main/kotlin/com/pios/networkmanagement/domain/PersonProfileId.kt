package com.pios.networkmanagement.domain

/**
 * Identity of a PersonProfile (Sprint 7A: PIOS Network Foundation),
 * generated on creation, never caller-supplied — same reasoning as
 * [PersonId].
 */
@JvmInline
value class PersonProfileId(val value: String) {
    init {
        require(value.isNotBlank()) { "PersonProfileId must not be blank" }
    }
}
