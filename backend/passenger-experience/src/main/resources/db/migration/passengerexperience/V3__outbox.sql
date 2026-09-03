-- Passenger Experience's own transactional outbox table (Task 14, First
-- Refusal Foundation; ADR-062). Written in the same PostgreSQL transaction
-- as a primary-connection designation/removal, so the two either both
-- commit or both roll back -- the same dual-write-avoidance discipline
-- already established for Dispatch's, Order Management's, and Driver
-- Management's own outbox tables. Consumed only by this module's own
-- relay; never shared with or read by another module.
--
-- Structurally identical to dispatch_outbox/order_management_outbox/
-- driver_management_outbox: `payload` is stored as plain text (a minimal
-- JSON string), not a native JSON/JSONB type or any schema-registry-backed
-- format, since the concrete event schema representation remains an open
-- decision (ADR-030). `id` is a simple auto-incrementing key, used only to
-- preserve commit order when the relay polls for unpublished records --
-- not a business identifier.
CREATE TABLE passenger_experience_outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    routing_key TEXT NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
