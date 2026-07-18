-- Order Management's own PostgreSQL schema (ADR-025; PERSISTENCE_ARCHITECTURE.md
-- Section 3). Persists exactly the Order logical entity this module owns —
-- id and status — and nothing belonging to any other domain (no shared
-- database, no cross-domain tables). Status is stored as text rather than a
-- native enum type to keep evolution of OrderStatus (DOMAIN_MODEL.md Section
-- 6) a plain data change, not a schema migration.
CREATE TABLE orders (
    id TEXT PRIMARY KEY,
    status TEXT NOT NULL
);
