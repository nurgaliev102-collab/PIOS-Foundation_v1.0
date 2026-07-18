-- Driver Management's own PostgreSQL schema (ADR-025; PERSISTENCE_ARCHITECTURE.md
-- Section 3). Persists exactly the Driver logical entity this module owns —
-- id and availability — and nothing belonging to any other domain (no
-- shared database, no cross-domain tables). Availability is stored as text
-- rather than a native enum type to keep evolution of the Availability
-- value object (DOMAIN_MODEL.md Section 6) a plain data change, not a
-- schema migration.
CREATE TABLE drivers (
    id TEXT PRIMARY KEY,
    availability TEXT NOT NULL
);
