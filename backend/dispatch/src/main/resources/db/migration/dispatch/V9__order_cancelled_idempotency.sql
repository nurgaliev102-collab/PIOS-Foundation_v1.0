-- Idempotency ledger for Dispatch's own consumption of Order Management's
-- OrderCancelled (ADR-053, Proposal Resolution on Order Cancellation --
-- Accepted). RabbitMQ is an at-least-once broker (ADR-029, ADR-031): the
-- same eventId may be delivered more than once. Recording every eventId
-- already processed, and only ever applying its effect (withdrawing the
-- order's own OPEN Proposal, if any) the first time, is what makes
-- redelivery of the same event produce the business effect exactly once
-- -- mirrors `driver_availability_processed_events`
-- (V2__driver_availability.sql) exactly, for a different consumed event.
--
-- No change to the `proposals` table itself: `status` is already stored
-- as plain TEXT with no CHECK constraint (V4__proposals.sql's own
-- comment), so `WITHDRAWN` is a data value, not a schema change.
CREATE TABLE order_cancelled_processed_events (
    event_id TEXT PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
