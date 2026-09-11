package com.pios.core.domain

/**
 * The key Core's Slice 01 read-model is organised by (ADR-067 Participant
 * Reference).
 *
 * Its [value] is a reference already flowing through the working product:
 * for passenger-sourced facts it is the `identityId` (the session token
 * `sub` claim, `Order.origin`, `Proposal.passengerReference`,
 * `passenger-experience.Connection.passenger_reference`); for
 * driver-sourced facts it is the driver's own id (`payload.driverId`).
 *
 * Deliberately named `ParticipantReference`, never `PersonId`. This type
 * carries **no** claim that PIOS has a single canonical Person ID, that
 * `identityId` is that id, or that the driver-id and identity-id spaces
 * reconcile — those are a Core Data Contract decision ADR-067 explicitly
 * defers (Explicit Non-Decisions). It is a plain opaque string wrapper:
 * Core neither mints references nor interprets their structure.
 *
 * No event infrastructure, broker, or schema is implied by this
 * representation (ADR-003).
 */
@JvmInline
value class ParticipantReference(val value: String) {
    init {
        require(value.isNotBlank()) { "ParticipantReference value must not be blank" }
    }
}
