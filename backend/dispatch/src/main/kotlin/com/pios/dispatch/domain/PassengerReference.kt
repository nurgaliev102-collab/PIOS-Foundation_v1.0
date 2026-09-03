package com.pios.dispatch.domain

/**
 * Dispatch's own reference to the passenger a First Refusal decision
 * concerns (ADR-062, Primary Driver / First Refusal — Passenger
 * Experience → Dispatch Contract; Task 14, First Refusal Foundation).
 * Per the module isolation rule (MODULE_STRUCTURE.md Section 2; ADR-005,
 * ADR-009), Dispatch does not own or import Passenger Experience's own
 * `PassengerReference` type; this is a distinct, module-local value
 * carrying only the plain identity string, mirroring
 * [OrderReference]/[DriverReference]'s own exact convention. Dispatch
 * does not verify that this passenger exists, and carries no passenger
 * profile, contact, or Personal Client Relationship information beyond
 * this bare identifier (ADR-062 Constraints: "No ranking, scoring, or
 * weighting is introduced").
 */
@JvmInline
value class PassengerReference(val passengerId: String) {
    init {
        require(passengerId.isNotBlank()) { "PassengerReference must not be blank" }
    }
}
