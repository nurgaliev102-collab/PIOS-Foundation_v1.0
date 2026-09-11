package com.pios.core.domain

/**
 * Core's own **closed** vocabulary of participant-history fact kinds
 * (ADR-067 Data Model Scope item 2: "a `kind` from a closed Core-local
 * vocabulary").
 *
 * Every kind here is a durable, participant-meaningful historical fact
 * derived from one of the five Current Authorized Inputs (ADR-067). This
 * enum is the single source of truth for the `kind` column in
 * `participant_history_events`; the column is plain TEXT so adding a kind
 * is a code change here, not a schema migration.
 *
 * Deliberately NOT here: anything derived from a Reserved Future Input,
 * anything derived from `DriverAvailabilityChanged` (excluded by ADR-067
 * as a transient operational state, not a participant fact), and any
 * mutable-relationship / trust / capability concept.
 */
enum class HistoryEventKind {
    /** From `OrderSubmitted` — this participant requested a ride. */
    ORDER_SUBMITTED,

    /**
     * From `OrderCompleted`, correlated by `orderId` to the participant a
     * prior `ORDER_SUBMITTED` recorded — a ride this participant requested
     * completed. Not recorded when the correlation is unknown.
     */
    ORDER_COMPLETED,

    /**
     * From `OrderCancelled`, correlated by `orderId` to the participant a
     * prior `ORDER_SUBMITTED` recorded — a ride this participant requested
     * was cancelled. Not recorded when the correlation is unknown.
     */
    ORDER_CANCELLED,

    /**
     * From `AssignmentCompleted` — this participant, as the driver
     * (`payload.driverId`), completed a ride. The passenger side of the
     * same ride is recorded separately from `OrderCompleted`.
     */
    RIDE_COMPLETED_AS_DRIVER,

    /**
     * From `PrimaryConnectionDesignated` — this participant, as the
     * passenger (`payload.passengerReference`), designated a primary
     * driver.
     */
    PRIMARY_DRIVER_DESIGNATED,

    /**
     * From `PrimaryConnectionDesignated` — this participant, as the
     * driver (`payload.driverId`), was designated a passenger's primary
     * driver.
     */
    DESIGNATED_AS_PRIMARY_DRIVER
}
