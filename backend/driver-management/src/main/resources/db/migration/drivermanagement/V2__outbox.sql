-- Driver Management's own transactional outbox table (ADR-032), applying
-- the same pattern already proven in Order Management (Outbox Foundation
-- v1.0). Written in the same PostgreSQL transaction as a Driver's own
-- availability state change, so the two either both commit or both roll
-- back -- closing the dual-write gap ADR-031 identified. Consumed only by
-- this domain's own relay (ADR-031, ADR-032); never shared with or read
-- by another module.
--
-- `payload` is stored as plain text (a minimal JSON string), not a native
-- JSON/JSONB type or any schema-registry-backed format, since the concrete
-- event schema representation remains an open decision (ADR-030). `id` is
-- a simple auto-incrementing key, used only to preserve commit order when
-- the relay polls for unpublished records -- not a business identifier.
CREATE TABLE driver_management_outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    routing_key TEXT NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
