-- Dispatch's own local projection of a passenger's trusted circle
-- (ADR-068, Relationship-Ordered Fallback Dispatch -- Trusted -> Network
-- -> Open Marketplace, Part 1), derived from consuming Passenger
-- Experience's ConnectionEstablished/ConnectionRemoved events -- never a
-- copy of Passenger Experience's own `connections` table or any
-- information Dispatch does not already have an ADR-068-authorized right
-- to hold. Dispatch is not the source of truth for a passenger's trusted
-- circle; this table exists only so Fallback Dispatch's Tier 1 (Trusted)
-- selection has a locally available candidate set without querying
-- Passenger Experience directly (a synchronous cross-module call ADR-068
-- explicitly rejects, for the same reasons ADR-062 already rejected it
-- for the primary-driver case).
--
-- Unlike primary_driver_records (one row per passenger), a passenger's
-- trusted circle is a set, so the primary key is the pair itself.
CREATE TABLE trusted_driver_records (
    passenger_reference TEXT NOT NULL,
    driver_reference TEXT NOT NULL,
    established_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (passenger_reference, driver_reference)
);

-- Fallback Dispatch Tier 1's own candidate query (ADR-068 Part 2, "T1
-- TRUSTED") joins this table against driver_availability by
-- driver_reference; an index on driver_reference keeps that join cheap as
-- both tables grow.
CREATE INDEX idx_trusted_driver_records_driver_reference ON trusted_driver_records (driver_reference);

-- Idempotency ledger for consumed ConnectionEstablished/ConnectionRemoved
-- events -- a dedicated ledger, separate from primary_driver_processed_events,
-- per ADR-068 Part 1's own instruction ("its own per-consumer DLQ and its
-- own markProcessed(eventId) idempotency ledger"). RabbitMQ is an
-- at-least-once broker: the same eventId may be delivered more than once.
-- Recording every eventId already processed, and only ever applying its
-- effect to trusted_driver_records the first time (in the same
-- transaction as this insert), is what makes redelivery of the same event
-- produce the business effect exactly once.
CREATE TABLE trusted_driver_processed_events (
    event_id TEXT PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
