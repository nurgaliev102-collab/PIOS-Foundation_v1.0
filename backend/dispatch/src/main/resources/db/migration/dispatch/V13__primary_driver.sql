-- Dispatch's own local projection of a passenger's currently designated
-- primary driver (ADR-062, Primary Driver / First Refusal -- Passenger
-- Experience -> Dispatch Contract; Task 14, First Refusal Foundation),
-- derived from consuming Passenger Experience's PrimaryConnectionDesignated/
-- PrimaryConnectionCleared events -- never a copy of Passenger
-- Experience's own Connection/primary_connections tables or any
-- information Dispatch does not already have an ADR-062-authorized right
-- to hold. Dispatch is not the source of truth for a passenger's primary
-- driver; this table exists only so Dispatch's own First Refusal decision
-- logic has a locally available answer to "does this passenger currently
-- have a primary driver, and who" without querying Passenger Experience
-- directly (a synchronous cross-module call ADR-062 explicitly rejected).
CREATE TABLE primary_driver_records (
    passenger_reference TEXT PRIMARY KEY,
    driver_reference TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Idempotency ledger for consumed PrimaryConnectionDesignated/
-- PrimaryConnectionCleared events. RabbitMQ is an at-least-once broker: the
-- same eventId may be delivered more than once. Recording every eventId
-- already processed, and only ever applying its effect to
-- primary_driver_records the first time (in the same transaction as this
-- insert), is what makes redelivery of the same event produce the business
-- effect exactly once. One ledger shared by both event types -- event ids
-- are globally unique regardless of type.
CREATE TABLE primary_driver_processed_events (
    event_id TEXT PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
