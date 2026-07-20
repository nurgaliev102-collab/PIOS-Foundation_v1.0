-- Dispatch's own local projection of driver availability (ADR-031,
-- ADR-032; Dispatch Consumer Foundation v1.0), derived from consuming
-- Driver Management's DriverAvailabilityChanged event -- never a copy of
-- Driver Management's own Driver aggregate or its owned information
-- (PERSISTENCE_ARCHITECTURE.md Section 5, Forbidden Ownership). Dispatch
-- is not the source of truth for a driver's availability; this table
-- exists only so Dispatch's own future assignment work
-- (out of this task's scope, ADR-002) has a locally available answer to
-- "which drivers are currently available" without querying Driver
-- Management directly.
CREATE TABLE driver_availability (
    driver_reference TEXT PRIMARY KEY,
    available BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Idempotency ledger for consumed DriverAvailabilityChanged events. RabbitMQ
-- is an at-least-once broker (ADR-029, ADR-031): the same eventId may be
-- delivered more than once. Recording every eventId already processed,
-- and only ever applying its effect to `driver_availability` the first
-- time (in the same transaction as this insert), is what makes redelivery
-- of the same event produce the business effect exactly once.
CREATE TABLE driver_availability_processed_events (
    event_id TEXT PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
