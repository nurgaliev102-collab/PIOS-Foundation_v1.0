-- Idempotency ledger for consumed AssignmentAccepted events (Tranche 1:
-- Dispatch Event Publishing Completion). RabbitMQ is an at-least-once
-- broker (ADR-029, ADR-031): the same eventId may be delivered more than
-- once. Recording every eventId already processed, and only ever
-- applying its effect the first time (in the same transaction as this
-- insert), is what makes redelivery of the same event produce the
-- business effect exactly once. Structurally identical to Dispatch's own
-- driver_availability_processed_events.
CREATE TABLE order_management_processed_events (
    event_id TEXT PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
