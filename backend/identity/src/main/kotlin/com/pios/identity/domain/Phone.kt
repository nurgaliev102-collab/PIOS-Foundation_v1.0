package com.pios.identity.domain

/**
 * A phone number as an [Identity]'s anchor (ADR-038). Deliberately minimal:
 * format shape only (leading `+`, digits only after it, plausible length) —
 * no carrier lookup, no verification, no proof this number is reachable or
 * belongs to whoever supplied it. That is exactly what distinguishes this
 * from a future [Credential]: a [Phone] is a claim; a [Credential] would be
 * evidence. Conflating the two here would invent a guarantee this module
 * does not yet back with any real verification — the same restraint
 * `network-management`'s own `Person.phone` already shows (optional,
 * unvalidated beyond non-blank).
 */
@JvmInline
value class Phone(val value: String) {
    init {
        require(PHONE_PATTERN.matches(value)) { "Phone must be in the form +<7-15 digits>, e.g. +79991234567" }
    }

    companion object {
        private val PHONE_PATTERN = Regex("^\\+[1-9][0-9]{6,14}$")
    }
}
